package server;

import common.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lưu / đọc lịch sử tin nhắn trong bảng `messages`.
 * Ảnh, tệp, sticker: nội dung nhị phân được ghi ra đĩa qua {@link FileStorage},
 * trong DB chỉ lưu tên file + đường dẫn (cột file_path).
 */
public final class MessageStore {

    /** Số tin nhắn gần nhất gửi lại cho client mỗi lần đăng nhập. */
    private static final int HISTORY_LIMIT = 300;

    /** File lớn hơn mức này sẽ không gửi lại trong lịch sử (chỉ hiện dòng chữ mô tả). */
    private static final long MAX_HISTORY_FILE_BYTES = 8L * 1024 * 1024;

    private static final String COLUMNS =
            "id, message_id, msg_type, sender, receiver, is_group, content, file_name, file_path, sent_at";

    private MessageStore() {}

    /** Loại tin nào được lưu vào DB. */
    public static boolean isPersistable(Message.Type type) {
        if (type == null) return false;
        switch (type) {
            case PRIVATE_TEXT: case GROUP_TEXT:
            case PRIVATE_EMOJI: case GROUP_EMOJI:
            case PRIVATE_LOCATION: case GROUP_LOCATION:
            case PRIVATE_STICKER: case GROUP_STICKER:
            case PRIVATE_IMAGE: case GROUP_IMAGE:
            case PRIVATE_FILE: case GROUP_FILE:
                return true;
            default:
                return false;
        }
    }

    public static void save(Message m) {
        if (!isPersistable(m.getType()) || m.getSender() == null || m.getReceiver() == null) return;
        String id = m.getMessageId() != null ? m.getMessageId() : UUID.randomUUID().toString();

        // Ghi nội dung nhị phân ra đĩa, DB chỉ giữ đường dẫn
        String filePath = null;
        String typeName = m.getType().name();
        if (m.getFileData() != null) {
            if (typeName.endsWith("_STICKER")) {
                filePath = FileStorage.storeSticker(m.getFileName(), m.getFileData());
            } else if (typeName.endsWith("_IMAGE") || typeName.endsWith("_FILE")) {
                filePath = FileStorage.storeFile(id, m.getFileName(), m.getFileData());
            }
        }

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO messages "
                   + "(message_id, msg_type, sender, receiver, is_group, content, file_name, file_path, sent_at) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, typeName);
            ps.setString(3, m.getSender());
            ps.setString(4, m.getReceiver());
            ps.setBoolean(5, m.isGroupType());
            ps.setString(6, m.getContent());
            ps.setString(7, m.getFileName());
            ps.setString(8, filePath);
            ps.setLong(9, m.getTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MessageStore] save failed: " + e.getMessage());
        }
    }

    /** Đánh dấu đã xoá (chỉ người gửi mới xoá được tin của mình) và xoá file đính kèm khỏi đĩa. */
    public static void markDeleted(String messageId, String requester) {
        if (messageId == null || requester == null) return;
        try (Connection c = Database.getConnection()) {
            String path = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT file_path FROM messages WHERE message_id = ? AND sender = ? AND is_deleted = 0")) {
                ps.setString(1, messageId);
                ps.setString(2, requester);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) path = rs.getString(1);
                }
            }
            int updated;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE messages SET is_deleted = 1 WHERE message_id = ? AND sender = ?")) {
                ps.setString(1, messageId);
                ps.setString(2, requester);
                updated = ps.executeUpdate();
            }
            if (updated > 0) FileStorage.delete(path);
        } catch (SQLException e) {
            System.err.println("[MessageStore] markDeleted failed: " + e.getMessage());
        }
    }

    /** Xoá toàn bộ tin của một nhóm (dùng khi nhóm giải tán vì không còn thành viên). */
    public static void deleteGroupMessages(String groupName) {
        if (groupName == null) return;
        List<String> paths = new ArrayList<>();
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT file_path FROM messages WHERE is_group = 1 AND receiver = ? AND file_path IS NOT NULL")) {
                ps.setString(1, groupName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) paths.add(rs.getString(1));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM messages WHERE is_group = 1 AND receiver = ?")) {
                ps.setString(1, groupName);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[MessageStore] deleteGroupMessages failed: " + e.getMessage());
            return;
        }
        for (String p : paths) FileStorage.delete(p);
    }

    /**
     * Lịch sử của một user: tin 1-1 mà user là người gửi/nhận + tin trong các nhóm user tham gia.
     * Trả về theo thứ tự cũ -> mới.
     */
    public static List<Message> loadHistory(String username, Collection<String> groupNames) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM ("
              + " SELECT " + COLUMNS
              + " FROM messages WHERE is_deleted = 0 AND ("
              + "  (is_group = 0 AND (sender = ? OR receiver = ?))");
        if (!groupNames.isEmpty()) {
            sql.append(" OR (is_group = 1 AND receiver IN (");
            for (int i = 0; i < groupNames.size(); i++) sql.append(i == 0 ? "?" : ",?");
            sql.append("))");
        }
        sql.append(" ) ORDER BY id DESC LIMIT ?) t ORDER BY id ASC");

        List<String> params = new ArrayList<>();
        params.add(username);
        params.add(username);
        params.addAll(groupNames);
        return query(sql.toString(), params);
    }

    /** Lịch sử của riêng một nhóm (gửi cho người vừa được thêm vào nhóm). */
    public static List<Message> loadGroupHistory(String groupName) {
        String sql = "SELECT * FROM ("
                   + " SELECT " + COLUMNS
                   + " FROM messages WHERE is_deleted = 0 AND is_group = 1 AND receiver = ?"
                   + " ORDER BY id DESC LIMIT ?) t ORDER BY id ASC";
        List<String> params = new ArrayList<>();
        params.add(groupName);
        return query(sql, params);
    }

    /** Chạy truy vấn: các tham số chuỗi theo thứ tự, tham số cuối luôn là HISTORY_LIMIT. */
    private static List<Message> query(String sql, List<String> params) {
        List<Message> result = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (String p : params) ps.setString(i++, p);
            ps.setInt(i, HISTORY_LIMIT);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(toHistoryMessage(rs));
            }
        } catch (SQLException e) {
            System.err.println("[MessageStore] load history failed: " + e.getMessage());
        }
        return result;
    }

    private static Message toHistoryMessage(ResultSet rs) throws SQLException {
        Message.Type type = Message.Type.valueOf(rs.getString("msg_type"));
        boolean group = rs.getBoolean("is_group");
        String content = rs.getString("content");
        String fileName = rs.getString("file_name");
        String filePath = rs.getString("file_path");

        byte[] data = null;
        String name = type.name();
        boolean media = name.endsWith("_IMAGE") || name.endsWith("_FILE") || name.endsWith("_STICKER");
        if (media) {
            data = FileStorage.read(filePath, MAX_HISTORY_FILE_BYTES);
            if (data == null) {
                // Tin cũ (chưa lưu file), file đã mất hoặc quá lớn -> hiển thị dạng chữ
                type = group ? Message.Type.GROUP_TEXT : Message.Type.PRIVATE_TEXT;
                String label = fileName != null ? fileName : "";
                if (name.endsWith("_IMAGE")) {
                    content = "[Hình ảnh: " + label + "]";
                } else if (name.endsWith("_FILE")) {
                    long size = FileStorage.size(filePath);
                    content = "[Tệp đính kèm: " + label + (size > MAX_HISTORY_FILE_BYTES ? " - quá lớn để tải lại" : "") + "]";
                } else {
                    content = "[Nhãn dán]";
                }
            }
        }

        Message m = new Message(type, rs.getString("sender"), rs.getString("receiver"), content);
        m.setMessageId(rs.getString("message_id"));
        m.setTimestamp(rs.getLong("sent_at"));
        if (data != null) {
            m.setFileName(fileName);
            m.setFileData(data);
        }
        return m;
    }
}
