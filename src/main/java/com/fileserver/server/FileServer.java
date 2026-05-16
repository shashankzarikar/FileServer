package com.fileserver.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private static final int PORT = 5000;

    public static void main(String[] args) {
        System.out.println("[Server] Starting on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] Waiting for client...");

            Socket clientSocket = serverSocket.accept();
            System.out.println("[Server] Client connected: "
                    + clientSocket.getInetAddress().getHostAddress());

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));

            PrintWriter writer = new PrintWriter(
                    clientSocket.getOutputStream(), true);

            String message = reader.readLine();
            System.out.println("[Server] Received: " + message);

            writer.println("HELLO|Server received: " + message);

            clientSocket.close();

        } catch (Exception e) {
            System.out.println("[Server] Error: " + e.getMessage());
        }
    }
}