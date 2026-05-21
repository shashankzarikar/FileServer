package com.fileserver.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final AuthService authService;
    private final UserStore userStore;
    private final FileService fileService;
    private String loggedInUser = null;
    private String userRole = null;

    public ClientHandler(Socket clientSocket, AuthService authService,
                         UserStore userStore, FileService fileService) {
        this.clientSocket = clientSocket;
        this.authService = authService;
        this.userStore = userStore;
        this.fileService = fileService;
    }

    @Override
    public void run() {
        String clientIP = clientSocket.getInetAddress().getHostAddress();
        System.out.println("[Server] Client connected: " + clientIP);

        try {
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            String firstPacket = in.readUTF();

            if (!firstPacket.startsWith("AUTH|") && !firstPacket.startsWith("REGISTER|")) {
                out.writeUTF("ERROR|First command must be AUTH or REGISTER");
                clientSocket.close();
                return;
            }

            // Handle REGISTER before login
            if (firstPacket.startsWith("REGISTER|")) {
                out.writeUTF("ERROR|Only admin can register users. Login as admin first.");
                clientSocket.close();
                return;
            }

            // Handle AUTH
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
                userRole = authService.getRole(username);
                out.writeUTF("OK|Welcome " + username + " [" + userRole + "]");
                System.out.println("[Server] Auth success: " + username + " Role: " + userRole);
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
            System.out.println("[Server] Error with client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("[Server] Connection closed: " + loggedInUser);
            } catch (Exception ignored) {}
        }
    }

    private void handleCommand(String command, DataInputStream in, DataOutputStream out) {
        try {
            if (command.equals("LIST")) {
                // Admin sees all files, regular user sees only their own
                if (userRole.equals("ADMIN")) {
                    out.writeUTF(fileService.listAllFiles());
                } else {
                    out.writeUTF(fileService.listFiles(loggedInUser));
                }

            } else if (command.equals("LISTUSERS")) {
                // Admin only command
                if (!userRole.equals("ADMIN")) {
                    out.writeUTF("ERROR|Only admin can list users");
                    return;
                }
                out.writeUTF(userStore.listUsers());

            } else if (command.startsWith("UPLOAD|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 3) {
                    out.writeUTF("ERROR|Invalid UPLOAD format. Use UPLOAD|filename|filesize");
                    return;
                }
                String filename = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                out.writeUTF("READY");
                String result = fileService.receiveFile(loggedInUser, filename,
                        fileSize, clientSocket.getInputStream());
                out.writeUTF(result);

            } else if (command.startsWith("DOWNLOAD|")) {
                // Format: DOWNLOAD|filename  or  DOWNLOAD|username/filename (admin only)
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    out.writeUTF("ERROR|Invalid DOWNLOAD format");
                    return;
                }

                String target = parts[1];
                String targetUser;
                String filename;

                if (target.contains("/")) {
                    // Admin downloading another user's file
                    if (!userRole.equals("ADMIN")) {
                        out.writeUTF("ERROR|Only admin can access other users files");
                        return;
                    }
                    String[] split = target.split("/", 2);
                    targetUser = split[0];
                    filename = split[1];
                } else {
                    targetUser = loggedInUser;
                    filename = target;
                }

                String sizeResponse = fileService.prepareDownload(targetUser, filename);
                out.writeUTF(sizeResponse);
                if (!sizeResponse.startsWith("SIZE|")) return;
                String ready = in.readUTF();
                if (!ready.equals("READY")) return;
                fileService.sendFile(targetUser, filename, out);

            } else if (command.startsWith("DELETE|")) {
                // Format: DELETE|filename  or  DELETE|username/filename (admin only)
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    out.writeUTF("ERROR|Invalid DELETE format");
                    return;
                }

                String target = parts[1];

                if (target.contains("/")) {
                    if (!userRole.equals("ADMIN")) {
                        out.writeUTF("ERROR|Only admin can delete other users files");
                        return;
                    }
                    String[] split = target.split("/", 2);
                    out.writeUTF(fileService.deleteFile(split[0], split[1]));
                } else {
                    out.writeUTF(fileService.deleteFile(loggedInUser, target));
                }

            } else if (command.startsWith("REGISTER|")) {
                if (!userRole.equals("ADMIN")) {
                    out.writeUTF("ERROR|Only admin can register new users");
                    return;
                }
                String[] parts = command.split("\\|");
                if (parts.length != 3) {
                    out.writeUTF("ERROR|Invalid REGISTER format. Use REGISTER|username|password");
                    return;
                }
                if (userStore.registerUser(parts[1], parts[2])) {
                    out.writeUTF("OK|User registered: " + parts[1]);
                    System.out.println("[Admin] Registered new user: " + parts[1]);
                } else {
                    out.writeUTF("ERROR|Username already exists: " + parts[1]);
                }
            } else if (command.startsWith("SHARE|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    out.writeUTF("ERROR|Invalid SHARE format. Use SHARE|filename");
                    return;
                }

                String filename = parts[1];

                // Verify file exists in user's folder
                String checkResponse = fileService.prepareDownload(loggedInUser, filename);
                if (!checkResponse.startsWith("SIZE|")) {
                    out.writeUTF("ERROR|File not found: " + filename);
                    return;
                }

                String token = userStore.createShareToken(loggedInUser, filename);
                if (token != null) {
                    out.writeUTF("OK|Share token: " + token + " (valid 24 hours)");
                    System.out.println("[" + loggedInUser + "] Shared: " + filename + " Token: " + token);
                } else {
                    out.writeUTF("ERROR|Could not create share token");
                }

            } else if (command.startsWith("ACCESS|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    out.writeUTF("ERROR|Invalid ACCESS format. Use ACCESS|token");
                    return;
                }

                String token = parts[1];
                String[] shareInfo = userStore.resolveShareToken(token);

                if (shareInfo == null) {
                    out.writeUTF("ERROR|Invalid or expired token");
                    return;
                }

                String owner = shareInfo[0];
                String filename = shareInfo[1];

                String sizeResponse = fileService.prepareDownload(owner, filename);
                // Append filename so client knows what to save as
                String sizeWithName = sizeResponse + "|" + filename;
                out.writeUTF(sizeWithName);

                if (!sizeResponse.startsWith("SIZE|")) return;

                String ready = in.readUTF();
                if (!ready.equals("READY")) return;

                System.out.println("[" + loggedInUser + "] Accessing shared file: "
                        + owner + "/" + filename + " via token: " + token);
                fileService.sendFile(owner, filename, out);

            } else if (command.startsWith("REVOKE|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    out.writeUTF("ERROR|Invalid REVOKE format. Use REVOKE|token");
                    return;
                }

                String token = parts[1];
                if (userStore.revokeShareToken(token, loggedInUser)) {
                    out.writeUTF("OK|Token revoked: " + token);
                    System.out.println("[" + loggedInUser + "] Revoked token: " + token);
                } else {
                    out.writeUTF("ERROR|Token not found or access denied");
                }
            } else if (command.startsWith("TOKENINFO|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    out.writeUTF("ERROR|Invalid format. Use TOKENINFO|token");
                    return;
                }
                out.writeUTF(userStore.getTokenInfo(parts[1]));
            } else if (command.equals("QUIT")) {
                out.writeUTF("OK|Goodbye " + loggedInUser);

            } else {
                out.writeUTF("ERROR|Unknown command: " + command);
            }

        } catch (Exception e) {
            try { out.writeUTF("ERROR|" + e.getMessage()); } catch (Exception ignored) {}
        }
    }
}