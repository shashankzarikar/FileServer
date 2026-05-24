package com.fileserver.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final AuthService authService;
    private final UserStore userStore;
    private final FileService fileService;
    private String loggedInUser = null;
    private String userRole = null;
    private java.util.function.Consumer<String> logger = System.out::println;

    public ClientHandler(Socket clientSocket, AuthService authService,
                         UserStore userStore, FileService fileService) {
        this.clientSocket = clientSocket;
        this.authService = authService;
        this.userStore = userStore;
        this.fileService = fileService;
    }

    public void setLogger(java.util.function.Consumer<String> logger) {
        this.logger = logger;
    }

    @Override
    public void run() {
        String clientIP = clientSocket.getInetAddress().getHostAddress();
        logger.accept("[Server] Client connected: " + clientIP);

        try {
            // 2 minute idle timeout — heartbeat keeps connection alive
            // If client crashes, server cleans up after 2 minutes
            clientSocket.setSoTimeout(2 * 60 * 1000);

            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            String firstPacket = in.readUTF();

            if (!firstPacket.startsWith("AUTH|") && !firstPacket.startsWith("REGISTER|")) {
                out.writeUTF("ERROR|First command must be AUTH or REGISTER");
                clientSocket.close();
                return;
            }

            if (firstPacket.startsWith("REGISTER|")) {
                out.writeUTF("ERROR|Only admin can register users. Login as admin first.");
                clientSocket.close();
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
                userRole = authService.getRole(username);
                out.writeUTF("OK|Welcome " + username + " [" + userRole + "]");
                logger.accept("[Server] Auth success: " + username + " Role: " + userRole);
            } else {
                out.writeUTF("ERROR|Invalid credentials");
                clientSocket.close();
                logger.accept("[Server] Auth failed for: " + username);
                return;
            }

            // Command loop
            while (true) {
                String command = in.readUTF();
                logger.accept("[" + loggedInUser + "] Command: " + command);
                handleCommand(command, in, out);
                if (command.equals("QUIT")) break;
            }

        } catch (SocketTimeoutException e) {
            // Client went silent for 2 minutes — likely crashed or disconnected
            logger.accept("[Server] Client timed out (2min silence): " + clientIP
                    + " User: " + loggedInUser);
        } catch (Exception e) {
            logger.accept("[Server] Error with client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                logger.accept("[Server] Connection closed: " + loggedInUser);
            } catch (Exception ignored) {
            }
        }
    }

    private void handleCommand(String command, DataInputStream in, DataOutputStream out) {
        try {
            if (command.equals("PING")) {
                // Heartbeat — keep connection alive
                out.writeUTF("PONG");

            } else if (command.equals("LIST")) {
                if (userRole.equals("ADMIN")) {
                    out.writeUTF(fileService.listAllFiles());
                } else {
                    out.writeUTF(fileService.listFiles(loggedInUser));
                }

            } else if (command.equals("LISTUSERS")) {
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

                // Disable timeout during file transfer — large files take time
                clientSocket.setSoTimeout(0);
                String result = fileService.receiveFile(loggedInUser, filename,
                        fileSize, clientSocket.getInputStream());
                // Re-enable timeout after transfer
                clientSocket.setSoTimeout(2 * 60 * 1000);

                out.writeUTF(result);
                logger.accept("[" + loggedInUser + "] Upload result: " + result);

            } else if (command.startsWith("DOWNLOAD|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    out.writeUTF("ERROR|Invalid DOWNLOAD format");
                    return;
                }

                String target = parts[1];
                String targetUser;
                String filename;

                if (target.contains("/")) {
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

                logger.accept("[" + loggedInUser + "] Downloading: " + filename);

                // Disable timeout during file transfer
                clientSocket.setSoTimeout(0);
                fileService.sendFile(targetUser, filename, out);
                // Re-enable timeout after transfer
                clientSocket.setSoTimeout(2 * 60 * 1000);

                logger.accept("[" + loggedInUser + "] Download complete: " + filename);

            } else if (command.startsWith("DELETE|")) {
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
                    logger.accept("[Admin] Registered new user: " + parts[1]);
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
                // Check if filename contains / — means admin is trying to share another user's file
                if (filename.contains("/")) {
                    out.writeUTF("ERROR|You can only share your own files. Upload the file to your storage first.");
                    return;
                }

                String checkResponse = fileService.prepareDownload(loggedInUser, filename);
                if (!checkResponse.startsWith("SIZE|")) {
                    out.writeUTF("ERROR|File not found: " + filename);
                    return;
                }
                String token = userStore.createShareToken(loggedInUser, filename);
                if (token != null) {
                    out.writeUTF("OK|Share token: " + token + " (valid 24 hours)");
                    logger.accept("[" + loggedInUser + "] Shared: " + filename + " Token: " + token);
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
                out.writeUTF(sizeResponse + "|" + filename);
                if (!sizeResponse.startsWith("SIZE|")) return;
                String ready = in.readUTF();
                if (!ready.equals("READY")) return;

                logger.accept("[" + loggedInUser + "] Accessing shared file: "
                        + owner + "/" + filename + " via token: " + token);

                // Disable timeout during file transfer
                clientSocket.setSoTimeout(0);
                fileService.sendFile(owner, filename, out);
                // Re-enable timeout after transfer
                clientSocket.setSoTimeout(2 * 60 * 1000);

            } else if (command.startsWith("REVOKE|")) {
                String[] parts = command.split("\\|");
                if (parts.length != 2) {
                    out.writeUTF("ERROR|Invalid REVOKE format. Use REVOKE|token");
                    return;
                }
                String token = parts[1];
                if (userStore.revokeShareToken(token, loggedInUser)) {
                    out.writeUTF("OK|Token revoked: " + token);
                    logger.accept("[" + loggedInUser + "] Revoked token: " + token);
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
            try {
                out.writeUTF("ERROR|" + e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }
}