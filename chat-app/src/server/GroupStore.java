package server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Lưu nhóm chat và danh sách thành viên (bảng `chat_groups`, `group_members`). */
public final class GroupStore {

    private GroupStore() {}

    /** Đọc toàn bộ nhóm khi server khởi động: tên nhóm -> tập thành viên. */
    public static Map<String, Set<String>> loadAll() {
        Map<String, Set<String>> groups = new HashMap<>();
        try (Connection c = Database.getConnection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT name FROM chat_groups")) {
                while (rs.next()) groups.put(rs.getString(1), ConcurrentHashMap.<String>newKeySet());
            }
            try (ResultSet rs = st.executeQuery("SELECT group_name, username FROM group_members")) {
                while (rs.next()) {
                    Set<String> members = groups.get(rs.getString(1));
                    if (members != null) members.add(rs.getString(2));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupStore] loadAll failed: " + e.getMessage());
        }
        return groups;
    }

    public static void createGroup(String name, String creator) {
        run("INSERT IGNORE INTO chat_groups (name, creator) VALUES (?, ?)", name, creator);
    }

    public static void addMember(String group, String username) {
        run("INSERT IGNORE INTO group_members (group_name, username) VALUES (?, ?)", group, username);
    }

    public static void removeMember(String group, String username) {
        run("DELETE FROM group_members WHERE group_name = ? AND username = ?", group, username);
    }

    /** Xoá hẳn nhóm (thành viên và lời mời tự xoá theo nhờ ON DELETE CASCADE). */
    public static void deleteGroup(String name) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM chat_groups WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[GroupStore] deleteGroup failed: " + e.getMessage());
        }
    }

    // ---------- lời mời đang chờ ----------

    public static void addInvite(String group, String username, String inviter) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO group_invites (group_name, username, inviter) VALUES (?, ?, ?)")) {
            ps.setString(1, group);
            ps.setString(2, username);
            ps.setString(3, inviter);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[GroupStore] addInvite failed: " + e.getMessage());
        }
    }

    /** Xoá lời mời; trả về true nếu lời mời có tồn tại. */
    public static boolean removeInvite(String group, String username) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM group_invites WHERE group_name = ? AND username = ?")) {
            ps.setString(1, group);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[GroupStore] removeInvite failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean hasInvite(String group, String username) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM group_invites WHERE group_name = ? AND username = ?")) {
            ps.setString(1, group);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[GroupStore] hasInvite failed: " + e.getMessage());
            return false;
        }
    }

    /** Các lời mời đang chờ của một user: mỗi phần tử là {tênNhóm, ngườiMời}. */
    public static List<String[]> loadInvites(String username) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT group_name, inviter FROM group_invites WHERE username = ? ORDER BY created_at")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(new String[]{rs.getString(1), rs.getString(2)});
            }
        } catch (SQLException e) {
            System.err.println("[GroupStore] loadInvites failed: " + e.getMessage());
        }
        return list;
    }

    private static void run(String sql, String a, String b) {
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[GroupStore] " + e.getMessage());
        }
    }
}
