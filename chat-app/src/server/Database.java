package server;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Kết nối MySQL (XAMPP) + tự tạo database và các bảng ở lần chạy đầu tiên.
 *
 * Nếu MySQL của bạn có mật khẩu / cổng khác mặc định thì chỉ cần sửa 4 hằng số bên dưới.
 */
public final class Database {

    // ====== CẤU HÌNH — sửa ở đây nếu cần ======
    private static final String HOST = "localhost";
    private static final int    PORT = 3306;
    private static final String DB_NAME = "chat_app";
    private static final String USER = "root";
    private static final String PASSWORD = "";      // XAMPP mặc định: root, không mật khẩu
    // ===========================================

    private static final String PARAMS =
            "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
            + "&allowPublicKeyRetrieval=true&sslMode=DISABLED";

    private static volatile boolean driverLoaded = false;

    private Database() {}

    private static synchronized void ensureDriverLoaded() throws SQLException {
        if (driverLoaded) return;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            driverLoaded = true;
            return;
        } catch (ClassNotFoundException ignored) {
        }

        // Tự động nạp driver nếu classpath bị bỏ qua (do đường dẫn chứa tiếng Việt có dấu)
        File[] candidates = new File[] {
            new File("lib/mysql-connector-j-26.7.0.jar"),
            new File(".idea/libraries/mysql-connector-j-26.7.0.jar"),
            new File("../lib/mysql-connector-j-26.7.0.jar"),
            new File("chat-app/lib/mysql-connector-j-26.7.0.jar")
        };

        File jarFile = null;
        for (File f : candidates) {
            if (f.exists()) {
                jarFile = f.getAbsoluteFile();
                break;
            }
        }

        if (jarFile == null) {
            throw new SQLException("Khong tim thay file mysql-connector-j-26.7.0.jar trong thu muc lib/ hoac .idea/libraries/");
        }

        try {
            URLClassLoader ucl = new URLClassLoader(
                new URL[] { jarFile.toURI().toURL() },
                Database.class.getClassLoader()
            );
            Class<?> driverClass = Class.forName("com.mysql.cj.jdbc.Driver", true, ucl);
            final Driver realDriver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new Driver() {
                @Override public Connection connect(String url, Properties info) throws SQLException { return realDriver.connect(url, info); }
                @Override public boolean acceptsURL(String url) throws SQLException { return realDriver.acceptsURL(url); }
                @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException { return realDriver.getPropertyInfo(url, info); }
                @Override public int getMajorVersion() { return realDriver.getMajorVersion(); }
                @Override public int getMinorVersion() { return realDriver.getMinorVersion(); }
                @Override public boolean jdbcCompliant() { return realDriver.jdbcCompliant(); }
                @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return realDriver.getParentLogger(); }
            });
            driverLoaded = true;
            System.out.println("[Database] Da tu dong nap MySQL Driver tu " + jarFile.getPath());
        } catch (Exception e) {
            throw new SQLException("Khong the nap MySQL Driver: " + e.getMessage(), e);
        }
    }

    /** Mở một kết nối mới tới database chat_app. Dùng xong nhớ đóng (try-with-resources). */
    public static Connection getConnection() throws SQLException {
        ensureDriverLoaded();
        return DriverManager.getConnection(
                "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB_NAME + PARAMS, USER, PASSWORD);
    }

    /** Gọi 1 lần khi server khởi động: tạo database + bảng nếu chưa có. */
    public static void init() throws SQLException {
        ensureDriverLoaded();

        // Bước 1: kết nối vào MySQL (chưa chọn database) để tạo database
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://" + HOST + ":" + PORT + "/" + PARAMS, USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + DB_NAME + "` "
                    + "CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
        }

        // Bước 2: tạo các bảng
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users ("
              + " id INT AUTO_INCREMENT PRIMARY KEY,"
              + " username VARCHAR(50) NOT NULL UNIQUE,"
              + " salt VARCHAR(32) NOT NULL,"
              + " password_hash VARCHAR(64) NOT NULL,"
              + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS chat_groups ("
              + " name VARCHAR(100) PRIMARY KEY,"
              + " creator VARCHAR(50) NOT NULL,"
              + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS group_members ("
              + " group_name VARCHAR(100) NOT NULL,"
              + " username VARCHAR(50) NOT NULL,"
              + " joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
              + " PRIMARY KEY (group_name, username),"
              + " FOREIGN KEY (group_name) REFERENCES chat_groups(name) ON DELETE CASCADE"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS messages ("
              + " id BIGINT AUTO_INCREMENT PRIMARY KEY,"
              + " message_id VARCHAR(64) NOT NULL UNIQUE,"
              + " msg_type VARCHAR(32) NOT NULL,"
              + " sender VARCHAR(50) NOT NULL,"
              + " receiver VARCHAR(100) NOT NULL,"
              + " is_group TINYINT(1) NOT NULL,"
              + " content TEXT,"
              + " file_name VARCHAR(255),"
              + " sent_at BIGINT NOT NULL,"
              + " is_deleted TINYINT(1) NOT NULL DEFAULT 0,"
              + " INDEX idx_sender (sender),"
              + " INDEX idx_receiver (receiver)"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");

            // Lời mời vào nhóm đang chờ trả lời (người được mời có thể đang offline)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS group_invites ("
              + " group_name VARCHAR(100) NOT NULL,"
              + " username VARCHAR(50) NOT NULL,"
              + " inviter VARCHAR(50) NOT NULL,"
              + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
              + " PRIMARY KEY (group_name, username),"
              + " FOREIGN KEY (group_name) REFERENCES chat_groups(name) ON DELETE CASCADE"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");

            // Nâng cấp database cũ: thêm cột lưu đường dẫn file của ảnh/tệp/sticker
            addColumnIfMissing(c, "messages", "file_path", "VARCHAR(500) NULL");
        }
        System.out.println("[Database] Connected to MySQL, database '" + DB_NAME + "' is ready");
    }

    private static void addColumnIfMissing(Connection c, String table, String column, String definition)
            throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getColumns(c.getCatalog(), null, table, column)) {
            if (rs.next()) return;
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
