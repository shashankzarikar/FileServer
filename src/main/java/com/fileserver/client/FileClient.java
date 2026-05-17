package com.fileserver.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class FileClient {

    private static final int PORT = 5000;

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

            // Send AUTH as first packet
            writer.println("AUTH|" + username + "|" + password);

            String response = reader.readLine();
            System.out.println("[Client] Server: " + response);

            if (!response.startsWith("OK")) {
                System.out.println("[Client] Authentication failed. Disconnecting.");
                return;
            }

            // After auth, send commands
            System.out.println("[Client] Authenticated! Type commands (or 'quit' to exit):");
            while (true) {
                System.out.print("> ");
                String command = scanner.nextLine().trim();
                if (command.equalsIgnoreCase("quit")) break;
                writer.println(command);
                System.out.println("[Client] Server: " + reader.readLine());
            }

        } catch (Exception e) {
            System.out.println("[Client] Error: " + e.getMessage());
        }
    }
}