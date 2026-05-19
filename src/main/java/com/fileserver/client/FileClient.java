package com.fileserver.client;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.io.File;

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

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(
                    socket.getOutputStream(), true);
            OutputStream rawOut = socket.getOutputStream();

            // AUTH
            writer.println("AUTH|" + username + "|" + password);
            String response = reader.readLine();
            System.out.println("[Client] Server: " + response);

            if (!response.startsWith("OK")) {
                System.out.println("[Client] Authentication failed. Disconnecting.");
                return;
            }

            System.out.println("[Client] Authenticated!");
            System.out.println("Commands: LIST | UPLOAD|filepath | DELETE|filename | QUIT");

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("QUIT")) {
                    writer.println("QUIT");
                    System.out.println("[Client] Server: " + reader.readLine());
                    break;

                } else if (input.equals("LIST")) {
                    writer.println("LIST");
                    System.out.println("[Client] Server: " + reader.readLine());

                } else if (input.startsWith("UPLOAD|")) {
                    String filePath = input.substring(7);
                    File file = new File(filePath);

                    if (!file.exists()) {
                        System.out.println("[Client] File not found: " + filePath);
                        continue;
                    }

                    long fileSize = file.length();
                    String filename = file.getName();

                    // Send UPLOAD command with filename and size
                    writer.println("UPLOAD|" + filename + "|" + fileSize);

                    // Wait for READY
                    String ready = reader.readLine();
                    if (!ready.equals("READY")) {
                        System.out.println("[Client] Server not ready: " + ready);
                        continue;
                    }

                    // Stream file bytes in 8KB chunks
                    System.out.println("[Client] Uploading " + filename + " (" + fileSize + " bytes)...");
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        long totalSent = 0;

                        while ((bytesRead = fis.read(buffer)) != -1) {
                            rawOut.write(buffer, 0, bytesRead);
                            totalSent += bytesRead;
                        }
                        rawOut.flush();
                    }

                    String result = reader.readLine();
                    System.out.println("[Client] Server: " + result);

                } else if (input.startsWith("DELETE|")) {
                    writer.println(input);
                    System.out.println("[Client] Server: " + reader.readLine());

                } else {
                    System.out.println("[Client] Unknown command.");
                }
            }

        } catch (Exception e) {
            System.out.println("[Client] Error: " + e.getMessage());
        }
    }
}