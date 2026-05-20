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
            String lanIP = InetAddress.getLocalHost().getHostAddress();
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