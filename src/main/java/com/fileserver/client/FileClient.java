package com.fileserver.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("QUIT")) {
                    out.writeUTF("QUIT");
                    System.out.println("[Client] Server: " + in.readUTF());
                    break;

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
                    System.out.println("[Client] File size: " + fileSize + " bytes. Downloading...");

                    out.writeUTF("READY");

                    File outputFile = new File(filename);
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        long remaining = fileSize;
                        byte[] buffer = new byte[BUFFER_SIZE];

                        while (remaining > 0) {
                            int toRead = (int) Math.min(BUFFER_SIZE, remaining);
                            in.readFully(buffer, 0, toRead);
                            fos.write(buffer, 0, toRead);
                            remaining -= toRead;
                        }
                        fos.flush();
                    }

                    System.out.println("[Client] Downloaded: " + outputFile.getAbsolutePath());
                    System.out.println("[Client] Size: " + outputFile.length() + " bytes");
                } else if (input.startsWith("DELETE|")) {
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