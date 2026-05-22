/**
 * FileServer — Terminal Version (Legacy)
 * This is the original terminal-based server without GUI.
 * Use ServerApp.java for the JavaFX GUI version.
 */
package com.fileserver.server;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileServer {

    private static final int PORT = 5000;
    private static final int MAX_CLIENTS = 10;

    public static void main(String[] args) {
        UserStore userStore = new UserStore();
        AuthService authService = new AuthService(userStore);
        FileService fileService = new FileService();
        ExecutorService threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            String lanIP = "unavailable";
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();

                if (ni.isLoopback() || !ni.isUp()) continue;

                String displayName = ni.getDisplayName().toLowerCase();
                if (displayName.contains("wsl") || displayName.contains("vethernet")
                        || displayName.contains("vmware") || displayName.contains("virtualbox")
                        || displayName.contains("hyper-v")) continue;

                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        lanIP = addr.getHostAddress();
                        break;
                    }
                }
            }
            System.out.println("[Server] FileServer started");
            System.out.println("[Server] LAN IP     : " + lanIP);
            System.out.println("[Server] Port       : " + PORT);
            System.out.println("[Server] Max clients: " + MAX_CLIENTS);
            System.out.println("[Server] Waiting for connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket, authService, userStore, fileService));
            }

        } catch (Exception e) {
            System.out.println("[Server] Fatal error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }
}