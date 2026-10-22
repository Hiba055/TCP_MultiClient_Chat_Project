package ma.project.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Networking/model layer. It has no JavaFX dependencies and can be tested independently
 * from the presentation layer.
 */
public final class ServerModel {
    private final Consumer<String> logger;
    private final Consumer<List<String>> usersListener;
    private final Set<ClientSession> clients = ConcurrentHashMap.newKeySet();
    private final AtomicInteger guestCounter = new AtomicInteger(1);
    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private String host;
    private int port;

    public ServerModel(Consumer<String> logger, Consumer<List<String>> usersListener) {
        this.logger = logger;
        this.usersListener = usersListener;
        loadConfig();
    }

    private void loadConfig() {
        Properties props = new Properties();
        try (var in = ServerModel.class.getResourceAsStream("/server.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ex) {
            log("Could not read server.properties; using defaults.");
        }
        host = props.getProperty("server.host", "0.0.0.0").trim();
        try {
            port = Integer.parseInt(props.getProperty("server.port", "3000").trim());
        } catch (NumberFormatException ex) {
            port = 3000;
        }
    }

    public synchronized void start() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(port, 50, java.net.InetAddress.getByName(host));
        running = true;
        log("Server Started on " + host + ":" + port);
        log("Waiting for Client...");
        virtualThreads.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setKeepAlive(true);
                ClientSession session = new ClientSession(socket);
                clients.add(session);
                updateUsers();
                log("Client connection accepted from " + socket.getRemoteSocketAddress());
                virtualThreads.submit(session);
            } catch (IOException ex) {
                if (running) log("Accept error: " + ex.getMessage());
            }
        }
    }

    private void handleLine(ClientSession client, String line) {
        String[] parts = line.split("\\t", -1);
        try {
            switch (parts[0]) {
                case "JOIN" -> {
                    String requested = parts.length > 1 ? Protocol.dec(parts[1]).trim() : "";
                    if (requested.isBlank()) {
                        client.username = "Guest-" + guestCounter.getAndIncrement();
                        client.readOnly = true;
                        client.send("SYSTEM\t" + Protocol.enc("Connected in READ-ONLY MODE. Enter a username to send messages; reconnect to change it."));
                    } else {
                        String clean = requested.replaceAll("[\\r\\n\\t]", "").trim();
                        if (clean.length() > 24) clean = clean.substring(0, 24);
                        if (clean.isBlank()) {
                            client.username = "Guest-" + guestCounter.getAndIncrement();
                            client.readOnly = true;
                        } else {
                            client.username = uniqueName(clean, client);
                            client.readOnly = false;
                            client.send("SYSTEM\t" + Protocol.enc("Welcome " + client.username + "!"));
                            log("Welcome " + client.username);
                            broadcastSystem(client.username + " joined the chat.");
                        }
                    }
                    updateUsers();
                }
                case "CHAT" -> {
                    if (client.username == null) {
                        client.send("SYSTEM\t" + Protocol.enc("Please connect first."));
                    } else if (client.readOnly) {
                        client.send("SYSTEM\t" + Protocol.enc("READ-ONLY MODE: enter a username before sending messages."));
                    } else if (parts.length > 1) {
                        String message = Protocol.dec(parts[1]).trim();
                        if (!message.isEmpty()) broadcastMessage(client.username, message);
                    }
                }
                case "USERS" -> {
                    List<String> names = clients.stream().filter(c -> c.username != null)
                            .map(c -> c.username + (c.readOnly ? " (read-only)" : ""))
                            .sorted(String.CASE_INSENSITIVE_ORDER).toList();
                    client.send("USERS\t" + Protocol.enc(String.join(", ", names)));
                }
                case "LEAVE" -> client.close();
                default -> client.send("SYSTEM\t" + Protocol.enc("Unknown command."));
            }
        } catch (IllegalArgumentException ex) {
            client.send("SYSTEM\t" + Protocol.enc("Invalid message format."));
        }
    }

    private String uniqueName(String requested, ClientSession self) {
        boolean exists = clients.stream().anyMatch(c -> c != self && c.username != null
                && !c.readOnly && c.username.equalsIgnoreCase(requested));
        if (!exists) return requested;
        int suffix = 2;
        while (true) {
            String candidate = requested + suffix++;
            final String test = candidate;
            if (clients.stream().noneMatch(c -> c != self && c.username != null
                    && !c.readOnly && c.username.equalsIgnoreCase(test))) return candidate;
        }
    }

    private void broadcastMessage(String username, String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String packet = "MSG\t" + Protocol.enc(username) + "\t" + Protocol.enc(time) + "\t" + Protocol.enc(message);
        clients.forEach(c -> c.send(packet));
        log("[" + time + "] " + username + ": " + message);
    }

    private void broadcastSystem(String message) {
        String packet = "SYSTEM\t" + Protocol.enc(message);
        clients.forEach(c -> c.send(packet));
    }

    private void updateUsers() {
        List<String> names = clients.stream().filter(c -> c.username != null)
                .map(c -> c.username + (c.readOnly ? " (read-only)" : ""))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        usersListener.accept(names);
    }

    private void log(String message) {
        logger.accept("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + message);
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        for (ClientSession client : new ArrayList<>(clients)) client.close();
        virtualThreads.shutdownNow();
        log("Server stopped.");
        usersListener.accept(List.of());
    }

    private final class ClientSession implements Runnable {
        private final Socket socket;
        private BufferedWriter out;
        private volatile String username;
        private volatile boolean readOnly;
        private final Object writeLock = new Object();

        private ClientSession(Socket socket) { this.socket = socket; }

        @Override public void run() {
            try (socket;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                out = writer;
                String line;
                while (running && (line = reader.readLine()) != null) handleLine(this, line);
            } catch (IOException ex) {
                if (running) log("Connection ended: " + ex.getMessage());
            } finally {
                String name = username;
                clients.remove(this);
                updateUsers();
                if (name != null && !readOnly) broadcastSystem(name + " left the chat.");
                if (name != null) log(name + " disconnected.");
            }
        }

        private void send(String message) {
            synchronized (writeLock) {
                try {
                    if (out != null) {
                        out.write(message);
                        out.newLine();
                        out.flush();
                    }
                } catch (IOException ex) {
                    close();
                }
            }
        }

        private void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
