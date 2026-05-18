package com.fileserver.server;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileService {

    private static final String STORAGE_ROOT = "storage";

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

    public String deleteFile(String username, String filename) {
        try {
            // Path traversal protection
            // Resolve the full path and verify it stays inside the user's directory
            String userDirPath = new File(getUserDir(username)).getCanonicalPath();
            File targetFile = new File(getUserDir(username), filename);
            String targetPath = targetFile.getCanonicalPath();

            if (!targetPath.startsWith(userDirPath)) {
                return "ERROR|Access denied";
            }

            if (!targetFile.exists()) {
                return "ERROR|File not found";
            }

            targetFile.delete();
            return "OK|File deleted: " + filename;

        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
    }
}