-- Tùy chọn: server tự chạy các lệnh này khi khởi động (Database.init()).
-- File này chỉ để bạn xem / import thủ công bằng phpMyAdmin nếu muốn.
CREATE DATABASE IF NOT EXISTS chat_app CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE chat_app;

CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  salt VARCHAR(32) NOT NULL,
  password_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS chat_groups (
  name VARCHAR(100) PRIMARY KEY,
  creator VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS group_members (
  group_name VARCHAR(100) NOT NULL,
  username VARCHAR(50) NOT NULL,
  joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (group_name, username),
  FOREIGN KEY (group_name) REFERENCES chat_groups(name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  message_id VARCHAR(64) NOT NULL UNIQUE,
  msg_type VARCHAR(32) NOT NULL,
  sender VARCHAR(50) NOT NULL,
  receiver VARCHAR(100) NOT NULL,
  is_group TINYINT(1) NOT NULL,
  content TEXT,
  file_name VARCHAR(255),
  file_path VARCHAR(500) NULL,
  sent_at BIGINT NOT NULL,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_sender (sender),
  INDEX idx_receiver (receiver)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS group_invites (
  group_name VARCHAR(100) NOT NULL,
  username VARCHAR(50) NOT NULL,
  inviter VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (group_name, username),
  FOREIGN KEY (group_name) REFERENCES chat_groups(name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
