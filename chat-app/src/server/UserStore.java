package server;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

/** Lưu tài khoản người dùng trong bảng `users` của MySQL (mật khẩu: SHA-256 + salt). */
public class UserStore {

    private static final Path LEGACY_FILE = Paths.get("data", "users.db");
    private static final SecureRandom RANDOM = new SecureRandom();

    public UserStore() {
        importLegacyFile();
    }

    public boolean exists(String username) {
        if (username == null) return false;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[UserStore] exists failed: " + e.getMessage());
            return false;
        }
    }

    public Set<String> getAllUsernames() {
        Set<String> result = new LinkedHashSet<>();
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT username FROM users ORDER BY username")) {
            while (rs.next()) result.add(rs.getString(1));
        } catch (SQLException e) {
            System.err.println("[UserStore] getAllUsernames failed: " + e.getMessage());
        }
        return result;
    }

    /** @return true nếu tạo thành công, false nếu tên đã tồn tại (hoặc lỗi DB). */
    public synchronized boolean register(String username, String password) {
        if (username == null) return false;
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        String saltHex = toHex(saltBytes);
        String hashHex = hash(saltHex, password);
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users (username, salt, password_hash) VALUES (?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, saltHex);
            ps.setString(3, hashHex);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() != 1062) { // 1062 = trùng username (UNIQUE)
                System.err.println("[UserStore] register failed: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean authenticate(String username, String password) {
        if (username == null) return false;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT salt, password_hash FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String expected = rs.getString("password_hash");
                String actual = hash(rs.getString("salt"), password);
                return constantTimeEquals(expected, actual);
            }
        } catch (SQLException e) {
            System.err.println("[UserStore] authenticate failed: " + e.getMessage());
            return false;
        }
    }

    // ---------- chuyển dữ liệu cũ data/users.db (nếu có) sang MySQL, chỉ khi bảng users còn trống ----------

    private void importLegacyFile() {
        if (!Files.exists(LEGACY_FILE)) return;
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            if (rs.getInt(1) > 0) return;

            int imported = 0;
            try (BufferedReader r = Files.newBufferedReader(LEGACY_FILE, StandardCharsets.UTF_8);
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT IGNORE INTO users (username, salt, password_hash) VALUES (?, ?, ?)")) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split(":", 3);
                    if (parts.length == 3) {
                        ps.setString(1, parts[0]);
                        ps.setString(2, parts[1]);
                        ps.setString(3, parts[2]);
                        imported += ps.executeUpdate();
                    }
                }
            }
            System.out.println("[UserStore] Imported " + imported + " account(s) from " + LEGACY_FILE);
        } catch (Exception e) {
            System.err.println("[UserStore] Legacy import failed: " + e.getMessage());
        }
    }

    // ---------- hash ----------

    private String hash(String saltHex, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(saltHex.getBytes(StandardCharsets.UTF_8));
            md.update((password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
