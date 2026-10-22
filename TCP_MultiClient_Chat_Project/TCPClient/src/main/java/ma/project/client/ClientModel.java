package ma.project.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** TCP communication model, independent of JavaFX controls. */
public final class ClientModel {
    private final Consumer<String> messageListener;
    private final Consumer<Boolean> connectionListener;
    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    private volatile Socket socket;
    private volatile BufferedReader in;
    private volatile BufferedWriter out;
    private volatile boolean connected;
    private final Object writeLock = new Object();

    public ClientModel(Consumer<String> messageListener, Consumer<Boolean> connectionListener) {
        this.messageListener = messageListener;
        this.connectionListener = connectionListener;
    }

    public void connect(String host, int port, String username) throws IOException {
        if (connected) disconnect();
        Socket newSocket = new Socket(host, port);
        newSocket.setKeepAlive(true);
        socket = newSocket;
        in = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8));
        connected = true;
        connectionListener.accept(true);
        send("JOIN\t" + Protocol.enc(username == null ? "" : username.trim()));
        virtualThreads.submit(this::readLoop);
    }

    private void readLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                String[] parts = line.split("\\t", -1);
                switch (parts[0]) {
                    case "MSG" -> {
                        if (parts.length >= 4) {
                            String user = Protocol.dec(parts[1]);
                            String time = Protocol.dec(parts[2]);
                            String message = Protocol.dec(parts[3]);
                            messageListener.accept("[" + time + "] " + user + ": " + message);
                        }
                    }
                    case "SYSTEM" -> {
                        if (parts.length >= 2) messageListener.accept("• " + Protocol.dec(parts[1]));
                    }
                    case "USERS" -> {
                        if (parts.length >= 2) messageListener.accept("• Active users: " + Protocol.dec(parts[1]));
                    }
                    default -> messageListener.accept("• Received an unknown server response.");
                }
            }
        } catch (IOException ex) {
            if (connected) messageListener.accept("• Connection lost: " + ex.getMessage());
        } finally {
            connected = false;
            connectionListener.accept(false);
        }
    }

    public void sendChat(String message) throws IOException {
        send("CHAT\t" + Protocol.enc(message));
    }

    public void requestUsers() throws IOException {
        send("USERS");
    }

    private void send(String line) throws IOException {
        synchronized (writeLock) {
            if (!connected || out == null) throw new IOException("Not connected to the server.");
            out.write(line);
            out.newLine();
            out.flush();
        }
    }

    public void disconnect() {
        if (connected) {
            try { send("LEAVE"); } catch (IOException ignored) {}
        }
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        connectionListener.accept(false);
    }

    public boolean isConnected() { return !connected; }

    public void shutdown() {
        disconnect();
        virtualThreads.shutdownNow();
    }
}
