package ma.project.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/** JavaFX presentation layer for a chat client. */
public class ClientView extends Application {
    private final TextArea chat = new TextArea();
    private final TextField usernameField = new TextField();
    private final TextField messageField = new TextField();
    private final TextField hostField = new TextField("127.0.0.1");
    private final TextField portField = new TextField("3000");
    private final Label status = new Label("● Offline");
    private final Label mode = new Label("Connect to start chatting.");
    private final Button connectButton = new Button("Connect");
    private final Button sendButton = new Button("SEND");
    private final Button usersButton = new Button("allUsers");
    private final Button leaveButton = new Button("LEAVE");
    private ClientModel model;
    private boolean readOnly = true;

    @Override public void start(Stage stage) {
        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) hostField.setText(args.get(0));
        if (args.size() > 1) portField.setText(args.get(1));

        model = new ClientModel(this::appendMessage, this::connectionChanged);
        chat.setEditable(false);
        chat.setWrapText(true);
        chat.setPromptText("Messages from the group will appear here...");
        messageField.setPromptText("Type a message or allUsers...");
        usernameField.setPromptText("Username (leave empty for read-only mode)");
        hostField.setPrefColumnCount(12);
        portField.setPrefColumnCount(5);
        messageField.setOnAction(e -> sendMessage());
        sendButton.setOnAction(e -> sendMessage());
        connectButton.setOnAction(e -> connect());
        usersButton.setOnAction(e -> requestUsers());
        leaveButton.setOnAction(e -> leave());
        updateControls(false);

        Label title = new Label("GROUP CHAT");
        title.setStyle("-fx-font-size: 23px; -fx-font-weight: bold; -fx-text-fill: #17324d;");
        status.setStyle("-fx-text-fill: #b42318; -fx-font-weight: bold;");
        mode.setStyle("-fx-text-fill: #53657a;");
        HBox network = new HBox(8, new Label("Server IP"), hostField, new Label("Port"), portField);
        network.setAlignment(Pos.CENTER_LEFT);
        HBox identity = new HBox(8, new Label("Username"), usernameField, connectButton, status);
        identity.setAlignment(Pos.CENTER_LEFT);
        HBox controls = new HBox(8, messageField, sendButton, usersButton, leaveButton);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(messageField, javafx.scene.layout.Priority.ALWAYS);
        VBox top = new VBox(12, title, network, identity, mode);
        top.setPadding(new Insets(18));
        VBox bottom = new VBox(8, controls);
        bottom.setPadding(new Insets(12, 18, 18, 18));
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(chat);
        root.setBottom(bottom);
        BorderPane.setMargin(chat, new Insets(0, 18, 0, 18));
        root.setStyle("-fx-background-color: #f4f7fb;");
        Scene scene = new Scene(root, 820, 560);
        stage.setTitle("TCP Chat — Client");
        stage.setMinWidth(650); stage.setMinHeight(450);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> model.shutdown());
        stage.show();
        appendMessage("Welcome! Enter a username and click Connect. Leave username blank to enter read-only mode.");
    }

    private void connect() {
        String host = hostField.getText().trim();
        String rawPort = portField.getText().trim();
        if (host.isBlank()) {
            appendMessage("• Please enter the server IP address.");
            return;
        }
        final int port;
        try {
            port = Integer.parseInt(rawPort);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            appendMessage("• Port must be a number between 1 and 65535.");
            return;
        }
        String username = usernameField.getText().trim();
        readOnly = username.isBlank();
        try {
            model.connect(host, port, username);
            mode.setText(readOnly ? "READ-ONLY MODE — you can view messages and request active users." : "Connected as " + username + ".");
            appendMessage("• Connecting to " + host + ":" + port + "...");
            updateControls(true);
        } catch (IOException ex) {
            appendMessage("• Could not connect: " + ex.getMessage());
            connectionChanged(false);
        }
    }

    private void sendMessage() {
        if (!model.isConnected()) return;
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;
        if (message.equalsIgnoreCase("end") || message.equalsIgnoreCase("bye")) {
            leave();
            return;
        }
        try {
            if (message.equalsIgnoreCase("allUsers")) {
                model.requestUsers();
            } else if (!readOnly) {
                model.sendChat(message);
            } else {
                appendMessage("• READ-ONLY MODE: enter a username before sending messages.");
            }
            messageField.clear();
        } catch (IOException ex) {
            appendMessage("• Could not send: " + ex.getMessage());
        }
    }

    private void requestUsers() {
        if (!model.isConnected()) {
            appendMessage("• Connect to the server first.");
            return;
        }
        try { model.requestUsers(); }
        catch (IOException ex) { appendMessage("• Could not request users: " + ex.getMessage()); }
    }

    private void leave() {
        model.disconnect();
        appendMessage("• You disconnected from the server.");
        updateControls(false);
        status.setText("● Offline");
        status.setStyle("-fx-text-fill: #b42318; -fx-font-weight: bold;");
        mode.setText("Disconnected. Connect again to rejoin.");
    }

    private void connectionChanged(boolean online) {
        Platform.runLater(() -> {
            status.setText(online ? "● Online" : "● Offline");
            status.setStyle(online
                    ? "-fx-text-fill: #16803c; -fx-font-weight: bold;"
                    : "-fx-text-fill: #b42318; -fx-font-weight: bold;");
            if (!online) updateControls(false);
        });
    }

    private void updateControls(boolean online) {
        connectButton.setDisable(online);
        hostField.setDisable(online);
        portField.setDisable(online);
        usernameField.setDisable(online);
        sendButton.setDisable(!online);
        messageField.setDisable(!online);
        usersButton.setDisable(!online);
        leaveButton.setDisable(!online);
    }

    private void appendMessage(String message) {
        Platform.runLater(() -> chat.appendText(message + System.lineSeparator()));
    }
}
