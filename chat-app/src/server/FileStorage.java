package server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Lưu nội dung nhị phân (ảnh, tệp, sticker) ra đĩa của server để lịch sử chat khôi phục được.
 * Trong MySQL chỉ lưu đường dẫn tương đối (cột messages.file_path).
 *
 * Thư mục gốc: ~/chat-server-uploads  (đổi hằng ROOT bên dưới nếu muốn).
 */
public final class FileStorage {

    private static final Path ROOT = Paths.get(System.getProperty("user.home"), "chat-server-uploads");
    private static final String STICKER_DIR = "stickers";

    private FileStorage() {}

    public static boolean isSharedPath(String relPath) {
        return relPath != null && relPath.startsWith(STICKER_DIR + "/");
    }

    /** Ghi ảnh/tệp. Trả về đường dẫn tương đối để lưu vào DB, hoặc null nếu lỗi. */
    public static String storeFile(String messageId, String fileName, byte[] data) {
        if (data == null) return null;
        String rel = safe(messageId) + "_" + safe(baseName(fileName));
        return write(rel, data, true) ? rel : null;
    }

    /** Ghi sticker (dùng chung theo tên, chỉ ghi một lần). */
    public static String storeSticker(String stickerId, byte[] data) {
        if (data == null) return null;
        String rel = STICKER_DIR + "/" + safe(stickerId) + ".png";
        return write(rel, data, false) ? rel : null;
    }

    /** Đọc lại nội dung; trả về null nếu không có hoặc lớn hơn maxBytes. */
    public static byte[] read(String relPath, long maxBytes) {
        if (relPath == null) return null;
        try {
            Path p = resolve(relPath);
            if (p == null || !Files.isRegularFile(p) || Files.size(p) > maxBytes) return null;
            return Files.readAllBytes(p);
        } catch (IOException e) {
            System.err.println("[FileStorage] read failed: " + e.getMessage());
            return null;
        }
    }

    public static long size(String relPath) {
        try {
            Path p = resolve(relPath);
            return (p != null && Files.isRegularFile(p)) ? Files.size(p) : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    /** Xoá file của tin bị thu hồi. Sticker dùng chung nên không xoá. */
    public static void delete(String relPath) {
        if (relPath == null || isSharedPath(relPath)) return;
        try {
            Path p = resolve(relPath);
            if (p != null) Files.deleteIfExists(p);
        } catch (IOException e) {
            System.err.println("[FileStorage] delete failed: " + e.getMessage());
        }
    }

    private static boolean write(String rel, byte[] data, boolean overwrite) {
        try {
            Path p = resolve(rel);
            if (p == null) return false;
            if (!overwrite && Files.exists(p)) return true;
            Files.createDirectories(p.getParent());
            Path tmp = Files.createTempFile(p.getParent(), "upload", ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("[FileStorage] write failed: " + e.getMessage());
            return false;
        }
    }

    /** Ghép đường dẫn và chặn thoát ra ngoài thư mục gốc (path traversal). */
    private static Path resolve(String rel) {
        Path p = ROOT.resolve(rel).normalize();
        return p.startsWith(ROOT) ? p : null;
    }

    private static String baseName(String name) {
        if (name == null || name.isEmpty()) return "file";
        int i = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String b = i >= 0 ? name.substring(i + 1) : name;
        return b.isEmpty() ? "file" : b;
    }

    private static String safe(String s) {
        if (s == null || s.isEmpty()) return "x";
        String r = s.replaceAll("[^A-Za-z0-9._-]", "_");
        if (r.length() > 120) r = r.substring(r.length() - 120);
        return r.startsWith(".") ? "_" + r : r;
    }
}
