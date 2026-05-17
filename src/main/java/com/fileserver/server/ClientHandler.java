package com.fileserver.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final AuthService authService;
    private String loggedInUser = null;

    public ClientHandler(Socket clientSocket, AuthService authService) {
        this.clientSocket = clientSocket;
        this.authService = authService;
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

            String firstPacket = reader.readLine();

            if (firstPacket == null || !firstPacket.startsWith("AUTH|")) {
                writer.println("ERROR|First command must be AUTH");
                clientSocket.close();
                System.out.println("[Server] Rejected " + clientIP + " — no AUTH");
                return;
            }

            // AUTH|username|password
            String[] parts = firstPacket.split("\\|");
            if (parts.length != 3) {
                writer.println("ERROR|Invalid AUTH format. Use AUTH|username|password");
                clientSocket.close();
                return;
            }

            String username = parts[1];
            String password = parts[2];

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

            // More commands will be handled here in Day 4
            String command;
            while ((command = reader.readLine()) != null) {
                System.out.println("[" + loggedInUser + "] Command: " + command);
                writer.println("OK|Command received: " + command);
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
}