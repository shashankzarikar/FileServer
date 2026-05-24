/**
 * FileClient — Terminal Version (Legacy)
 * This is the original terminal-based client without GUI.
 * Use ClientApp.java for the JavaFX GUI version.
 * StressTest.java uses the same socket logic for concurrent testing.
 */
package com.fileserver.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.util.Scanner;

public class FileClient {

    private static final int PORT = 5000;
    private static final int BUFFER_SIZE = 8192;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter server IP (press Enter for localhost): ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) host = "localhost";

        System.out.print("Enter username: ");
        String username = scanner.nextLine().trim();

        System.out.print("Enter password: ");
        String password = scanner.nextLine().trim();

        System.out.println("[Client] Connecting to " + host + ":" + PORT);

        try (Socket socket = new Socket(host, PORT)) {
            System.out.println("[Client] Connected!");

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // AUTH
            out.writeUTF("AUTH|" + username + "|" + password);
            String response = in.readUTF();
            System.out.println("[Client] Server: " + response);

            if (!response.startsWith("OK")) {
                System.out.println("[Client] Authentication failed. Disconnecting.");
                return;
            }

            System.out.println("[Client] Authenticated!");
            System.out.println("Commands: LIST | UPLOAD|filepath | DOWNLOAD|filename | DELETE|filename | QUIT");

            // Start heartbeat thread to keep connection alive
            Thread heartbeat = new Thread(() -> {
                while (!socket.isClosed()) {
                    try {
                        Thread.sleep(60000);
                        if (socket.isClosed()) break;
                        out.writeUTF("PING");
                        String pong = in.readUTF();
                        if (!pong.equals("PONG")) {
                            System.out.println("[Client] Connection lost — server not responding.");
                            break;
                        }
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        System.out.println("[Client] Connection lost: " + e.getMessage());
                        break;
                    }
                }
            });
            heartbeat.setDaemon(true);
            heartbeat.start();

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("QUIT")) {
                    out.writeUTF("QUIT");
                    System.out.println("[Client] Server: " + in.readUTF());
                    break;

                } else if (input.startsWith("REGISTER|")) {
                    out.writeUTF(input);
                    System.out.println("[Client] Server: " + in.readUTF());

                } else if (input.equals("LIST")) {
                    out.writeUTF("LIST");
                    System.out.println("[Client] Server: " + in.readUTF());

                } else if (input.startsWith("UPLOAD|")) {
                    String filePath = input.substring(7);
                    File file = new File(filePath);

                    if (!file.exists()) {
                        System.out.println("[Client] File not found: " + filePath);
                        continue;
                    }

                    long fileSize = file.length();
                    String filename = file.getName();

                    out.writeUTF("UPLOAD|" + filename + "|" + fileSize);

                    String ready = in.readUTF();
                    if (!ready.equals("READY")) {
                        System.out.println("[Client] Server not ready: " + ready);
                        continue;
                    }

                    System.out.println("[Client] Uploading " + filename + " (" + fileSize + " bytes)...");
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            socket.getOutputStream().write(buffer, 0, bytesRead);
                        }
                        socket.getOutputStream().flush();
                    }

                    System.out.println("[Client] Server: " + in.readUTF());

                } else if (input.startsWith("DOWNLOAD|")) {
                    String filename = input.substring(9);

                    out.writeUTF("DOWNLOAD|" + filename);

                    String sizeResponse = in.readUTF();
                    if (!sizeResponse.startsWith("SIZE|")) {
                        System.out.println("[Client] Server: " + sizeResponse);
                        continue;
                    }

                    long fileSize = Long.parseLong(sizeResponse.split("\\|")[1]);
                    String saveAs = filename.contains("/") ? filename.split("/", 2)[1] : filename;
                    System.out.println("[Client] File size: " + fileSize + " bytes. Downloading...");

                    out.writeUTF("READY");

                    // Ask user where to save
                    System.out.print("[Client] Enter download path (press Enter for Downloads folder): ");
                    String customPath = scanner.nextLine().trim();

                    File outputFile;
                    if (customPath.isEmpty()) {
                        String downloadPath = System.getProperty("user.home") + File.separator + "Downloads";
                        File downloadDir = new File(downloadPath);
                        if (!downloadDir.exists()) downloadDir = new File(System.getProperty("user.dir"));
                        downloadDir.mkdirs();
                        outputFile = new File(downloadDir, saveAs);
                    } else {
                        File customDir = new File(customPath);
                        customDir.mkdirs();
                        outputFile = new File(customDir, saveAs);
                    }

                    System.out.println("[Client] Saving to: " + outputFile.getAbsolutePath());

                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        long remaining = fileSize;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(8192, remaining);
                            in.readFully(buffer, 0, toRead);
                            fos.write(buffer, 0, toRead);
                            remaining -= toRead;
                        }
                        fos.flush();
                    }

                    System.out.println("[Client] Downloaded: " + outputFile.getAbsolutePath());
                } else if (input.startsWith("DELETE|")) {
                    out.writeUTF(input);
                    System.out.println("[Client] Server: " + in.readUTF());
                } else if (input.equals("LISTUSERS")) {
                    out.writeUTF("LISTUSERS");
                    System.out.println("[Client] Server: " + in.readUTF());
                } else if (input.startsWith("SHARE|")) {
                    out.writeUTF(input);
                    System.out.println("[Client] Server: " + in.readUTF());

                } else if (input.startsWith("ACCESS|")) {
                    String token = input.substring(7);
                    out.writeUTF("ACCESS|" + token);

                    String sizeResponse = in.readUTF();
                    if (!sizeResponse.startsWith("SIZE|")) {
                        System.out.println("[Client] Server: " + sizeResponse);
                        continue;
                    }

                    String[] sizeParts = sizeResponse.split("\\|");
                    long fileSize = Long.parseLong(sizeParts[1]);
                    String sharedFilename = sizeParts.length > 2 ? sizeParts[2] : "shared_" + token;
                    System.out.println("[Client] Shared file: " + sharedFilename
                            + " (" + fileSize + " bytes). Downloading...");

                    out.writeUTF("READY");

                    // Ask user where to save
                    System.out.print("[Client] Enter download path (press Enter for Downloads folder): ");
                    String customPath = scanner.nextLine().trim();

                    File outputFile;
                    if (customPath.isEmpty()) {
                        String downloadPath = System.getProperty("user.home") + File.separator + "Downloads";
                        File downloadDir = new File(downloadPath);
                        if (!downloadDir.exists()) downloadDir = new File(System.getProperty("user.dir"));
                        downloadDir.mkdirs();
                        outputFile = new File(downloadDir, sharedFilename);
                    } else {
                        File customDir = new File(customPath);
                        customDir.mkdirs();
                        outputFile = new File(customDir, sharedFilename);
                    }

                    System.out.println("[Client] Saving to: " + outputFile.getAbsolutePath());

                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        long remaining = fileSize;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(8192, remaining);
                            in.readFully(buffer, 0, toRead);
                            fos.write(buffer, 0, toRead);
                            remaining -= toRead;
                        }
                        fos.flush();
                    }

                    System.out.println("[Client] Saved: " + outputFile.getAbsolutePath());
                } else if (input.startsWith("TOKENINFO|")) {
                    out.writeUTF(input);
                    String tokenResponse = in.readUTF();
                    if (tokenResponse.startsWith("INFO|")) {
                        String[] parts = tokenResponse.split("\\|");
                        System.out.println("[Client] Token info:");
                        System.out.println("         Owner    : " + parts[1]);
                        System.out.println("         File     : " + parts[2]);
                        System.out.println("         Expires  : " + parts[3]);
                        System.out.println("         Use ACCESS|token to download");
                    } else {
                        System.out.println("[Client] Server: " + tokenResponse);
                    }
                } else if (input.startsWith("REVOKE|")) {
                    out.writeUTF(input);
                    System.out.println("[Client] Server: " + in.readUTF());
                } else {
                    System.out.println("[Client] Unknown command.");
                }
            }

        } catch (Exception e) {
            System.out.println("[Client] Error: " + e.getMessage());
        }
    }
}