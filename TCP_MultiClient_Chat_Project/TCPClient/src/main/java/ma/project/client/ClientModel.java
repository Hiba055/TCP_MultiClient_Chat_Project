
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * TCP communication model, independent of JavaFX controls.
 */
public final class ClientModel {

    private final Consumer<String> messageListener;
    private final Consumer<Boolean> connectionListener;

    private final ExecutorService virtualThreads =
            Executors.newVirtualThreadPerTaskExecutor();

    private volatile Socket socket;
    private volatile BufferedReader in;
    private volatile BufferedWriter out;
    private volatile boolean connected;

    private final Object writeLock = new Object();
    private final AtomicBoolean connectionNotification =
            new AtomicBoolean(false);

    public ClientModel(
            Consumer<String> messageListener,
            Consumer<Boolean> connectionListener) {

        this.messageListener = messageListener;
        this.connectionListener = connectionListener;
    }

    /**
     * Connects to the server and sends the username.
     * An empty username requests read-only mode.
     */
    public synchronized void connect(
            String host, int port, String username) throws IOException {

        if (connected) {
            disconnect();
        }

        Socket newSocket = new Socket(host, port);
        newSocket.setKeepAlive(true);

        try {
            BufferedReader newIn = new BufferedReader(
                    new InputStreamReader(
                            newSocket.getInputStream(),
                            StandardCharsets.UTF_8));

            BufferedWriter newOut = new BufferedWriter(
                    new OutputStreamWriter(
                            newSocket.getOutputStream(),
                            StandardCharsets.UTF_8));

            socket = newSocket;
            in = newIn;
            out = newOut;
            connected = true;
            connectionNotification.set(true);

            connectionListener.accept(true);

            String safeUsername =
                    username == null ? "" : username.trim();

            send("JOIN\t" + Protocol.enc(safeUsername));

            virtualThreads.submit(this::readLoop);

        } catch (IOException | RuntimeException ex) {
            connected = false;
            connectionNotification.set(false);

            try {
                newSocket.close();
            } catch (IOException ignored) {
                // Ignore cleanup errors.
            }

            socket = null;
            in = null;
            out = null;

            connectionListener.accept(false);

            if (ex instanceof IOException ioException) {
                throw ioException;
            }

            throw ex;
        }
    }

    /**
     * Reads server messages in a background virtual thread.
     */
    private void readLoop() {
        try {
            String line;

            while (connected && (line = in.readLine()) != null) {
                String[] parts = line.split("\t", -1);

                switch (parts[0]) {
                    case "MSG" -> {
                        if (parts.length >= 4) {
                            String user = Protocol.dec(parts[1]);
                            String time = Protocol.dec(parts[2]);
                            String message = Protocol.dec(parts[3]);

                            messageListener.accept(
                                    "[" + time + "] " + user + ": " + message);
                        }
                    }

                    case "SYSTEM" -> {
                        if (parts.length >= 2) {
                            messageListener.accept(
                                    "• " + Protocol.dec(parts[1]));
                        }
                    }

                    case "USERS" -> {
                        if (parts.length >= 2) {
                            messageListener.accept(
                                    "• Active users: "
                                            + Protocol.dec(parts[1]));
                        }
                    }

                    default -> messageListener.accept(
                            "• Received an unknown server response.");
                }
            }

        } catch (IOException ex) {
            if (connected) {
                messageListener.accept(
                        "• Connection lost: " + ex.getMessage());
            }

        } finally {
            connected = false;
            closeSocket();

            if (connectionNotification.compareAndSet(true, false)) {
                connectionListener.accept(false);
            }
        }
    }

    /**
     * Sends a chat message to the server.
     */
    public void sendChat(String message) throws IOException {
        send("CHAT\t" + Protocol.enc(message));
    }

    /**
     * Requests the list of active users.
     */
    public void requestUsers() throws IOException {
        send("USERS");
    }

    /**
     * Writes one protocol line to the server.
     */
    private void send(String line) throws IOException {
        synchronized (writeLock) {
            if (!connected || out == null) {
                throw new IOException("Not connected to the server.");
            }

            out.write(line);
            out.newLine();
            out.flush();
        }
    }

    /**
     * Disconnects from the server.
     */
    public void disconnect() {
        synchronized (writeLock) {
            if (connected && out != null) {
                try {
                    out.write("LEAVE");
                    out.newLine();
                    out.flush();
                } catch (IOException ignored) {
                    // The connection may already be closed.
                }
            }

            connected = false;
            closeSocket();
        }

        if (connectionNotification.compareAndSet(true, false)) {
            connectionListener.accept(false);
        }
    }

    private void closeSocket() {
        Socket currentSocket = socket;

        socket = null;
        in = null;
        out = null;

        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException ignored) {
                // Ignore cleanup errors.
            }
        }
    }

    /**
     * Returns true when connected to the server.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Cleans up resources when the client closes.
     */
    public void shutdown() {
        disconnect();
        virtualThreads.shutdownNow();
    }
}
