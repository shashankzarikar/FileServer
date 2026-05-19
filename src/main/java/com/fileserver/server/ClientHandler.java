package com.fileserver.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final AuthService authService;
    private final FileService fileService;
    private String loggedInUser = null;

    public ClientHandler(Socket clientSocket, AuthService authService, FileService fileService) {
        this.clientSocket = clientSocket;
        this.authService = authService;
        this.fileService = fileService;
    }

    @Override
    public void run() {
        String clientIP = clientSocket.getInetAddress().getHostAddress();
        System.out.println("[Server] Client connected: " + clientIP);

        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(
                    clientSocket.getOutputStream(), true);

            // First packet must be AUTH
            String firstPacket = reader.readLine();

            if (firstPacket == null || !firstPacket.startsWith("AUTH|")) {
                writer.println("ERROR|First command must be AUTH");
                clientSocket.close();
                System.out.println("[Server] Rejected " + clientIP + " — no AUTH");
                return;
            }

            String[] authParts = firstPacket.split("\\|");
            if (authParts.length != 3) {
                writer.println("ERROR|Invalid AUTH format. Use AUTH|username|password");
                clientSocket.close();
                return;
            }

            String username = authParts[1];
            String password = authParts[2];

            if (authService.authenticate(username, password)) {
                loggedInUser = username;
                writer.println("OK|Welcome " + username);
                System.out.println("[Server] Auth success: " + username);
            } else {
                writer.println("ERROR|Invalid credentials");
                clientSocket.close();
                System.out.println("[Server] Auth failed for: " + username);
                return;
            }

            // Command loop
            String command;
            while ((command = reader.readLine()) != null) {
                System.out.println("[" + loggedInUser + "] Command: " + command);
                handleCommand(command, reader, writer);
            }

        } catch (Exception e) {
            System.out.println("[Server] Error with client " + clientIP + ": " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("[Server] Connection closed: " + clientIP);
            } catch (Exception e) {
                System.out.println("[Server] Could not close socket: " + e.getMessage());
            }
        }
    }

    private void handleCommand(String command, BufferedReader reader, PrintWriter writer) {
        try {
            if (command.equals("LIST")) {
                writer.println(fileService.listFiles(loggedInUser));

            } else if (command.startsWith("UPLOAD|")) {
                // UPLOAD|filename|filesize
                String[] parts = command.split("\\|");
                if (parts.length != 3) {
                    writer.println("ERROR|Invalid UPLOAD format. Use UPLOAD|filename|filesize");
                    return;
                }

                String filename = parts[1];
                long fileSize = Long.parseLong(parts[2]);

                // Tell client we are ready to receive bytes
                writer.println("READY");

                // Receive raw bytes directly from the socket input stream
                String result = fileService.receiveFile(
                        loggedInUser, filename, fileSize,
                        clientSocket.getInputStream());

                writer.println(result);
                System.out.println("[" + loggedInUser + "] Upload result: " + result);

            } else if (command.startsWith("DELETE|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    writer.println("ERROR|Invalid DELETE format. Use DELETE|filename");
                    return;
                }
                writer.println(fileService.deleteFile(loggedInUser, parts[1]));

            } else if (command.equals("QUIT")) {
                writer.println("OK|Goodbye " + loggedInUser);

            } else {
                writer.println("ERROR|Unknown command: " + command);
            }

        } catch (Exception e) {
            writer.println("ERROR|" + e.getMessage());
        }
    }
}