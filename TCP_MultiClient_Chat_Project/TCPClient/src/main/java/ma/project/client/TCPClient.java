package ma.project.client;

import javafx.application.Application;

/** Application entry point. Optional command-line arguments: server IP and port. */
public class TCPClient {
    public static void main(String[] args) {
        Application.launch(ClientView.class, args);
    }
}
