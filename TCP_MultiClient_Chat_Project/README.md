# Mini Project 1 — Multi-Client Chat Application Using Java Sockets

This submission contains two independent Maven projects:
- `TCPServer`: JavaFX server dashboard and TCP server.
- `TCPClient`: JavaFX chat client.

## Requirements
- JDK 21 or later (virtual threads are used)
- Maven 3.9+
- Internet access on the first Maven build to download JavaFX/Maven dependencies

## Build
Open a terminal in each project folder and run:

```bash
mvn clean package
```

## Run from IntelliJ
1. Open `TCPServer` as a Maven project.
2. Run `ma.project.server.TCPServer`.
3. Open `TCPClient` as a Maven project.
4. Run `ma.project.client.TCPClient`.
5. For each client run configuration, set Program arguments to `127.0.0.1 3000`.
6. Launch multiple client instances to test group chat.

The server reads `server.properties` from the classpath. The default server binds to `0.0.0.0` on port `3000`.
Client command-line format required by the assignment:

```bash
java --module-path <javafx-sdk-lib> --add-modules javafx.controls -cp target/classes ma.project.client.TCPClient <ServerIPAddress> <PortNumber>
```

The JavaFX Maven plugin can also launch the app from the project folder:

```bash
mvn javafx:run
```

For client command-line arguments through Maven, configure the run arguments in the IntelliJ Run Configuration or pass them using the JavaFX Maven plugin's `javafx.args` setting supported by your plugin version.

## Protocol summary
The client sends line-based commands over TCP: `JOIN`, `CHAT`, `USERS`, and `LEAVE`.
Text fields are Base64 encoded so spaces and punctuation are preserved safely.
The server uses a virtual thread per connection and broadcasts chat messages to all connected clients.

## Deliverables
- Source code for both Maven projects
- UML source files in `Documentation/`
- This README
- Build output JARs are generated in each project's `target/` directory after `mvn package`.

Note: JavaFX applications need the JavaFX runtime/native libraries for the target operating system. The Maven JARs produced by `mvn package` are not universal self-contained executables; use the configured Maven JavaFX plugin or package a platform-specific runtime for final distribution.
