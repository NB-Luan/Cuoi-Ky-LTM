# Đồ Án Môn Học: Lập Trình Mạng (Network Programming)
## ỨNG DỤNG CHAT CLIENT - SERVER QUA GIAO THỨC TCP SOCKET KẾT HỢP MYSQL

---

## 📌 Giới Thiệu Đề Tài
Đồ án xây dựng hệ thống phần mềm trò chuyện trực tuyến (Chat Application) theo mô hình **Client - Server** sử dụng ngôn ngữ **Java** và giao thức truyền vận **TCP Socket**, tích hợp hệ quản trị cơ sở dữ liệu **MySQL** để quản lý người dùng và lưu trữ lịch sử tin nhắn.

Giao diện ứng dụng được thiết kế hiện đại bằng **Java Swing** và **Java2D** (phong cách phẳng - Flat UI, giao diện bong bóng chat hiện đại), tối ưu hóa trải nghiệm người dùng mà không cần phụ thuộc vào các thư viện giao diện cồng kềnh từ bên ngoài.

---

## 🚀 Các Tính Năng Nổi Bật

### 1. Kiến Trúc Mạng & Xử Lý Đa Luồng (Networking & Concurrency)
- **Giao thức**: TCP Socket đảm bảo độ tin cậy, không mất gói tin và đúng thứ tự.
- **Mô hình xử lý**: `ExecutorService` (Cached ThreadPool) trên Server cho phép phục vụ đồng thời nhiều Client kết nối.
- **Định tuyến tin nhắn**: Đóng gói đối tượng `Message` tuần tự hóa (`Serializable`), Server định tuyến thông minh theo loại tin và người nhận.

### 2. Quản Lý Tài Khoản & Bảo Mật
- Đăng ký và đăng nhập tài khoản an toàn.
- Mã hóa mật khẩu một chiều sử dụng thuật toán **SHA-256** kết hợp chuỗi muối ngẫu nhiên (**Salt**).
- Theo dõi trạng thái hoạt động (Online/Offline) của người dùng theo thời gian thực.

### 3. Nhắn Tin 1 - 1 & Nhắn Tin Nhóm
- **Chat riêng (1-1)**: Trò chuyện trực tiếp giữa hai người dùng.
- **Chat nhóm**: Tạo nhóm mới, mời thành viên tham gia (hỗ trợ lưu lời mời khi người được mời đang offline), rời nhóm và tự động giải tán nhóm khi thành viên cuối cùng rời đi.
- **Lịch sử tin nhắn**: Tự động tải lại lịch sử trò chuyện gần nhất từ MySQL khi người dùng đăng nhập.
- **Thu hồi tin nhắn**: Cho phép người gửi xóa tin nhắn đối với cả hai bên, đồng thời xóa file đính kèm trên server.

### 4. Đa Phương Tiện & Truyền Tệp Tin
- **Emoji & Sticker**: Hỗ trợ bộ biểu tượng Emoji Unicode và Sticker vector được sinh động bằng Java2D.
- **Gửi Vị Trí (Location)**: Chia sẻ tọa độ GPS kèm liên kết mở nhanh trên Google Maps.
- **Truyền File & Ảnh**:
  - Gửi ảnh hiển thị thumbnail trực quan ngay trong khung chat.
  - Gửi tệp đính kèm với cơ chế tải về (`~/chat-downloads`) và mở trực tiếp bằng phần mềm mặc định.
  - Lưu trữ file phân tán tại `~/chat-server-uploads` trên Server.

### 5. Gọi Video Thời Gian Thực (Video Call 1-1)
- Truyền tải khung hình video nén định dạng JPEG qua Socket.
- Hỗ trợ âm thanh hai chiều (PCM 16 kHz mono) thông qua `javax.sound.sampled`.
- Cơ chế linh hoạt: Tự động chuyển đổi sang chia sẻ màn hình (`java.awt.Robot`) nếu thiết bị không có webcam hoặc thiếu thư viện camera.

---

## 🛠️ Công Nghệ Sử Dụng

| Thành phần | Công nghệ / Thư viện |
| :--- | :--- |
| **Ngôn ngữ** | Java (JDK 17 / 21 / 25) |
| **Giao thức mạng** | TCP Sockets (`java.net.ServerSocket`, `java.net.Socket`) |
| **Giao diện người dùng** | Java Swing, Java2D Graphics |
| **Cơ sở dữ liệu** | MySQL Server (XAMPP / MariaDB) |
| **Kết nối CSDL** | JDBC (`mysql-connector-j`) |
| **Cơ chế truyền dữ liệu** | Java Object Serialization (`Message` Object) |

---

## 📂 Cấu Trúc Thư Mục Dự Án

```
├── .idea/                      # Cấu hình dự án IntelliJ IDEA
├── .vscode/                     # Cấu hình dự án Antigravity IDE / VS Code
├── chat-app/
│   ├── src/
│   │   ├── common/             # Đối tượng dữ liệu dùng chung
│   │   │   └── Message.java
│   │   ├── server/             # Mã nguồn phía Server
│   │   │   ├── ChatServer.java
│   │   │   ├── ClientHandler.java
│   │   │   ├── Database.java
│   │   │   ├── FileStorage.java
│   │   │   ├── GroupStore.java
│   │   │   ├── MessageStore.java
│   │   │   └── UserStore.java
│   │   └── client/             # Mã nguồn phía Client
│   │       ├── CameraSource.java
│   │       ├── ChatClient.java
│   │       ├── ChatPanel.java
│   │       ├── EmojiPicker.java
│   │       ├── LoginFrame.java
│   │       ├── MainFrame.java
│   │       ├── StickerPicker.java
│   │       ├── UI.java
│   │       └── VideoCallFrame.java
├── database/
│   └── schema.sql              # Cấu trúc bảng CSDL MySQL
├── lib/                        # Thư viện MySQL JDBC Driver
│   └── mysql-connector-j-26.7.0.jar
├── build.bat                   # Kịch bản tự động biên dịch toàn bộ mã nguồn
├── run-server.bat              # Kịch bản chạy Server
├── run-client.bat              # Kịch bản chạy Client
├── start-demo.bat              # Kịch bản 1-click: tự động bật Server + 2 Client
└── README.md
```

---

## ⚙️ Hướng Dẫn Cài Đặt & Khởi Chạy

### 1. Yêu cầu môi trường
- Đã cài đặt **JDK 8+** (khuyên dùng JDK 17, 21 hoặc 25).
- Đã cài đặt **XAMPP** (hoặc MySQL Server độc lập).

### 2. Chuẩn bị Cơ Sở Dữ Liệu
1. Mở XAMPP Control Panel và nhấn **Start** tại module **MySQL** (mặc định cổng `3306`).
2. Server sẽ **tự động khởi tạo** database `chat_app` cùng toàn bộ các bảng ở lần chạy đầu tiên.

### 3. Cách chạy ứng dụng

#### 👉 Cách 1: Chạy nhanh bằng kịch bản Batch (Khuyên dùng khi demo)
- **Chạy toàn bộ hệ thống (Server + 2 Client)**: Click đúp vào file `start-demo.bat`.
- **Chạy riêng Server**: Click đúp vào file `run-server.bat` (Server lắng nghe tại port `9999`).
- **Mở thêm Client**: Click đúp vào file `run-client.bat` để mở thêm nhiều cửa sổ đăng nhập khác nhau.

#### 👉 Cách 2: Chạy trong Antigravity IDE / VS Code
- Chạy Server: Mở file `chat-app/src/server/ChatServer.java` ➔ Nhấn nút **Run** (▶) ở góc trên bên phải.
- Chạy Client: Mở file `chat-app/src/client/LoginFrame.java` ➔ Nhấn nút **Run** (▶).

#### 👉 Cách 3: Chạy trong IntelliJ IDEA
- Mở thư mục dự án trong IntelliJ IDEA.
- Chuột phải vào `ChatServer.java` ➔ Chọn **Run 'ChatServer.main()'**.
- Chuột phải vào `LoginFrame.java` ➔ Chọn **Run 'LoginFrame.main()'**.
