package com.fileserver.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class FileClient {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) {
        System.out.println("[Client] Connecting to " + HOST + ":" + PORT);

        try (Socket socket = new Socket(HOST, PORT)) {
            System.out.println("[Client] Connected!");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            PrintWriter writer = new PrintWriter(
                    socket.getOutputStream(), true);

            writer.println("Hello from client!");

            String response = reader.readLine();
            System.out.println("[Client] Server replied: " + response);

        } catch (Exception e) {
            System.out.println("[Client] Error: " + e.getMessage());
        }
    }
}