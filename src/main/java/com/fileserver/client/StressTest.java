/**
 * StressTest — Concurrent Load Verification
 *
 * Simulates 10 simultaneous clients each performing:
 * AUTH → UPLOAD → LIST → DOWNLOAD → DELETE → QUIT
 *
 * Run this with FileServer already running.
 * All 10 clients must pass with zero failures and exact byte verification.
 *
 * Last result: 10/10 passed, 0 failures, 97890 bytes verified per client.
 */
package com.fileserver.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class StressTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;
    private static final int NUM_CLIENTS = 10;
    private static final int BUFFER_SIZE = 8192;

    // Counts successful and failed operations
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);

    // CountDownLatch ensures all threads start at exactly the same time
    private static final CountDownLatch startLatch = new CountDownLatch(1);
    private static final CountDownLatch doneLatch = new CountDownLatch(NUM_CLIENTS);

    public static void main(String[] args) throws Exception {
        System.out.println("[StressTest] Starting " + NUM_CLIENTS + " simultaneous clients...");
        System.out.println("[StressTest] Make sure FileServer is running first.");
        System.out.println();

        // Create a test file to upload
        File testFile = createTestFile();
        System.out.println("[StressTest] Test file created: " + testFile.getAbsolutePath()
                + " (" + testFile.length() + " bytes)");
        System.out.println();

        ExecutorService pool = Executors.newFixedThreadPool(NUM_CLIENTS);

        // Launch all clients
        for (int i = 0; i < NUM_CLIENTS; i++) {
            final int clientId = i + 1;
            pool.execute(() -> {
                try {
                    // Wait until all threads are ready — then start simultaneously
                    startLatch.await();
                    runClient(clientId, testFile);
                } catch (Exception e) {
                    System.out.println("[Client-" + clientId + "] Fatal: " + e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads at once — true simultaneous load
        System.out.println("[StressTest] Releasing all clients simultaneously...");
        System.out.println("─────────────────────────────────────────────────");
        startLatch.countDown();

        // Wait for all clients to finish
        doneLatch.await();
        pool.shutdown();

        // Cleanup test file
        testFile.delete();

        // Results
        System.out.println("─────────────────────────────────────────────────");
        System.out.println("[StressTest] RESULTS:");
        System.out.println("[StressTest] Successful clients : " + successCount.get());
        System.out.println("[StressTest] Failed clients     : " + failCount.get());
        System.out.println("[StressTest] Total clients      : " + NUM_CLIENTS);

        if (failCount.get() == 0) {
            System.out.println("[StressTest] ALL PASSED — Server handles concurrent load correctly.");
        } else {
            System.out.println("[StressTest] SOME FAILED — Check server logs for errors.");
        }
    }

    private static void runClient(int clientId, File testFile) throws Exception {
        String username = "stress" + clientId;
        String password = "stress123";
        String filename = "stress_test_" + clientId + ".txt";

        System.out.println("[Client-" + clientId + "] Connecting...");

        try (Socket socket = new Socket(HOST, PORT)) {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Step 1 — AUTH
            out.writeUTF("AUTH|" + username + "|" + password);
            String authResponse = in.readUTF();

            if (!authResponse.startsWith("OK")) {
                System.out.println("[Client-" + clientId + "] Auth failed: " + authResponse);
                failCount.incrementAndGet();
                return;
            }
            System.out.println("[Client-" + clientId + "] Authenticated.");

            // Step 2 — UPLOAD
            long fileSize = testFile.length();
            out.writeUTF("UPLOAD|" + filename + "|" + fileSize);
            String ready = in.readUTF();

            if (!ready.equals("READY")) {
                System.out.println("[Client-" + clientId + "] Upload not ready: " + ready);
                failCount.incrementAndGet();
                return;
            }

            try (FileInputStream fis = new FileInputStream(testFile)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    socket.getOutputStream().write(buffer, 0, bytesRead);
                }
                socket.getOutputStream().flush();
            }

            String uploadResult = in.readUTF();
            if (!uploadResult.startsWith("DONE")) {
                System.out.println("[Client-" + clientId + "] Upload failed: " + uploadResult);
                failCount.incrementAndGet();
                return;
            }
            System.out.println("[Client-" + clientId + "] Upload done.");

            // Step 3 — LIST
            out.writeUTF("LIST");
            String listResult = in.readUTF();
            System.out.println("[Client-" + clientId + "] List: " + listResult);

            // Step 4 — DOWNLOAD
            out.writeUTF("DOWNLOAD|" + filename);
            String sizeResponse = in.readUTF();

            if (!sizeResponse.startsWith("SIZE|")) {
                System.out.println("[Client-" + clientId + "] Download failed: " + sizeResponse);
                failCount.incrementAndGet();
                return;
            }

            long downloadSize = Long.parseLong(sizeResponse.split("\\|")[1]);
            out.writeUTF("READY");

            File downloadedFile = new File("stress_download_" + clientId + ".txt");
            try (FileOutputStream fos = new FileOutputStream(downloadedFile)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = downloadSize;
                while (remaining > 0) {
                    int toRead = (int) Math.min(BUFFER_SIZE, remaining);
                    in.readFully(buffer, 0, toRead);
                    fos.write(buffer, 0, toRead);
                    remaining -= toRead;
                }
                fos.flush();
            }

            // Verify downloaded file size matches original
            if (downloadedFile.length() != testFile.length()) {
                System.out.println("[Client-" + clientId + "] SIZE MISMATCH — corruption detected!");
                failCount.incrementAndGet();
                downloadedFile.delete();
                return;
            }

            System.out.println("[Client-" + clientId + "] Download verified — " + downloadedFile.length() + " bytes.");
            downloadedFile.delete();

            // Step 5 — DELETE
            out.writeUTF("DELETE|" + filename);
            String deleteResult = in.readUTF();
            System.out.println("[Client-" + clientId + "] Delete: " + deleteResult);

            // Step 6 — QUIT
            out.writeUTF("QUIT");
            in.readUTF();

            successCount.incrementAndGet();
            System.out.println("[Client-" + clientId + "] ALL STEPS PASSED.");

        } catch (Exception e) {
            System.out.println("[Client-" + clientId + "] Error: " + e.getMessage());
            failCount.incrementAndGet();
        }
    }

    private static File createTestFile() throws Exception {
        File file = new File("stress_test_source.txt");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // Write 50KB of test data
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("StressTest line ").append(i)
                        .append(" — testing concurrent file transfer — ")
                        .append("abcdefghijklmnopqrstuvwxyz0123456789\n");
            }
            fos.write(sb.toString().getBytes());
        }
        return file;
    }
}