package common;

import java.io.Serializable;

public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        // Authentication / session
        LOGIN, LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT,
        REGISTER, REGISTER_SUCCESS, REGISTER_FAILED,

        // Directory updates pushed by server
        // USER_LIST content format: "user1:ONLINE,user2:OFFLINE,..." (all registered accounts)
        USER_LIST, GROUP_LIST,

        // Group management
        CREATE_GROUP, JOIN_GROUP, LEAVE_GROUP,
        // receiver = group name, sender = inviter/invitee depending on direction
        GROUP_INVITE, GROUP_INVITE_ACCEPT, GROUP_INVITE_DECLINE,
        // Thành viên nhóm mời người khác: sender = người mời, receiver = tên nhóm,
        // content = danh sách username được mời, cách nhau bởi dấu phẩy.
        GROUP_INVITE_REQUEST,

        // Typing indicators. receiver = target user (PRIVATE_TYPING) or group name (GROUP_TYPING).
        // No explicit "stop typing" message: the receiving client auto-hides the
        // indicator a few seconds after the last one arrives.
        PRIVATE_TYPING, GROUP_TYPING,

        // Delivery / read receipts for private (1-1) messages only.
        // content = the messageId of the message being acknowledged.
        // MSG_DELIVERED: sender = who received it, receiver = original sender (server-generated).
        // MSG_SEEN: sender = who viewed it, receiver = original sender (client-generated).
        MSG_DELIVERED, MSG_SEEN,

        // Chat content (PRIVATE_* -> receiver is a username, GROUP_* -> receiver is a group name)
        PRIVATE_TEXT, GROUP_TEXT,
        PRIVATE_EMOJI, GROUP_EMOJI,
        PRIVATE_STICKER, GROUP_STICKER,
        PRIVATE_LOCATION, GROUP_LOCATION,
        PRIVATE_IMAGE, GROUP_IMAGE,
        PRIVATE_FILE, GROUP_FILE,

        // Video call signaling + media (1-1 only)
        VIDEO_CALL_REQUEST, VIDEO_CALL_ACCEPT, VIDEO_CALL_REJECT, VIDEO_CALL_END,
        VIDEO_FRAME,   // fileData = ảnh JPEG; content = "CAM_OFF" (không có fileData) khi đối phương tắt camera / keep-alive
        AUDIO_FRAME,   // fileData = PCM 16kHz, 16-bit, mono, little-endian (~100ms mỗi gói)
        // VIDEO_CALL_REJECT.content: null = từ chối, "busy" = đang bận, "offline" = người nhận không online

        // Message deletion (sender = who requested the delete, receiver = username (PRIVATE)
        // or group name (GROUP) the original message was sent to, content = messageId of the
        // message being deleted). Only the original author may delete their own message;
        // the client enforces this and the server just relays it like any other chat message.
        PRIVATE_DELETE, GROUP_DELETE,

        // Lịch sử tin nhắn lấy từ database khi đăng nhập: tin gốc nằm trong trường `inner`
        HISTORY,

        SYSTEM, ERROR
    }

    private Type type;
    private String sender;
    private String receiver;   // username (private) or group name (group), or null
    private String content;    // text / emoji unicode / sticker id / "lat,lng" / free text / password (LOGIN, REGISTER)
    private String fileName;   // for IMAGE / FILE / STICKER
    private byte[] fileData;   // raw bytes for IMAGE / FILE / STICKER / VIDEO_FRAME
    private long timestamp;
    private String messageId;  // unique id for content messages that need delivery/read receipts
    private Message inner;     // chỉ dùng cho HISTORY: tin nhắn gốc đọc từ database

    public Message() {
        this.timestamp = System.currentTimeMillis();
    }

    public Message(Type type, String sender, String receiver, String content) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public byte[] getFileData() { return fileData; }
    public void setFileData(byte[] fileData) { this.fileData = fileData; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public Message getInner() { return inner; }
    public void setInner(Message inner) { this.inner = inner; }

    public boolean isGroupType() {
        return type != null && type.name().startsWith("GROUP_");
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", sender=" + sender + ", receiver=" + receiver
                + ", content=" + content
                + ", file=" + (fileName != null ? fileName + "(" + (fileData == null ? 0 : fileData.length) + "b)" : "-")
                + "}";
    }
}
