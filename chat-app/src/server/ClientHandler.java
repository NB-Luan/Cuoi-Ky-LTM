package server;

import common.Message;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String username;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUsername() { return username; }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Pre-auth loop: accept REGISTER and LOGIN attempts (with retries) until
            // login succeeds. The connection is only promoted to a chat session once
            // username is set.
            while (username == null) {
                Message msg = (Message) in.readObject();
                switch (msg.getType()) {
                    case REGISTER:
                        handleRegister(msg);
                        break;
                    case LOGIN:
                        handleLogin(msg);
                        break;
                    default:
                        sendError("Please log in or register first");
                }
            }

            Message msg;
            while (running && (msg = (Message) in.readObject()) != null) {
                handle(msg);
            }
        } catch (EOFException | java.net.SocketException eof) {
            // client disconnected
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[Server] Connection error for " + username + ": " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleRegister(Message msg) throws IOException {
        String requested = msg.getSender() != null ? msg.getSender().trim() : "";
        String password = msg.getContent();
        if (requested.length() < 3) {
            sendMessage(new Message(Message.Type.REGISTER_FAILED, "server", null,
                    "Username must be at least 3 characters"));
            return;
        }
        if (password == null || password.length() < 4) {
            sendMessage(new Message(Message.Type.REGISTER_FAILED, "server", null,
                    "Password must be at least 4 characters"));
            return;
        }
        if (server.registerAccount(requested, password)) {
            sendMessage(new Message(Message.Type.REGISTER_SUCCESS, "server", requested,
                    "Account '" + requested + "' created. You can now log in."));
            server.broadcastUserList(); // let already-online clients see the new (offline) account
            System.out.println("[Server] New account registered: " + requested);
        } else {
            sendMessage(new Message(Message.Type.REGISTER_FAILED, "server", null,
                    "Username '" + requested + "' is already taken"));
        }
    }

    private void handleLogin(Message msg) throws IOException {
        String requested = msg.getSender() != null ? msg.getSender().trim() : "";
        String password = msg.getContent();

        if (requested.isEmpty()) {
            sendMessage(new Message(Message.Type.LOGIN_FAILED, "server", null, "Username is required"));
            return;
        }
        if (!server.userExists(requested)) {
            sendMessage(new Message(Message.Type.LOGIN_FAILED, "server", null,
                    "No account found for '" + requested + "'. Please register first."));
            return;
        }
        if (!server.authenticate(requested, password)) {
            sendMessage(new Message(Message.Type.LOGIN_FAILED, "server", null, "Incorrect password"));
            return;
        }
        if (server.isOnline(requested)) {
            sendMessage(new Message(Message.Type.LOGIN_FAILED, "server", null,
                    "This account is already logged in elsewhere"));
            return;
        }

        this.username = requested;
        server.registerClient(username, this);
        sendMessage(new Message(Message.Type.LOGIN_SUCCESS, "server", username, "Welcome " + username));
        server.broadcastUserList();
        server.sendGroupListTo(this);
        server.sendHistoryTo(this);   // gửi lịch sử chat lưu trong MySQL
        server.sendPendingInvitesTo(this);   // lời mời vào nhóm nhận được lúc offline
        System.out.println("[Server] " + username + " logged in from " + socket.getRemoteSocketAddress());
    }

    private void handle(Message msg) {
        msg.setSender(username);   // không tin sender do client tự khai, tránh giả mạo người khác
        switch (msg.getType()) {
            case CREATE_GROUP:
                server.createGroup(msg.getReceiver(), username, parseMembers(msg.getContent()));
                break;
            case JOIN_GROUP:   // chỉ vào được nhóm khi đã được mời
                server.acceptGroupInvite(msg.getReceiver(), username);
                break;
            case GROUP_INVITE_REQUEST:
                server.inviteMembers(msg.getReceiver(), username, parseMembers(msg.getContent()));
                break;
            case LEAVE_GROUP:
                server.leaveGroup(msg.getReceiver(), username);
                break;
            case GROUP_INVITE_ACCEPT:
                server.acceptGroupInvite(msg.getReceiver(), username);
                break;
            case GROUP_INVITE_DECLINE:
                server.declineGroupInvite(msg.getReceiver(), username);
                break;

            case PRIVATE_TYPING:
                server.routeTyping(msg);
                break;
            case GROUP_TYPING:
                server.routeGroup(msg);
                break;
            case MSG_SEEN:
                server.routeSeenReceipt(msg);
                break;

            case PRIVATE_TEXT:
            case PRIVATE_EMOJI:
            case PRIVATE_STICKER:
            case PRIVATE_LOCATION:
            case PRIVATE_IMAGE:
            case PRIVATE_FILE:
                server.routePrivate(msg);
                break;

            case GROUP_TEXT:
            case GROUP_EMOJI:
            case GROUP_STICKER:
            case GROUP_LOCATION:
            case GROUP_IMAGE:
            case GROUP_FILE:
                server.routeGroup(msg);
                break;

            case PRIVATE_DELETE:
                server.routePrivate(msg);
                break;
            case GROUP_DELETE:
                server.routeGroup(msg);
                break;

            case VIDEO_CALL_REQUEST:
            case VIDEO_CALL_ACCEPT:
            case VIDEO_CALL_REJECT:
            case VIDEO_CALL_END:
            case VIDEO_FRAME:
            case AUDIO_FRAME:
                server.routePrivate(msg); // 1-1 relay by receiver username
                break;

            case LOGOUT:
                running = false;
                break;

            default:
                System.out.println("[Server] Unhandled message: " + msg);
        }
    }

    private List<String> parseMembers(String csv) {
        List<String> list = new ArrayList<>();
        if (csv != null) {
            for (String s : csv.split(",")) {
                s = s.trim();
                if (!s.isEmpty()) list.add(s);
            }
        }
        return list;
    }

    public synchronized boolean sendMessage(Message msg) {
        try {
            out.reset(); // avoid stale object graph caching across sends
            out.writeObject(msg);
            out.flush();
            return true;
        } catch (IOException e) {
            System.out.println("[Server] Failed to send to " + username + ": " + e.getMessage());
            return false;
        }
    }

    private void sendError(String text) {
        sendMessage(new Message(Message.Type.ERROR, "server", null, text));
    }

    private void disconnect() {
        running = false;
        if (username != null) {
            server.removeClient(username);
            server.broadcastUserList();
            System.out.println("[Server] " + username + " disconnected");
        }
        try { socket.close(); } catch (IOException ignored) {}
    }
}
