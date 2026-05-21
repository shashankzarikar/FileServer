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
        String createUsers = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'USER',
                created_at TEXT DEFAULT (datetime('now'))
            )
            """;

        String createShares = """
            CREATE TABLE IF NOT EXISTS shares (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                token TEXT UNIQUE NOT NULL,
                owner TEXT NOT NULL,
                filename TEXT NOT NULL,
                created_at TEXT DEFAULT (datetime('now')),
                expires_at TEXT NOT NULL
            )
            """;

        try (Connection conn = connect()) {
            conn.prepareStatement(createUsers).execute();
            conn.prepareStatement(createShares).execute();
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
    public String listUsers() {
        String query = "SELECT username, role, created_at FROM users";
        StringBuilder sb = new StringBuilder("USERS|");
        boolean first = true;

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                if (!first) sb.append(",");
                sb.append(rs.getString("username"))
                        .append("(").append(rs.getString("role")).append(")");
                first = false;
            }
        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
        return sb.toString();
    }
    public String createShareToken(String owner, String filename) {
        // Generate a random 8-character token
        String token = java.util.UUID.randomUUID().toString().substring(0, 8);

        String insert = """
            INSERT INTO shares (token, owner, filename, expires_at)
            VALUES (?, ?, ?, datetime('now', '+24 hours'))
            """;

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(insert)) {

            stmt.setString(1, token);
            stmt.setString(2, owner);
            stmt.setString(3, filename);
            stmt.execute();
            return token;

        } catch (Exception e) {
            System.out.println("[UserStore] Share error: " + e.getMessage());
            return null;
        }
    }

    public String[] resolveShareToken(String token) {
        // Returns [owner, filename] if token is valid and not expired, null otherwise
        String query = """
            SELECT owner, filename FROM shares
            WHERE token = ?
            AND datetime('now') < datetime(expires_at)
            """;

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, token);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new String[]{ rs.getString("owner"), rs.getString("filename") };
            }

        } catch (Exception e) {
            System.out.println("[UserStore] Resolve token error: " + e.getMessage());
        }
        return null;
    }

    public boolean revokeShareToken(String token, String requestingUser) {
        // Only the owner or admin can revoke a token
        String query = "SELECT owner FROM shares WHERE token = ?";
        String delete = "DELETE FROM shares WHERE token = ?";

        try (Connection conn = connect()) {
            PreparedStatement checkStmt = conn.prepareStatement(query);
            checkStmt.setString(1, token);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) return false;

            String owner = rs.getString("owner");
            if (!owner.equals(requestingUser) && !getRole(requestingUser).equals("ADMIN")) {
                return false;
            }

            PreparedStatement deleteStmt = conn.prepareStatement(delete);
            deleteStmt.setString(1, token);
            deleteStmt.execute();
            return true;

        } catch (Exception e) {
            System.out.println("[UserStore] Revoke error: " + e.getMessage());
            return false;
        }
    }
    public String getTokenInfo(String token) {
        String query = """
            SELECT owner, filename, expires_at FROM shares
            WHERE token = ?
            AND datetime('now') < datetime(expires_at)
            """;

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, token);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return "INFO|" + rs.getString("owner") + "|"
                        + rs.getString("filename") + "|"
                        + rs.getString("expires_at");
            }

        } catch (Exception e) {
            System.out.println("[UserStore] TokenInfo error: " + e.getMessage());
        }
        return "ERROR|Invalid or expired token";
    }
}