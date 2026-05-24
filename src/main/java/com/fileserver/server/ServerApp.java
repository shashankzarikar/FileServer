package com.fileserver.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerApp extends Application {

    private static final int PORT = 5000;
    private static final int MAX_CLIENTS = 10;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private boolean running = false;

    private TextArea logArea;
    private Label statusLabel;
    private Label clientCountLabel;
    private Label lanIPLabel;
    private Button startStopButton;

    private final AtomicInteger activeClients = new AtomicInteger(0);

    @Override
    public void start(Stage stage) {
        stage.setTitle("FileServer — Control Panel");

        // ── TOP BAR ──────────────────────────────────────────────────
        Label title = new Label("FileServer");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#1D4ED8"));

        statusLabel = new Label("● Stopped");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        statusLabel.setTextFill(Color.web("#DC2626"));

        HBox topBar = new HBox(20, title, statusLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(16, 20, 8, 20));

        // ── INFO ROW ─────────────────────────────────────────────────
        lanIPLabel = new Label("LAN IP: detecting...");
        lanIPLabel.setFont(Font.font("Arial", 13));
        lanIPLabel.setTextFill(Color.web("#374151"));

        Label portLabel = new Label("Port: " + PORT);
        portLabel.setFont(Font.font("Arial", 13));
        portLabel.setTextFill(Color.web("#374151"));

        clientCountLabel = new Label("Active clients: 0 / " + MAX_CLIENTS);
        clientCountLabel.setFont(Font.font("Arial", 13));
        clientCountLabel.setTextFill(Color.web("#374151"));

        HBox infoRow = new HBox(30, lanIPLabel, portLabel, clientCountLabel);
        infoRow.setPadding(new Insets(0, 20, 8, 20));

        // ── BUTTON ROW ───────────────────────────────────────────────
        startStopButton = new Button("Start Server");
        startStopButton.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        startStopButton.setStyle(
                "-fx-background-color: #1D4ED8; -fx-text-fill: white; " +
                        "-fx-padding: 8 24 8 24; -fx-background-radius: 6;"
        );
        startStopButton.setOnAction(e -> toggleServer());

        Button clearButton = new Button("Clear Log");
        clearButton.setFont(Font.font("Arial", 13));
        clearButton.setStyle(
                "-fx-background-color: #F3F4F6; -fx-text-fill: #374151; " +
                        "-fx-padding: 8 16 8 16; -fx-background-radius: 6;"
        );
        clearButton.setOnAction(e -> logArea.clear());

        HBox buttonRow = new HBox(12, startStopButton, clearButton);
        buttonRow.setPadding(new Insets(0, 20, 12, 20));

        // ── LOG AREA ─────────────────────────────────────────────────
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setFont(Font.font("Courier New", 12));
        logArea.setStyle("-fx-control-inner-background: #1E293B; -fx-text-fill: #E2E8F0;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        Label logLabel = new Label("Server Log");
        logLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        logLabel.setTextFill(Color.web("#6B7280"));
        logLabel.setPadding(new Insets(0, 20, 4, 20));

        // ── LAYOUT ───────────────────────────────────────────────────
        VBox root = new VBox(0, topBar, infoRow, buttonRow, logLabel, logArea);
        root.setStyle("-fx-background-color: #FFFFFF;");
        VBox.setMargin(logArea, new Insets(0, 20, 20, 20));

        Scene scene = new Scene(root, 720, 520);
        stage.setScene(scene);
        stage.setMinWidth(600);
        stage.setMinHeight(400);
        stage.show();

        // Detect LAN IP on startup
        detectLanIP();

        // Shutdown server cleanly when window is closed
        stage.setOnCloseRequest(e -> stopServer());

        log("FileServer ready. Click 'Start Server' to begin.");
    }

    private void detectLanIP() {
        new Thread(() -> {
            try {
                String ip = "unavailable";

                java.util.Enumeration<java.net.NetworkInterface> interfaces =
                        java.net.NetworkInterface.getNetworkInterfaces();

                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface ni = interfaces.nextElement();

                    if (ni.isLoopback() || !ni.isUp()) continue;

                    // Skip WSL and virtual adapters
                    String displayName = ni.getDisplayName().toLowerCase();
                    if (displayName.contains("wsl") || displayName.contains("vethernet")
                            || displayName.contains("vmware") || displayName.contains("virtualbox")
                            || displayName.contains("hyper-v")) continue;

                    java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress addr = addresses.nextElement();

                        if (addr instanceof java.net.Inet4Address
                                && !addr.isLoopbackAddress()) {
                            ip = addr.getHostAddress();
                            break;
                        }
                    }
                }

                final String finalIP = ip;
                Platform.runLater(() -> lanIPLabel.setText("LAN IP: " + finalIP));

            } catch (Exception e) {
                Platform.runLater(() -> lanIPLabel.setText("LAN IP: unavailable"));
            }
        }).start();
    }

    private void toggleServer() {
        if (running) {
            stopServer();
        } else {
            startServer();
        }
    }

    private void startServer() {
        UserStore userStore = new UserStore();
        AuthService authService = new AuthService(userStore);
        FileService fileService = new FileService();
        threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                running = true;

                Platform.runLater(() -> {
                    statusLabel.setText("● Running");
                    statusLabel.setTextFill(Color.web("#059669"));
                    startStopButton.setText("Stop Server");
                    startStopButton.setStyle(
                            "-fx-background-color: #DC2626; -fx-text-fill: white; " +
                                    "-fx-padding: 8 24 8 24; -fx-background-radius: 6;"
                    );
                });

                log("Server started on port " + PORT);

                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        String clientIP = clientSocket.getInetAddress().getHostAddress();

                        int count = activeClients.incrementAndGet();
                        Platform.runLater(() ->
                                clientCountLabel.setText("Active clients: " + count + " / " + MAX_CLIENTS)
                        );

                        log("Client connected: " + clientIP);

                        threadPool.execute(() -> {
                            ClientHandler handler = new ClientHandler(
                                    clientSocket, authService, userStore, fileService
                            );
                            handler.setLogger(this::log);
                            handler.run();

                            int remaining = activeClients.decrementAndGet();
                            Platform.runLater(() ->
                                    clientCountLabel.setText("Active clients: " + remaining + " / " + MAX_CLIENTS)
                            );
                            log("Client disconnected: " + clientIP);
                        });

                    } catch (Exception e) {
                        if (running) log("Accept error: " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                log("Server error: " + e.getMessage());
            }
        }).start();
    }

    private void stopServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (threadPool != null) {
                threadPool.shutdown();
            }
        } catch (Exception e) {
            log("Error stopping server: " + e.getMessage());
        }

        Platform.runLater(() -> {
            statusLabel.setText("● Stopped");
            statusLabel.setTextFill(Color.web("#DC2626"));
            startStopButton.setText("Start Server");
            startStopButton.setStyle(
                    "-fx-background-color: #1D4ED8; -fx-text-fill: white; " +
                            "-fx-padding: 8 24 8 24; -fx-background-radius: 6;"
            );
            clientCountLabel.setText("Active clients: 0 / " + MAX_CLIENTS);
        });

        log("Server stopped.");
    }

    public void log(String message) {
        String timestamp = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String line = "[" + timestamp + "] " + message + "\n";
        Platform.runLater(() -> {
            logArea.appendText(line);
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}