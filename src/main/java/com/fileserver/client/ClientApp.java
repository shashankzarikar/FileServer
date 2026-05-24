package com.fileserver.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class ClientApp extends Application {

    private static final int PORT = 5000;
    private static final int BUFFER_SIZE = 8192;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String loggedInUser;
    private String userRole;

    // UI components
    private Stage primaryStage;
    private TextArea logArea;
    private ListView<String> fileListView;
    private Label statusLabel;
    private Label userLabel;
    private ProgressBar progressBar;
    private Label progressLabel;

    private volatile boolean connected = false;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("FileServer Client");
        showLoginScreen();
        stage.setOnCloseRequest(e -> disconnect());
    }

    // ── LOGIN SCREEN ─────────────────────────────────────────────────
    private void showLoginScreen() {
        primaryStage.setTitle("FileServer — Login");

        Label title = new Label("FileServer");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        title.setTextFill(Color.web("#1D4ED8"));

        Label subtitle = new Label("Connect to your LAN file server");
        subtitle.setFont(Font.font("Arial", 14));
        subtitle.setTextFill(Color.web("#6B7280"));

        TextField ipField = new TextField("localhost");
        ipField.setPromptText("Server IP address");
        ipField.setFont(Font.font("Arial", 13));
        ipField.setStyle("-fx-padding: 8; -fx-background-radius: 6;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setFont(Font.font("Arial", 13));
        usernameField.setStyle("-fx-padding: 8; -fx-background-radius: 6;");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setFont(Font.font("Arial", 13));
        passwordField.setStyle("-fx-padding: 8; -fx-background-radius: 6;");

        Label errorLabel = new Label("");
        errorLabel.setTextFill(Color.web("#DC2626"));
        errorLabel.setFont(Font.font("Arial", 12));

        Button connectButton = new Button("Connect");
        connectButton.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        connectButton.setStyle(
                "-fx-background-color: #1D4ED8; -fx-text-fill: white; " +
                        "-fx-padding: 10 30 10 30; -fx-background-radius: 6;"
        );
        connectButton.setMaxWidth(Double.MAX_VALUE);

        connectButton.setOnAction(e -> {
            String ip = ipField.getText().trim();
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();

            if (ip.isEmpty() || username.isEmpty() || password.isEmpty()) {
                errorLabel.setText("All fields are required.");
                return;
            }

            connectButton.setDisable(true);
            connectButton.setText("Connecting...");
            errorLabel.setText("");

            new Thread(() -> {
                String result = connect(ip, username, password);
                Platform.runLater(() -> {
                    if (result.startsWith("OK")) {
                        showDashboard();
                    } else {
                        errorLabel.setText(result);
                        connectButton.setDisable(false);
                        connectButton.setText("Connect");
                    }
                });
            }).start();
        });

        // Allow Enter key to trigger connect
        passwordField.setOnAction(e -> connectButton.fire());

        VBox form = new VBox(12,
                new Label("Server IP"), ipField,
                new Label("Username"), usernameField,
                new Label("Password"), passwordField,
                errorLabel,
                connectButton
        );
        form.setMaxWidth(320);

        for (int i = 0; i < form.getChildren().size(); i++) {
            if (form.getChildren().get(i) instanceof Label l
                    && !l.equals(errorLabel)) {
                l.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                l.setTextFill(Color.web("#374151"));
            }
        }

        VBox root = new VBox(20, title, subtitle, form);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(60));
        root.setStyle("-fx-background-color: #F9FAFB;");

        Scene scene = new Scene(root, 480, 520);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // ── DASHBOARD ────────────────────────────────────────────────────
    private void showDashboard() {
        primaryStage.setTitle("FileServer — " + loggedInUser + " [" + userRole + "]");

        // Top bar
        userLabel = new Label(loggedInUser + " [" + userRole + "]");
        userLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        userLabel.setTextFill(Color.web("#1D4ED8"));

        statusLabel = new Label("● Connected");
        statusLabel.setFont(Font.font("Arial", 12));
        statusLabel.setTextFill(Color.web("#059669"));

        Button disconnectButton = new Button("Disconnect");
        disconnectButton.setStyle(
                "-fx-background-color: #DC2626; -fx-text-fill: white; " +
                        "-fx-padding: 6 14 6 14; -fx-background-radius: 6;"
        );
        disconnectButton.setOnAction(e -> {
            disconnect();
            showLoginScreen();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(12, userLabel, statusLabel, spacer, disconnectButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 16, 12, 16));
        topBar.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E5E7EB; -fx-border-width: 0 0 1 0;");

        // File list
        fileListView = new ListView<>();
        fileListView.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 13;");
        VBox.setVgrow(fileListView, Priority.ALWAYS);

        Label filesLabel = new Label("Your Files");
        filesLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        filesLabel.setTextFill(Color.web("#374151"));

        Button refreshButton = new Button("Refresh");
        refreshButton.setStyle(
                "-fx-background-color: #F3F4F6; -fx-text-fill: #374151; " +
                        "-fx-padding: 4 12 4 12; -fx-background-radius: 6;"
        );
        refreshButton.setOnAction(e -> refreshFileList());

        HBox filesHeader = new HBox(10, filesLabel, refreshButton);
        filesHeader.setAlignment(Pos.CENTER_LEFT);

        // Action buttons
        Button uploadButton = new Button("Upload File");
        uploadButton.setStyle(
                "-fx-background-color: #1D4ED8; -fx-text-fill: white; " +
                        "-fx-padding: 8 16 8 16; -fx-background-radius: 6;"
        );
        uploadButton.setMaxWidth(Double.MAX_VALUE);
        uploadButton.setOnAction(e -> uploadFile());

        Button downloadButton = new Button("Download");
        downloadButton.setStyle(
                "-fx-background-color: #059669; -fx-text-fill: white; " +
                        "-fx-padding: 8 16 8 16; -fx-background-radius: 6;"
        );
        downloadButton.setMaxWidth(Double.MAX_VALUE);
        downloadButton.setOnAction(e -> downloadSelected());

        Button deleteButton = new Button("Delete");
        deleteButton.setStyle(
                "-fx-background-color: #DC2626; -fx-text-fill: white; " +
                        "-fx-padding: 8 16 8 16; -fx-background-radius: 6;"
        );
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setOnAction(e -> deleteSelected());

        Button shareButton = new Button("Share");
        shareButton.setStyle(
                "-fx-background-color: #7C3AED; -fx-text-fill: white; " +
                        "-fx-padding: 8 16 8 16; -fx-background-radius: 6;"
        );
        shareButton.setMaxWidth(Double.MAX_VALUE);
        shareButton.setOnAction(e -> shareSelected());

        Button accessButton = new Button("Access Shared");
        accessButton.setStyle(
                "-fx-background-color: #0369A1; -fx-text-fill: white; " +
                        "-fx-padding: 8 16 8 16; -fx-background-radius: 6;"
        );
        accessButton.setMaxWidth(Double.MAX_VALUE);
        accessButton.setOnAction(e -> accessSharedFile());

        Button tokenInfoButton = new Button("Token Info");
        tokenInfoButton.setStyle(
                "-fx-background-color: #92400E; -fx-text-fill: white; " +
                        "-fx-padding: 8 16 8 16; -fx-background-radius: 6;"
        );
        tokenInfoButton.setMaxWidth(Double.MAX_VALUE);
        tokenInfoButton.setOnAction(e -> checkTokenInfo());

        Button registerButton = new Button("Register User");
        registerButton.setStyle(
                "-fx-background-color: #065F46; -fx-text-fill: white; " +
                        "-fx-padding: 8 16 8 16; -fx-background-radius: 6;"
        );
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setOnAction(e -> registerUser());

// Only show register button for admin
        registerButton.setVisible(userRole.equals("ADMIN"));
        registerButton.setManaged(userRole.equals("ADMIN"));

        VBox actionButtons = new VBox(8,
                uploadButton, downloadButton, deleteButton, shareButton,
                accessButton, tokenInfoButton, registerButton
        );
        actionButtons.setPrefWidth(130);

        // Progress bar
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        progressLabel = new Label("");
        progressLabel.setFont(Font.font("Arial", 11));
        progressLabel.setTextFill(Color.web("#6B7280"));

        VBox progressBox = new VBox(4, progressBar, progressLabel);
        progressBox.setPadding(new Insets(4, 0, 0, 0));

        // Log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setFont(Font.font("Courier New", 11));
        logArea.setPrefHeight(160);
        logArea.setStyle("-fx-control-inner-background: #1E293B; -fx-text-fill: #E2E8F0;");

        Label logLabel = new Label("Activity Log");
        logLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        logLabel.setTextFill(Color.web("#6B7280"));

        // Left panel
        VBox leftPanel = new VBox(10, filesHeader, fileListView, progressBox);
        leftPanel.setPadding(new Insets(16));
        HBox.setHgrow(leftPanel, Priority.ALWAYS);

        // Right panel
        VBox rightPanel = new VBox(8, actionButtons);
        rightPanel.setPadding(new Insets(16, 16, 16, 0));

        HBox mainArea = new HBox(0, leftPanel, rightPanel);
        VBox.setVgrow(mainArea, Priority.ALWAYS);

        VBox bottomArea = new VBox(6, logLabel, logArea);
        bottomArea.setPadding(new Insets(0, 16, 16, 16));

        VBox root = new VBox(0, topBar, mainArea, bottomArea);
        root.setStyle("-fx-background-color: #FFFFFF;");

        Scene scene = new Scene(root, 720, 600);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(500);

        // Load files immediately
        refreshFileList();
        log("Connected as " + loggedInUser + " [" + userRole + "]");
        startHeartbeat();
    }

    // ── NETWORK ──────────────────────────────────────────────────────
    private String connect(String ip, String username, String password) {
        try {
            socket = new Socket(ip, PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            out.writeUTF("AUTH|" + username + "|" + password);
            String response = in.readUTF();

            if (response.startsWith("OK")) {
                loggedInUser = username;
                userRole = response.contains("ADMIN") ? "ADMIN" : "USER";
                connected = true;
                return response;
            }
            return response.replace("OK|", "").replace("ERROR|", "");

        } catch (Exception e) {
            return "Cannot connect to server: " + e.getMessage();
        }
    }

    private void disconnect() {
        connected = false;
        try {
            if (out != null) out.writeUTF("QUIT");
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
    }

    // ── FILE OPERATIONS ──────────────────────────────────────────────
    private void refreshFileList() {
        new Thread(() -> {
            try {
                out.writeUTF("LIST");
                String response = in.readUTF();
                Platform.runLater(() -> {
                    fileListView.getItems().clear();
                    if (response.equals("FILES|empty")) {
                        fileListView.getItems().add("(no files)");
                    } else {
                        String[] files = response.replace("FILES|", "").split(",");
                        fileListView.getItems().addAll(files);
                    }
                });
            } catch (Exception e) {
                log("Error refreshing: " + e.getMessage());
            }
        }).start();
    }

    private void uploadFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select file to upload");
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file == null) return;

        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Uploading " + file.getName() + "...");
        log("Uploading: " + file.getName() + " (" + file.length() + " bytes)");

        new Thread(() -> {
            try {
                long fileSize = file.length();
                out.writeUTF("UPLOAD|" + file.getName() + "|" + fileSize);

                String ready = in.readUTF();
                if (!ready.equals("READY")) {
                    log("Server not ready: " + ready);
                    return;
                }

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    long totalSent = 0;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        socket.getOutputStream().write(buffer, 0, bytesRead);
                        totalSent += bytesRead;
                        final double progress = (double) totalSent / fileSize;
                        Platform.runLater(() -> progressBar.setProgress(progress));
                    }
                    socket.getOutputStream().flush();
                }

                String result = in.readUTF();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressLabel.setText("");
                    log("Upload complete: " + result);
                    refreshFileList();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    log("Upload error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void downloadSelected() {
        String selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.equals("(no files)")) {
            log("No file selected.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save file as");
        String filename = selected.contains("/") ? selected.split("/")[1] : selected;
        fileChooser.setInitialFileName(filename);
        File saveLocation = fileChooser.showSaveDialog(primaryStage);
        if (saveLocation == null) return;

        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Downloading " + filename + "...");
        log("Downloading: " + selected);

        new Thread(() -> {
            try {
                out.writeUTF("DOWNLOAD|" + selected);

                String sizeResponse = in.readUTF();
                if (!sizeResponse.startsWith("SIZE|")) {
                    log("Error: " + sizeResponse);
                    return;
                }

                long fileSize = Long.parseLong(sizeResponse.split("\\|")[1]);
                out.writeUTF("READY");

                try (FileOutputStream fos = new FileOutputStream(saveLocation)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long remaining = fileSize;
                    long totalReceived = 0;

                    while (remaining > 0) {
                        int toRead = (int) Math.min(BUFFER_SIZE, remaining);
                        in.readFully(buffer, 0, toRead);
                        fos.write(buffer, 0, toRead);
                        remaining -= toRead;
                        totalReceived += toRead;
                        final double progress = (double) totalReceived / fileSize;
                        Platform.runLater(() -> progressBar.setProgress(progress));
                    }
                    fos.flush();
                }

                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressLabel.setText("");
                    log("Downloaded: " + saveLocation.getAbsolutePath());
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    log("Download error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void deleteSelected() {
        String selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.equals("(no files)")) {
            log("No file selected.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete File");
        confirm.setHeaderText("Delete " + selected + "?");
        confirm.setContentText("This cannot be undone.");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        out.writeUTF("DELETE|" + selected);
                        String response = in.readUTF();
                        Platform.runLater(() -> {
                            log("Delete: " + response);
                            refreshFileList();
                        });
                    } catch (Exception e) {
                        log("Delete error: " + e.getMessage());
                    }
                }).start();
            }
        });
    }

    private void shareSelected() {
        String selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.equals("(no files)")) {
            log("No file selected.");
            return;
        }

        new Thread(() -> {
            try {
                out.writeUTF("SHARE|" + selected);
                String response = in.readUTF();
                Platform.runLater(() -> {
                    log("Share: " + response);
                    if (response.startsWith("OK")) {
                        // Extract token and show in dialog
                        String token = response.replace("OK|Share token: ", "")
                                .replace(" (valid 24 hours)", "");
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("File Shared");
                        alert.setHeaderText("Share token for " + selected);
                        alert.setContentText(
                                "Token: " + token + "\n\n" +
                                        "Share this token with other users.\n" +
                                        "They can use ACCESS|" + token + " to download the file.\n" +
                                        "Token expires in 24 hours."
                        );
                        alert.showAndWait();
                    }
                });
            } catch (Exception e) {
                log("Share error: " + e.getMessage());
            }
        }).start();
    }

    private void log(String message) {
        String timestamp = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> {
            logArea.appendText("[" + timestamp + "] " + message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void accessSharedFile() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Access Shared File");
        dialog.setHeaderText("Enter share token");
        dialog.setContentText("Token:");
        dialog.showAndWait().ifPresent(token -> {
            if (token.trim().isEmpty()) return;

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save shared file as");
            File saveLocation = fileChooser.showSaveDialog(primaryStage);
            if (saveLocation == null) return;

            progressBar.setVisible(true);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            log("Accessing shared file with token: " + token);

            new Thread(() -> {
                try {
                    out.writeUTF("ACCESS|" + token.trim());

                    String sizeResponse = in.readUTF();
                    if (!sizeResponse.startsWith("SIZE|")) {
                        Platform.runLater(() -> {
                            progressBar.setVisible(false);
                            log("Error: " + sizeResponse);
                        });
                        return;
                    }

                    String[] sizeParts = sizeResponse.split("\\|");
                    long fileSize = Long.parseLong(sizeParts[1]);
                    String sharedFilename = sizeParts.length > 2 ? sizeParts[2] : "shared_file";
                    log("Downloading shared file: " + sharedFilename + " (" + fileSize + " bytes)");

                    out.writeUTF("READY");

                    try (FileOutputStream fos = new FileOutputStream(saveLocation)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        long remaining = fileSize;
                        long totalReceived = 0;

                        while (remaining > 0) {
                            int toRead = (int) Math.min(BUFFER_SIZE, remaining);
                            in.readFully(buffer, 0, toRead);
                            fos.write(buffer, 0, toRead);
                            remaining -= toRead;
                            totalReceived += toRead;
                            final double progress = (double) totalReceived / fileSize;
                            Platform.runLater(() -> progressBar.setProgress(progress));
                        }
                        fos.flush();
                    }

                    Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        progressLabel.setText("");
                        log("Shared file saved: " + saveLocation.getAbsolutePath());
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        log("Access error: " + e.getMessage());
                    });
                }
            }).start();
        });
    }

    private void checkTokenInfo() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Token Info");
        dialog.setHeaderText("Enter share token to check");
        dialog.setContentText("Token:");
        dialog.showAndWait().ifPresent(token -> {
            if (token.trim().isEmpty()) return;

            new Thread(() -> {
                try {
                    out.writeUTF("TOKENINFO|" + token.trim());
                    String response = in.readUTF();

                    Platform.runLater(() -> {
                        if (response.startsWith("INFO|")) {
                            String[] parts = response.split("\\|");
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Token Information");
                            alert.setHeaderText("Token: " + token);
                            alert.setContentText(
                                    "Owner    : " + parts[1] + "\n" +
                                            "File     : " + parts[2] + "\n" +
                                            "Expires  : " + parts[3]
                            );
                            alert.showAndWait();
                        } else {
                            log("Token info: " + response);
                        }
                    });

                } catch (Exception e) {
                    log("Token info error: " + e.getMessage());
                }
            }).start();
        });
    }
    private void startHeartbeat() {
        Thread heartbeat = new Thread(() -> {
            while (connected && socket != null && !socket.isClosed()) {
                try {
                    Thread.sleep(60000); // ping every 60 seconds
                    if (!connected) break;

                    out.writeUTF("PING");
                    String response = in.readUTF();

                    if (!response.equals("PONG")) {
                        Platform.runLater(() -> {
                            statusLabel.setText("● Disconnected");
                            statusLabel.setTextFill(Color.web("#DC2626"));
                            log("Connection lost — server did not respond to heartbeat.");
                        });
                        connected = false;
                        break;
                    }

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    if (connected) {
                        Platform.runLater(() -> {
                            statusLabel.setText("● Disconnected");
                            statusLabel.setTextFill(Color.web("#DC2626"));
                            log("Connection lost: " + e.getMessage());
                        });
                    }
                    connected = false;
                    break;
                }
            }
        });
        heartbeat.setDaemon(true);
        heartbeat.start();
    }
    private void registerUser() {
        // Ask for username
        TextInputDialog usernameDialog = new TextInputDialog();
        usernameDialog.setTitle("Register New User");
        usernameDialog.setHeaderText("Create a new user account");
        usernameDialog.setContentText("Username:");

        usernameDialog.showAndWait().ifPresent(username -> {
            if (username.trim().isEmpty()) return;

            // Ask for password
            TextInputDialog passwordDialog = new TextInputDialog();
            passwordDialog.setTitle("Register New User");
            passwordDialog.setHeaderText("Set password for: " + username);
            passwordDialog.setContentText("Password:");

            passwordDialog.showAndWait().ifPresent(password -> {
                if (password.trim().isEmpty()) return;

                new Thread(() -> {
                    try {
                        out.writeUTF("REGISTER|" + username.trim() + "|" + password.trim());
                        String response = in.readUTF();
                        Platform.runLater(() -> {
                            log("Register: " + response);
                            if (response.startsWith("OK")) {
                                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                                alert.setTitle("Success");
                                alert.setHeaderText("User registered successfully");
                                alert.setContentText(
                                        "Username: " + username.trim() + "\n" +
                                                "Password: " + password.trim() + "\n\n" +
                                                "Share these credentials with the new user."
                                );
                                alert.showAndWait();
                            } else {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Registration Failed");
                                alert.setHeaderText("Could not register user");
                                alert.setContentText(response.replace("ERROR|", ""));
                                alert.showAndWait();
                            }
                        });
                    } catch (Exception e) {
                        log("Register error: " + e.getMessage());
                    }
                }).start();
            });
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}