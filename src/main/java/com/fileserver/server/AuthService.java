package com.fileserver.server;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

public class AuthService {

    private static final String USERS_FILE = "data/users.json";

    public boolean authenticate(String username, String password) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(USERS_FILE)));
            JSONObject root = new JSONObject(content);
            JSONArray users = root.getJSONArray("users");

            String hashedInput = sha256(password);

            for (int i = 0; i < users.length(); i++) {
                JSONObject user = users.getJSONObject(i);
                if (user.getString("username").equals(username) &&
                        user.getString("password").equals(hashedInput)) {
                    return true;
                }
            }

        } catch (Exception e) {
            System.out.println("[Auth] Error reading users file: " + e.getMessage());
        }
        return false;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}