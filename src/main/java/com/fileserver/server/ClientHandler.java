package com.fileserver.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
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
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            // First packet must be AUTH
            String firstPacket = in.readUTF();

            if (firstPacket == null || !firstPacket.startsWith("AUTH|")) {
                out.writeUTF("ERROR|First command must be AUTH");
                clientSocket.close();
                System.out.println("[Server] Rejected " + clientIP + " — no AUTH");
                return;
            }

            String[] authParts = firstPacket.split("\\|");
            if (authParts.length != 3) {
                out.writeUTF("ERROR|Invalid AUTH format. Use AUTH|username|password");
                clientSocket.close();
                return;
            }

            String username = authParts[1];
            String password = authParts[2];

            if (authService.authenticate(username, password)) {
                loggedInUser = username;
                out.writeUTF("OK|Welcome " + username);
                System.out.println("[Server] Auth success: " + username);
            } else {
                out.writeUTF("ERROR|Invalid credentials");
                clientSocket.close();
                System.out.println("[Server] Auth failed for: " + username);
                return;
            }

            // Command loop
            while (true) {
                String command = in.readUTF();
                System.out.println("[" + loggedInUser + "] Command: " + command);
                handleCommand(command, in, out);
                if (command.equals("QUIT")) break;
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

    private void handleCommand(String command, DataInputStream in, DataOutputStream out) {
        try {
            if (command.equals("LIST")) {
                out.writeUTF(fileService.listFiles(loggedInUser));

            } else if (command.startsWith("UPLOAD|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 3) {
                    out.writeUTF("ERROR|Invalid UPLOAD format. Use UPLOAD|filename|filesize");
                    return;
                }

                String filename = parts[1];
                long fileSize = Long.parseLong(parts[2]);

                out.writeUTF("READY");

                String result = fileService.receiveFile(
                        loggedInUser, filename, fileSize,
                        clientSocket.getInputStream());

                out.writeUTF(result);
                System.out.println("[" + loggedInUser + "] Upload result: " + result);

            }  else if (command.startsWith("DOWNLOAD|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    out.writeUTF("ERROR|Invalid DOWNLOAD format. Use DOWNLOAD|filename");
                    return;
                }

                String filename = parts[1];
                String sizeResponse = fileService.prepareDownload(loggedInUser, filename);
                out.writeUTF(sizeResponse);

                if (!sizeResponse.startsWith("SIZE|")) {
                    return;
                }

                String ready = in.readUTF();
                if (!ready.equals("READY")) {
                    return;
                }

                System.out.println("[" + loggedInUser + "] Downloading: " + filename);
                fileService.sendFile(loggedInUser, filename, out);
                System.out.println("[" + loggedInUser + "] Download complete: " + filename);

            }else if (command.startsWith("DELETE|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    out.writeUTF("ERROR|Invalid DELETE format. Use DELETE|filename");
                    return;
                }
                out.writeUTF(fileService.deleteFile(loggedInUser, parts[1]));

            } else if (command.equals("QUIT")) {
                out.writeUTF("OK|Goodbye " + loggedInUser);

            } else {
                out.writeUTF("ERROR|Unknown command: " + command);
            }

        } catch (Exception e) {
            try {
                out.writeUTF("ERROR|" + e.getMessage());
            } catch (Exception ignored) {}
        }
    }
}