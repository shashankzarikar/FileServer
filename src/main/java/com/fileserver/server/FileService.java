package com.fileserver.server;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

public class FileService {

    private static final String STORAGE_ROOT = "storage";
    private static final int BUFFER_SIZE = 8192;

    public FileService() {
        new File(STORAGE_ROOT).mkdirs();
    }

    public String getUserDir(String username) {
        String path = STORAGE_ROOT + "/" + username;
        new File(path).mkdirs();
        return path;
    }

    public String listFiles(String username) {
        File userDir = new File(getUserDir(username));
        File[] files = userDir.listFiles();

        if (files == null || files.length == 0) {
            return "FILES|empty";
        }

        StringBuilder sb = new StringBuilder("FILES|");
        for (int i = 0; i < files.length; i++) {
            sb.append(files[i].getName());
            if (i < files.length - 1) sb.append(",");
        }
        return sb.toString();
    }

    public String listAllFiles() {
        File storageRoot = new File(STORAGE_ROOT);
        File[] userDirs = storageRoot.listFiles(File::isDirectory);

        if (userDirs == null || userDirs.length == 0) {
            return "FILES|empty";
        }

        StringBuilder sb = new StringBuilder("FILES|");
        boolean first = true;

        for (File userDir : userDirs) {
            File[] files = userDir.listFiles();
            if (files == null) continue;

            for (File file : files) {
                if (!first) sb.append(",");
                sb.append(userDir.getName()).append("/").append(file.getName());
                first = false;
            }
        }

        return sb.length() > 6 ? sb.toString() : "FILES|empty";
    }

    public String deleteFile(String username, String filename) {
        try {
            String userDirPath = new File(getUserDir(username)).getCanonicalPath();
            File targetFile = new File(getUserDir(username), filename);
            String targetPath = targetFile.getCanonicalPath();

            if (!targetPath.startsWith(userDirPath)) {
                return "ERROR|Access denied";
            }

            if (!targetFile.exists()) {
                return "ERROR|File not found";
            }

            // Acquire exclusive lock before deleting
            // Blocks if anyone is currently downloading this file
            try (FileChannel channel = FileChannel.open(targetFile.toPath(),
                    StandardOpenOption.WRITE);
                 FileLock lock = channel.lock(0, Long.MAX_VALUE, false)) {
                targetFile.delete();
            }

            return "OK|File deleted: " + filename;

        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
    }

    public String receiveFile(String username, String filename,
                              long fileSize, InputStream inputStream) {
        try {
            String userDirPath = new File(getUserDir(username)).getCanonicalPath();
            File targetFile = new File(getUserDir(username), filename);
            String targetPath = targetFile.getCanonicalPath();

            if (!targetPath.startsWith(userDirPath)) {
                return "ERROR|Access denied";
            }

            // Conflict resolution
            if (targetFile.exists()) {
                String timestamp = String.valueOf(System.currentTimeMillis());
                String newName = filename.contains(".")
                        ? filename.substring(0, filename.lastIndexOf("."))
                        + "_" + timestamp
                        + filename.substring(filename.lastIndexOf("."))
                        : filename + "_" + timestamp;
                targetFile = new File(getUserDir(username), newName);
                System.out.println("[FileService] File exists — saving as: " + newName);
            }

            // Exclusive lock — blocks all readers and writers
            // Ensures no one reads a partially written file
            try (FileOutputStream fos = new FileOutputStream(targetFile);
                 FileChannel channel = fos.getChannel();
                 FileLock lock = channel.lock(0, Long.MAX_VALUE, false)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = fileSize;
                int bytesRead;

                while (remaining > 0) {
                    int toRead = (int) Math.min(BUFFER_SIZE, remaining);
                    bytesRead = inputStream.read(buffer, 0, toRead);
                    if (bytesRead == -1) break;
                    fos.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }

                fos.flush();
            }

            return "DONE|" + targetFile.getName();

        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
    }

    public String prepareDownload(String username, String filename) {
        try {
            String userDirPath = new File(getUserDir(username)).getCanonicalPath();
            File targetFile = new File(getUserDir(username), filename);
            String targetPath = targetFile.getCanonicalPath();

            if (!targetPath.startsWith(userDirPath)) {
                return "ERROR|Access denied";
            }

            if (!targetFile.exists()) {
                return "ERROR|File not found";
            }

            return "SIZE|" + targetFile.length();

        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
    }

    public void sendFile(String username, String filename,
                         DataOutputStream out) throws Exception {
        File targetFile = new File(getUserDir(username), filename);

        // Shared lock — multiple users can download simultaneously
        // But blocks if someone is currently uploading or deleting this file
        try (FileChannel channel = FileChannel.open(targetFile.toPath(),
                StandardOpenOption.READ);
             FileLock lock = channel.lock(0, Long.MAX_VALUE, true);
             FileInputStream fis = new FileInputStream(targetFile)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalSent = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
            }
            out.flush();
        }
    }
}