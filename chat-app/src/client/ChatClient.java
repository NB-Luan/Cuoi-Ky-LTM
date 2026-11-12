package client;

import common.Message;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class ChatClient {

    private final String host;
    private final int port;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private Consumer<Message> listener;
    private Thread readerThread;
    private volatile boolean running = false;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    public Message register(String username, String password) throws IOException, ClassNotFoundException {
        try (Socket s = new Socket(host, port)) {
            ObjectOutputStream o = new ObjectOutputStream(s.getOutputStream());
            o.flush();
            ObjectInputStream i = new ObjectInputStream(s.getInputStream());
            o.writeObject(new Message(Message.Type.REGISTER, username, null, password));
            o.flush();
            return (Message) i.readObject();
        }
    }

    public Message login(String username, String password) throws IOException, ClassNotFoundException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());

        this.username = username;
        out.writeObject(new Message(Message.Type.LOGIN, username, null, password));
        out.flush();

        Message reply = (Message) in.readObject();
        if (reply.getType() == Message.Type.LOGIN_SUCCESS) {
            running = true;
        }
        return reply;
    }

    public void setListener(Consumer<Message> listener) {
        this.listener = listener;
    }

    public void startListening() {
        readerThread = new Thread(() -> {
            try {
                while (running) {
                    Message msg = (Message) in.readObject();
                    if (listener != null) listener.accept(msg);
                }
            } catch (Exception e) {
                if (running && listener != null) {
                    listener.accept(new Message(Message.Type.ERROR, "server", null,
                            "Disconnected: " + e.getMessage()));
                }
            }
        }, "chat-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public synchronized void send(Message msg) {
        try {
            out.reset();
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            if (listener != null) {
                listener.accept(new Message(Message.Type.ERROR, "server", null,
                        "Send failed: " + e.getMessage()));
            }
        }
    }

    public String getUsername() { return username; }

    public void close() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
}
