# Java TCP Chat

Chương trình chat client-server bằng Java thuần (TCP socket, `ObjectInputStream`/
`ObjectOutputStream`), không phụ thuộc thư viện ngoài. Hỗ trợ:

- Đăng nhập bằng username (không trùng)
- **Chat đơn** (1-1) và **chat nhóm** (tạo nhóm, thêm thành viên)
- Gửi **emoji** (unicode) và **sticker** (ảnh PNG sinh sẵn, không cần asset ngoài)
- Gửi **vị trí** (lat,lng → kèm link Google Maps)
- Gửi **ảnh** (hiển thị thumbnail trong khung chat) và **file** bất kỳ (tự lưu vào
  `~/chat-downloads` khi nhận)
- **Video call** 1-1: xem ghi chú bên dưới

## Cấu trúc

```
chat-app/
  src/
    common/Message.java        # đối tượng message dùng chung (Serializable)
    server/ChatServer.java     # server: accept, quản lý user/group, định tuyến
    server/ClientHandler.java  # thread xử lý 1 kết nối client
    client/ChatClient.java     # lớp kết nối + gửi/nhận message
    client/LoginFrame.java     # màn hình đăng nhập
    client/MainFrame.java      # cửa sổ chat chính (Swing)
    client/EmojiPicker.java    # popup chọn emoji
    client/StickerPicker.java  # popup chọn sticker (vẽ bằng Java2D)
    client/VideoCallFrame.java # cửa sổ video call
    client/UI.java             # theme dùng chung: màu, font, icon vector, nút bo góc
    client/ChatPanel.java      # khung hội thoại dạng bong bóng tin nhắn
```

## Giao diện

Toàn bộ phần nhìn được gom vào `client/UI.java` (bảng màu, font, icon vector vẽ
bằng Java2D, nút/ô nhập bo góc, thanh cuộn mảnh) nên muốn đổi tông màu chỉ cần
sửa các hằng `PRIMARY`, `APP_BG`, `TEXT`... ở đầu file đó.

- Màn hình đăng nhập: cột trái gradient giới thiệu tính năng, cột phải là form
  có placeholder và thông báo lỗi ngay dưới nút.
- Cửa sổ chat: sidebar có avatar sinh tự động theo tên + chấm trạng thái
  online/offline, header hiển thị đối tượng đang chat, khung hội thoại dạng
  **bong bóng** (tin của mình nằm bên phải, nền gradient tím-xanh), thanh soạn
  tin có các nút icon tròn.
- Tin nhắn hệ thống hiển thị dạng "viên thuốc" căn giữa; tin bị thu hồi biến
  thành bong bóng xám mờ "Tin nhắn đã bị xoá".
- Không dùng thư viện ngoài, không cần file ảnh: avatar, icon và sticker đều
  được vẽ lúc chạy.

## Biên dịch & chạy

Yêu cầu JDK 8+ (đã test cấu trúc với JDK 17/21).

```bash
cd chat-app/src
javac common/Message.java server/*.java client/*.java -d ../out

# Terminal 1: chạy server (mặc định cổng 9999)
cd ../out
java server.ChatServer 9999

# Terminal 2, 3, ...: mỗi client một cửa sổ
java client.LoginFrame
```

Trong cửa sổ đăng nhập, nhập host (mặc định `127.0.0.1`), port và username rồi bấm
**Connect**. Mở nhiều instance `client.LoginFrame` (trên cùng máy hoặc máy khác
trong LAN, trỏ đúng IP server) để test chat giữa nhiều người.

## Cách dùng

- **Chat đơn**: chọn một user trong danh sách "Online users" bên trái, gõ tin
  nhắn và bấm Send/Enter.
- **Chat nhóm**: bấm "+ New group", đặt tên nhóm và liệt kê username thành viên
  (cách nhau bởi dấu phẩy). Nhóm xuất hiện trong "My groups" cho mọi thành viên
  đang online; chọn nhóm để chat chung.
- **Mời thêm thành viên**: mọi thành viên của nhóm đều có nút **Mời thêm** ở header
  nhóm, chọn một hoặc nhiều người trong danh sách. Người được mời phải đồng ý mới vào
  nhóm; nếu họ đang offline, lời mời được lưu trong DB và hiện ra khi họ đăng nhập.
  Người mới vào nhóm sẽ thấy lại lịch sử của nhóm.
- **Rời nhóm**: nút **Rời nhóm** ở header nhóm. Sau khi rời, nhóm biến mất khỏi danh
  sách và người đó không gửi/nhận được tin của nhóm nữa. Khi thành viên cuối cùng rời
  đi, nhóm bị giải tán (xoá nhóm cùng lịch sử của nhóm).
- **Emoji / Sticker**: bấm nút mặt cười / nhãn dán ở thanh soạn tin.
- **Location**: bấm "Location", nhập kinh độ/vĩ độ (demo nhập tay).
- **Image / File**: bấm nút tương ứng, chọn file từ máy. Tệp đính kèm hiện thành một
  bong bóng có nút **Lưu về...** (chọn nơi lưu) và **Mở** (mở bằng chương trình mặc định),
  cả người gửi lẫn người nhận đều bấm được, kể cả sau khi đăng nhập lại. File nhận được
  cũng tự lưu vào `~/chat-downloads`.
- **Video Call**: chọn 1 user (không dùng cho nhóm) rồi bấm "Gọi video" ở header. Người
  nhận sẽ thấy hộp thoại Accept/Reject.

## Video Call

- **Hình ảnh**: dùng webcam thật nếu project có thư viện
  [Webcam Capture (sarxos)](https://github.com/sarxos/webcam-capture). Chưa có thư viện hoặc không có
  camera thì tự chuyển sang **chia sẻ màn hình** (`java.awt.Robot`) và hiện lý do ở dòng trạng thái.
  Cách bật webcam: IntelliJ → File → Project Structure → Libraries → thêm `webcam-capture-0.3.12.jar`
  cùng `slf4j-api` và `bridj` (có trong bản `-dist` của webcam-capture). Không cần sửa code.
- **Âm thanh**: micro/loa qua `javax.sound.sampled` (có sẵn trong JDK), PCM 16 kHz mono, gói 100 ms
  (`Message.Type.AUDIO_FRAME`). Nên đeo tai nghe khi thử 2 client trên cùng một máy để tránh hú.
- **Nút trong cuộc gọi**: Tắt/Bật mic, Tắt/Bật camera, Kết thúc.
- **Tự kết thúc** khi: người kia không nghe máy sau 30 giây, người kia offline/đang bận (báo ngay),
  hoặc không nhận được gì từ người kia quá 10 giây (mất mạng, tắt ứng dụng).
- Chỉ có 1 cuộc gọi tại một thời điểm; gọi đến khi đang bận sẽ tự từ chối với lý do "bận".
- Việc chụp hình, nén JPEG và gửi được làm trên thread riêng nên giao diện không bị đơ.

## Giao thức (tóm tắt)

Mỗi gói tin là một đối tượng `Message` (Serializable) gồm: `type`, `sender`,
`receiver` (username cho tin riêng, tên nhóm cho tin nhóm), `content` (text /
emoji / "lat,lng"), `fileName`, `fileData` (byte[] cho ảnh/file/sticker/khung
video), `timestamp`. Server chỉ định tuyến (route) theo `type`/`receiver`,
không lưu lịch sử tin nhắn (in-memory, không có DB) — phù hợp cho mục đích học
tập/demo. Có thể mở rộng thêm JDBC/file log để lưu lịch sử nếu cần.

## Database (MySQL / XAMPP)

Server lưu **tài khoản, nhóm chat, thành viên nhóm và lịch sử tin nhắn** vào MySQL
(database `chat_app`, tự tạo ở lần chạy đầu). Khi đăng nhập, client nhận lại tối đa 300 tin gần nhất.

1. Mở XAMPP Control Panel → **Start** module **MySQL**.
2. IntelliJ: File → Project Structure → Libraries → thêm `mysql-connector-j-x.x.x.jar`.
3. Chạy `server.ChatServer`, sau đó chạy `client.LoginFrame` như bình thường.

Cấu hình kết nối (user, mật khẩu, cổng) ở đầu file `server/Database.java`.
Các bảng: `users`, `chat_groups`, `group_members`, `group_invites`, `messages`
(xem `database/schema.sql`). Database cũ được server tự nâng cấp (thêm cột
`messages.file_path` và bảng `group_invites`) khi khởi động.

**Ảnh / file / sticker**: nội dung được server ghi ra thư mục `~/chat-server-uploads`
(đổi ở hằng `ROOT` trong `server/FileStorage.java`), trong DB chỉ lưu tên và đường dẫn.
Khi đăng nhập lại, ảnh và sticker hiện lại như cũ; tệp đính kèm được lưu lại vào
`~/chat-downloads` nếu chưa có. File lớn hơn 8 MB chỉ hiện dòng chữ mô tả (hằng
`MAX_HISTORY_FILE_BYTES` trong `MessageStore`). Tin nhắn ảnh/file gửi trước bản cập nhật
này không có file lưu lại nên vẫn hiển thị dạng chữ. Thu hồi tin sẽ xoá luôn file trên server.
