package com.fileserver.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.security.MessageDigest;

public class UserStore {

    private static final String DB_URL = "jdbc:sqlite:fileserver.db";

    public UserStore() {
        initDatabase();
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(DB_URL);
    }

    private void initDatabase() {
        String createTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'USER',
                    created_at TEXT DEFAULT (datetime('now'))
                )
                """;

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(createTable)) {
            stmt.execute();
            createDefaultAdmin();
            System.out.println("[UserStore] Database ready.");
        } catch (Exception e) {
            System.out.println("[UserStore] Error initializing database: " + e.getMessage());
        }
    }

    private void createDefaultAdmin() {
        String check = "SELECT COUNT(*) FROM users WHERE username = 'admin'";
        String insert = "INSERT INTO users (username, password, role) VALUES ('admin', ?, 'ADMIN')";

        try (Connection conn = connect();
             PreparedStatement checkStmt = conn.prepareStatement(check)) {

            ResultSet rs = checkStmt.executeQuery();
            if (rs.getInt(1) == 0) {
                try (PreparedStatement insertStmt = conn.prepareStatement(insert)) {
                    insertStmt.setString(1, sha256("admin123"));
                    insertStmt.execute();
                    System.out.println("[UserStore] Default admin created. Username: admin | Password: admin123");
                }
            }
        } catch (Exception e) {
            System.out.println("[UserStore] Error creating admin: " + e.getMessage());
        }
    }

    public boolean authenticate(String username, String password) {
        String query = "SELECT password FROM users WHERE username = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("password").equals(sha256(password));
            }
        } catch (Exception e) {
            System.out.println("[UserStore] Auth error: " + e.getMessage());
        }
        return false;
    }

    public boolean registerUser(String username, String password) {
        String insert = "INSERT INTO users (username, password, role) VALUES (?, ?, 'USER')";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(insert)) {

            stmt.setString(1, username);
            stmt.setString(2, sha256(password));
            stmt.execute();
            return true;

        } catch (Exception e) {
            // UNIQUE constraint violation means username already exists
            System.out.println("[UserStore] Register error: " + e.getMessage());
            return false;
        }
    }

    public String getRole(String username) {
        String query = "SELECT role FROM users WHERE username = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("role");
            }
        } catch (Exception e) {
            System.out.println("[UserStore] Role error: " + e.getMessage());
        }
        return "USER";
    }

    public boolean userExists(String username) {
        String query = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.getInt(1) > 0;

        } catch (Exception e) {
            System.out.println("[UserStore] Exists check error: " + e.getMessage());
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