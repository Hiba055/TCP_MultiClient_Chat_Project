package ma.project.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** JavaFX view/controller for the server dashboard. */
public class ServerView extends Application {
    private final ListView<String> users = new ListView<>();
    private final TextArea logs = new TextArea();
    private final Label status = new Label("● Offline");
    private final Button startButton = new Button("Start Server");
    private final Button stopButton = new Button("Stop Server");
    private ServerModel model;

    @Override public void start(Stage stage) {
        model = new ServerModel(this::appendLog, this::showUsers);
        users.setPlaceholder(new Label("No connected clients"));
        users.setCellFactory(list -> new ListCell<>() {
            private final Map<String, String> colors = new HashMap<>();
            private final Random random = new Random();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText("●  " + item);
                    String color = colors.computeIfAbsent(item, k -> String.format("#%02x%02x%02x",
                            170 + random.nextInt(70), 170 + random.nextInt(70), 170 + random.nextInt(70)));
                    setStyle("-fx-background-color: " + color + "; -fx-padding: 8px; -fx-text-fill: #243047;");
                }
            }
        });
        logs.setEditable(false);
        logs.setWrapText(true);
        logs.setPromptText("Server activity will appear here...");
        status.setStyle("-fx-text-fill: #b42318; -fx-font-weight: bold; -fx-font-size: 14px;");
        stopButton.setDisable(true);
        startButton.setOnAction(e -> startServer());
        stopButton.setOnAction(e -> stopServer());

        Label title = new Label("TCP CHAT SERVER");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #17324d;");
        Label usersTitle = new Label("Connected users");
        usersTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label logsTitle = new Label("Activity log");
        logsTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        HBox actions = new HBox(10, startButton, stopButton, status);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox top = new VBox(12, title, actions);
        top.setPadding(new Insets(18));
        GridPane content = new GridPane();
        content.setHgap(14); content.setVgap(8);
        content.add(usersTitle, 0, 0); content.add(logsTitle, 1, 0);
        content.add(users, 0, 1); content.add(logs, 1, 1);
        GridPane.setHgrow(logs, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setVgrow(users, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setVgrow(logs, javafx.scene.layout.Priority.ALWAYS);
        users.setPrefWidth(250);
        content.setPadding(new Insets(0, 18, 18, 18));
        BorderPane root = new BorderPane();
        root.setTop(top); root.setCenter(content);
        root.setStyle("-fx-background-color: #f4f7fb;");
        Scene scene = new Scene(root, 850, 520);
        stage.setTitle("TCP Chat — Server");
        stage.setMinWidth(700); stage.setMinHeight(420);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> model.stop());
        stage.show();
        appendLog("Server dashboard ready. Click Start Server.");
    }

    private void startServer() {
        try {
            model.start();
            status.setText("● Online");
            status.setStyle("-fx-text-fill: #16803c; -fx-font-weight: bold; -fx-font-size: 14px;");
            startButton.setDisable(true); stopButton.setDisable(false);
        } catch (IOException ex) {
            appendLog("Could not start server: " + ex.getMessage());
        }
    }

    private void stopServer() {
        model.stop();
        status.setText("● Offline");
        status.setStyle("-fx-text-fill: #b42318; -fx-font-weight: bold; -fx-font-size: 14px;");
        startButton.setDisable(false); stopButton.setDisable(true);
    }

    private void appendLog(String line) {
        Platform.runLater(() -> logs.appendText(line + System.lineSeparator()));
    }

    private void showUsers(List<String> names) {
        Platform.runLater(() -> users.getItems().setAll(names));
    }
}
