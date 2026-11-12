package server;

import common.Message;

import java.io.IOException;
import java.sql.SQLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {

    public static final int DEFAULT_PORT = 9999;

    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groups = new ConcurrentHashMap<>(); // groupName -> usernames
    private final UserStore userStore = new UserStore();

    public ChatServer(int port) {
        this.port = port;
        groups.putAll(GroupStore.loadAll());   // nạp lại các nhóm đã lưu trong DB
        System.out.println("[Server] Loaded " + groups.size() + " group(s) from database");
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        try {
            Database.init();   // kết nối MySQL + tự tạo database/bảng
        } catch (SQLException e) {
            System.err.println("[Server] Khong ket noi duoc MySQL: " + e.getMessage());
            System.err.println("[Server] Kiem tra: (1) XAMPP da Start MySQL chua?  (2) Da them mysql-connector-j vao project chua?");
            System.err.println("[Server] (3) User/mat khau/port trong server/Database.java co dung khong?");
            return;
        }
        new ChatServer(port).start();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[Server] Listening on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                pool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
        }
    }

    public boolean userExists(String username) {
        return userStore.exists(username);
    }

    public boolean registerAccount(String username, String password) {
        return userStore.register(username, password);
    }

    public boolean authenticate(String username, String password) {
        return userStore.authenticate(username, password);
    }

    public boolean isOnline(String username) {
        return clients.containsKey(username);
    }

    // ---------- registration (live connections) ----------

    public void registerClient(String username, ClientHandler handler) {
        clients.put(username, handler);
    }

    public void removeClient(String username) {
        clients.remove(username);
        // Thành viên nhóm được lưu trong DB nên KHÔNG xoá khỏi nhóm khi ngắt kết nối
    }

    /** Gửi lịch sử chat (từ MySQL) cho user vừa đăng nhập. */
    public void sendHistoryTo(ClientHandler h) {
        String user = h.getUsername();
        List<String> myGroups = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : groups.entrySet()) {
            if (e.getValue().contains(user)) myGroups.add(e.getKey());
        }
        for (Message m : MessageStore.loadHistory(user, myGroups)) {
            Message wrap = new Message(Message.Type.HISTORY, "server", user, null);
            wrap.setInner(m);
            h.sendMessage(wrap);
        }
    }

    /** Gửi lịch sử của một nhóm cho user vừa được thêm vào nhóm đó. */
    private void sendGroupHistoryTo(ClientHandler h, String groupName) {
        for (Message m : MessageStore.loadGroupHistory(groupName)) {
            Message wrap = new Message(Message.Type.HISTORY, "server", h.getUsername(), null);
            wrap.setInner(m);
            h.sendMessage(wrap);
        }
    }

    /** Gửi các lời mời vào nhóm còn đang chờ cho user vừa đăng nhập. */
    public void sendPendingInvitesTo(ClientHandler h) {
        for (String[] inv : GroupStore.loadInvites(h.getUsername())) {
            if (!groups.containsKey(inv[0])) continue;
            h.sendMessage(new Message(Message.Type.GROUP_INVITE, inv[1], inv[0], null));
        }
    }

    public void broadcastUserList() {
        StringBuilder sb = new StringBuilder();
        for (String username : userStore.getAllUsernames()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(username).append(":").append(clients.containsKey(username) ? "ONLINE" : "OFFLINE");
        }
        Message m = new Message(Message.Type.USER_LIST, "server", null, sb.toString());
        for (ClientHandler h : clients.values()) h.sendMessage(m);
    }

    private void sendError(String username, String text) {
        ClientHandler h = clients.get(username);
        if (h != null) h.sendMessage(new Message(Message.Type.ERROR, "server", null, text));
    }

    public synchronized void createGroup(String groupName, String creator, List<String> members) {
        if (groupName == null) return;
        groupName = groupName.trim();
        if (groupName.isEmpty()) return;
        if (groupName.length() > 100 || groupName.matches(".*[:;,].*")) {
            sendError(creator, "Tên nhóm tối đa 100 ký tự và không được chứa dấu : ; ,");
            return;
        }
        if (groups.containsKey(groupName)) {
            sendError(creator, "Tên nhóm '" + groupName + "' đã tồn tại");
            return;
        }
        Set<String> set = ConcurrentHashMap.newKeySet();
        set.add(creator);
        groups.put(groupName, set);
        GroupStore.createGroup(groupName, creator);
        GroupStore.addMember(groupName, creator);
        notifyGroupMembers(groupName, "Group '" + groupName + "' created by " + creator);
        inviteMembers(groupName, creator, members);
    }

    /**
     * Thành viên nhóm mời người khác. Lời mời được lưu trong DB nên người được mời
     * đang offline vẫn nhận được khi đăng nhập lại. Người được mời phải đồng ý mới vào nhóm.
     */
    public synchronized void inviteMembers(String groupName, String inviter, List<String> invitees) {
        Set<String> set = groups.get(groupName);
        if (set == null || !set.contains(inviter)) {
            sendError(inviter, "Bạn không phải thành viên của nhóm '" + groupName + "'");
            return;
        }
        List<String> invited = new ArrayList<>();
        for (String invitee : invitees) {
            if (invitee.equals(inviter)) continue;
            String problem = null;
            if (!userStore.exists(invitee)) {
                problem = "Không có tài khoản '" + invitee + "'";
            } else if (set.contains(invitee)) {
                problem = "'" + invitee + "' đã ở trong nhóm";
            } else if (GroupStore.hasInvite(groupName, invitee)) {
                problem = "'" + invitee + "' đã được mời, đang chờ trả lời";
            }
            if (problem != null) {
                sendError(inviter, problem);
                continue;
            }
            GroupStore.addInvite(groupName, invitee, inviter);
            ClientHandler target = clients.get(invitee);
            if (target != null) {
                target.sendMessage(new Message(Message.Type.GROUP_INVITE, inviter, groupName, null));
            }
            invited.add(invitee);
        }
        if (!invited.isEmpty()) {
            notifyGroupMembers(groupName,
                    inviter + " đã mời " + String.join(", ", invited) + " vào nhóm '" + groupName + "'", false);
        }
    }

    public synchronized void acceptGroupInvite(String groupName, String username) {
        boolean hadInvite = GroupStore.removeInvite(groupName, username);
        Set<String> set = groups.get(groupName);
        if (set == null) {
            sendError(username, "Nhóm '" + groupName + "' không còn tồn tại");
            return;
        }
        if (!hadInvite) {
            if (!set.contains(username)) {
                sendError(username, "Bạn chưa được mời vào nhóm '" + groupName + "'");
            }
            return;
        }
        set.add(username);
        GroupStore.addMember(groupName, username);
        ClientHandler h = clients.get(username);
        if (h != null) {
            sendGroupListTo(h);                 // nhóm phải có trong danh sách trước khi lịch sử tới
            sendGroupHistoryTo(h, groupName);   // người mới thấy lại lịch sử nhóm
        }
        notifyGroupMembers(groupName, username + " đã tham gia nhóm '" + groupName + "'");
    }

    public synchronized void declineGroupInvite(String groupName, String username) {
        if (GroupStore.removeInvite(groupName, username)) {
            notifyGroupMembers(groupName, username + " đã từ chối lời mời vào nhóm '" + groupName + "'", false);
        }
    }

    /** Thành viên tự rời nhóm. Nhóm không còn ai thì bị giải tán (xoá nhóm và lịch sử của nhóm). */
    public synchronized void leaveGroup(String groupName, String username) {
        Set<String> set = groups.get(groupName);
        ClientHandler leaver = clients.get(username);
        if (set == null || !set.remove(username)) {
            sendError(username, "Bạn không ở trong nhóm '" + groupName + "'");
            if (leaver != null) sendGroupListTo(leaver);   // đồng bộ lại danh sách nhóm cho client
            return;
        }
        GroupStore.removeMember(groupName, username);
        GroupStore.removeInvite(groupName, username);
        if (leaver != null) sendGroupListTo(leaver);       // người rời không còn thấy nhóm này

        if (set.isEmpty()) {
            groups.remove(groupName);
            GroupStore.deleteGroup(groupName);
            MessageStore.deleteGroupMessages(groupName);
            System.out.println("[Server] Group '" + groupName + "' dissolved (no members left)");
        } else {
            notifyGroupMembers(groupName, username + " đã rời nhóm '" + groupName + "'");
        }
    }

    private void notifyGroupMembers(String groupName, String systemText) {
        notifyGroupMembers(groupName, systemText, true);
    }

    private void notifyGroupMembers(String groupName, String systemText, boolean sendList) {
        Set<String> members = groups.get(groupName);
        if (members == null) return;
        Message sys = new Message(Message.Type.SYSTEM, "server", groupName, systemText);
        for (String member : members) {
            ClientHandler h = clients.get(member);
            if (h != null) {
                h.sendMessage(sys);
                if (sendList) sendGroupListTo(h);
            }
        }
    }

    public void sendGroupListTo(ClientHandler h) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Set<String>> e : groups.entrySet()) {
            if (e.getValue().contains(h.getUsername())) {
                if (sb.length() > 0) sb.append(";");
                sb.append(e.getKey()).append(":").append(String.join(",", e.getValue()));
            }
        }
        h.sendMessage(new Message(Message.Type.GROUP_LIST, "server", null, sb.toString()));
    }
    public void routePrivate(Message msg) {
        // Lưu vào MySQL trước khi chuyển đi (kể cả khi người nhận đang offline)
        boolean persisted = MessageStore.isPersistable(msg.getType());
        if (persisted) MessageStore.save(msg);
        if (msg.getType() == Message.Type.PRIVATE_DELETE) {
            MessageStore.markDeleted(msg.getContent(), msg.getSender());
        }

        ClientHandler target = clients.get(msg.getReceiver());
        if (target != null) {
            boolean delivered = target.sendMessage(msg);
            if (delivered && msg.getMessageId() != null && isDeliveryTrackable(msg.getType())) {
                ClientHandler senderHandler = clients.get(msg.getSender());
                if (senderHandler != null) {
                    senderHandler.sendMessage(new Message(Message.Type.MSG_DELIVERED,
                            msg.getReceiver(), msg.getSender(), msg.getMessageId()));
                }
            }
        } else if (msg.getType() == Message.Type.PRIVATE_DELETE) {
            // người nhận offline: DB đã cập nhật, họ sẽ không thấy tin này trong lịch sử nữa
        } else if (isCallSignal(msg.getType())) {
            replyCallTargetOffline(msg);
        } else {
            ClientHandler sender = clients.get(msg.getSender());
            if (sender != null) {
                String text = persisted
                        ? "User '" + msg.getReceiver() + "' is offline - message saved, they will see it when they log in"
                        : "User '" + msg.getReceiver() + "' is not online";
                sender.sendMessage(new Message(Message.Type.ERROR, "server", null, text));
            }
        }
    }

    private boolean isCallSignal(Message.Type t) {
        return t == Message.Type.VIDEO_CALL_REQUEST || t == Message.Type.VIDEO_CALL_ACCEPT
                || t == Message.Type.VIDEO_CALL_REJECT || t == Message.Type.VIDEO_CALL_END
                || t == Message.Type.VIDEO_FRAME || t == Message.Type.AUDIO_FRAME;
    }

    /** Đối phương không còn online: báo cho bên gọi 1 lần để kết thúc cuộc gọi (thay vì spam lỗi mỗi khung hình). */
    private void replyCallTargetOffline(Message msg) {
        Message.Type t = msg.getType();
        if (t == Message.Type.VIDEO_CALL_END || t == Message.Type.VIDEO_CALL_REJECT) return; // đằng nào cũng đang kết thúc
        ClientHandler sender = clients.get(msg.getSender());
        if (sender == null) return;
        Message.Type reply = (t == Message.Type.VIDEO_CALL_REQUEST)
                ? Message.Type.VIDEO_CALL_REJECT : Message.Type.VIDEO_CALL_END;
        sender.sendMessage(new Message(reply, msg.getReceiver(), msg.getSender(), "offline"));
    }

    private boolean isDeliveryTrackable(Message.Type type) {
        switch (type) {
            case PRIVATE_TEXT:
            case PRIVATE_EMOJI:
            case PRIVATE_STICKER:
            case PRIVATE_LOCATION:
            case PRIVATE_IMAGE:
            case PRIVATE_FILE:
                return true;
            default:
                return false;
        }
    }
    private void relayIfOnline(Message msg) {
        ClientHandler target = clients.get(msg.getReceiver());
        if (target != null) target.sendMessage(msg);
    }

    public void routeTyping(Message msg) {
        relayIfOnline(msg);
    }

    public void routeSeenReceipt(Message msg) {
        relayIfOnline(msg);
    }

    public void routeGroup(Message msg) {
        Set<String> members = groups.get(msg.getReceiver());
        if (members == null) {
            ClientHandler sender = clients.get(msg.getSender());
            if (sender != null) sender.sendMessage(new Message(Message.Type.ERROR, "server", null,
                    "Group '" + msg.getReceiver() + "' does not exist"));
            return;
        }
        if (!members.contains(msg.getSender())) {
            sendError(msg.getSender(), "Bạn không còn là thành viên của nhóm '" + msg.getReceiver() + "'");
            return;
        }
        if (MessageStore.isPersistable(msg.getType())) MessageStore.save(msg);
        if (msg.getType() == Message.Type.GROUP_DELETE) {
            MessageStore.markDeleted(msg.getContent(), msg.getSender());
        }
        for (String member : members) {
            if (member.equals(msg.getSender())) continue; // sender renders locally
            ClientHandler h = clients.get(member);
            if (h != null) h.sendMessage(msg);
        }
    }
}
