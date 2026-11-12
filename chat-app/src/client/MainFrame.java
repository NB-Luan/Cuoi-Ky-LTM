package client;

import common.Message;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class MainFrame extends JFrame {

    private static final String EMPTY_CARD = "__empty__";

    private final ChatClient client;

    private final DefaultListModel<UserEntry> userModel = new DefaultListModel<UserEntry>();
    private final DefaultListModel<String> groupModel = new DefaultListModel<String>();
    private final JList<UserEntry> userList = new JList<UserEntry>(userModel);
    private final JList<String> groupList = new JList<String>(groupModel);

    private final JPanel chatCards = new JPanel(new CardLayout());
    private final Map<String, ChatPanel> panes = new HashMap<String, ChatPanel>();
    private final Map<String, Set<String>> groupMembers = new HashMap<String, Set<String>>();
    private final Map<String, Boolean> onlineState = new HashMap<String, Boolean>();
    private final Map<String, VideoCallFrame> activeCalls = new HashMap<String, VideoCallFrame>();
    /** Hộp thoại "Cuộc gọi đến" đang hiển thị (không modal, để còn đóng được khi người gọi cúp máy). */
    private JDialog incomingDialog;
    private String incomingCaller;

    private final Map<String, JLabel> statusLabels = new HashMap<String, JLabel>();
    private final Map<String, javax.swing.Timer> typingTimers = new HashMap<String, javax.swing.Timer>();
    private final Map<String, String> lastKnownStatusText = new HashMap<String, String>();
    private final Map<String, String> lastSentMessageId = new HashMap<String, String>();
    private final Map<String, String> lastReceivedMessageId = new HashMap<String, String>();
    private final Map<String, String> lastSeenSentToId = new HashMap<String, String>();
    private long lastTypingSentAt = 0L;

    private final JLabel headerAvatar = new JLabel();
    private final JLabel titleLabel = UI.label("Chưa chọn cuộc trò chuyện", Font.BOLD, 15f, UI.TEXT);
    private final JLabel subtitleLabel = UI.label("Chọn một người dùng hoặc nhóm ở cột bên trái",
            Font.PLAIN, 11.5f, UI.MUTED);
    private final UI.Btn videoBtn = UI.softButton("Gọi video", UI.glyph("video", 16, UI.PRIMARY_DARK));
    private final UI.Btn inviteBtn = UI.softButton("Mời thêm", UI.glyph("plus", 15, UI.PRIMARY_DARK));
    private final UI.Btn leaveBtn = new UI.Btn("Rời nhóm", UI.glyph("logout", 15, UI.DANGER),
            null, new Color(0xFDE8EC), UI.DANGER, 12);

    private final UI.HintField input = new UI.HintField("Nhập tin nhắn...", "");
    private final JLabel statusBar = UI.label(" ", Font.PLAIN, 11.5f, UI.MUTED);

    private String currentTarget = null;
    private boolean currentIsGroup = false;

    private static final Path DOWNLOAD_DIR = Paths.get(System.getProperty("user.home"), "chat-downloads");
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    public MainFrame(ChatClient client) {
        super("Java TCP Chat - " + client.getUsername());
        this.client = client;
        client.setListener(msg -> SwingUtilities.invokeLater(() -> handleIncoming(msg)));

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UI.APP_BG);
        buildUI();
        setSize(1080, 700);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (currentTarget != null && !currentIsGroup) {
                    maybeSendSeenReceipt(currentTarget, keyFor(currentTarget, false));
                }
            }
        });

        try {
            Files.createDirectories(DOWNLOAD_DIR);
        } catch (IOException ignored) {
        }
    }


    private void buildUI() {
        add(buildSidebar(), BorderLayout.WEST);
        add(buildChatArea(), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(UI.CARD_BG);
        bottom.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, UI.BORDER),
                BorderFactory.createEmptyBorder(5, 16, 5, 16)));
        bottom.add(statusBar, BorderLayout.WEST);
        JLabel hintLabel = UI.label("Chuột phải vào tin nhắn của bạn để thu hồi", Font.PLAIN, 11.5f, UI.MUTED);
        bottom.add(hintLabel, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        updateHeader();
    }


    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(UI.CARD_BG);
        sidebar.setPreferredSize(new Dimension(286, 0));
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, UI.BORDER));

        sidebar.add(buildProfileHeader(), BorderLayout.NORTH);

        userList.setCellRenderer(new RowRenderer(false));
        userList.setFixedCellHeight(56);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBackground(UI.CARD_BG);
        userList.setBorder(BorderFactory.createEmptyBorder(2, 6, 6, 6));
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && userList.getSelectedValue() != null) {
                groupList.clearSelection();
                selectConversation(userList.getSelectedValue().username, false);
            }
        });

        groupList.setCellRenderer(new RowRenderer(true));
        groupList.setFixedCellHeight(56);
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupList.setBackground(UI.CARD_BG);
        groupList.setBorder(BorderFactory.createEmptyBorder(2, 6, 6, 6));
        groupList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && groupList.getSelectedValue() != null) {
                userList.clearSelection();
                selectConversation(groupList.getSelectedValue(), true);
            }
        });

        JPanel lists = new JPanel(new GridLayout(2, 1, 0, 0));
        lists.setBackground(UI.CARD_BG);
        lists.add(listSection("Người dùng", userList, null));

        UI.Btn newGroupBtn = UI.softButton("Tạo nhóm mới", UI.glyph("plus", 14, UI.PRIMARY_DARK));
        newGroupBtn.setFont(UI.font(Font.BOLD, 12.5f));
        newGroupBtn.addActionListener(e -> createGroupDialog());
        lists.add(listSection("Nhóm của tôi", groupList, newGroupBtn));

        sidebar.add(lists, BorderLayout.CENTER);
        return sidebar;
    }

    private JComponent listSection(String title, JList<?> list, JComponent footer) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UI.CARD_BG);
        panel.setBorder(new MatteBorder(0, 0, 1, 0, UI.BORDER));
        panel.add(UI.sectionTitle(title), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(list);
        UI.styleScroll(scroll);
        scroll.getViewport().setBackground(UI.CARD_BG);
        panel.add(scroll, BorderLayout.CENTER);

        if (footer != null) {
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setBackground(UI.CARD_BG);
            wrap.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));
            wrap.add(footer, BorderLayout.CENTER);
            panel.add(wrap, BorderLayout.SOUTH);
        }
        return panel;
    }

    private JComponent buildProfileHeader() {
        UI.Card card = new UI.Card(0, UI.PRIMARY, null);
        card.setGradientTo(UI.ACCENT);
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(BorderFactory.createEmptyBorder(18, 16, 18, 16));

        card.add(new JLabel(UI.avatarCached(client.getUsername(), 44, true, true)), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = UI.label(client.getUsername(), Font.BOLD, 14.5f, Color.WHITE);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel state = UI.label("Đang trực tuyến", Font.PLAIN, 11.5f, new Color(255, 255, 255, 205));
        state.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(name);
        text.add(Box.createVerticalStrut(3));
        text.add(state);
        card.add(text, BorderLayout.CENTER);

        UI.Btn logout = UI.iconButton(UI.glyph("logout", 17, Color.WHITE), "Đăng xuất", 34,
                new Color(255, 255, 255, 40), new Color(255, 255, 255, 80));
        logout.addActionListener(e -> doLogout());
        card.add(logout, BorderLayout.EAST);
        return card;
    }

    private void doLogout() {
        int ok = JOptionPane.showConfirmDialog(this, "Đăng xuất khỏi tài khoản này?",
                "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            client.close();
        } catch (Exception ignored) {
        }
        dispose();
        UI.install();
        new LoginFrame().setVisible(true);
    }


    private JComponent buildChatArea() {
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(UI.CHAT_BG);

        center.add(buildChatHeader(), BorderLayout.NORTH);

        chatCards.setBackground(UI.CHAT_BG);
        chatCards.add(buildEmptyCard(), EMPTY_CARD);
        ((CardLayout) chatCards.getLayout()).show(chatCards, EMPTY_CARD);
        center.add(chatCards, BorderLayout.CENTER);

        center.add(buildComposer(), BorderLayout.SOUTH);
        return center;
    }

    private JComponent buildChatHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(UI.CARD_BG);
        header.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, UI.BORDER),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));

        headerAvatar.setIcon(UI.glyph("group", 34, UI.OFFLINE));
        header.add(headerAvatar, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(titleLabel);
        text.add(Box.createVerticalStrut(3));
        text.add(subtitleLabel);
        header.add(text, BorderLayout.CENTER);

        videoBtn.addActionListener(e -> startVideoCall());
        inviteBtn.addActionListener(e -> inviteToCurrentGroup());
        leaveBtn.addActionListener(e -> leaveCurrentGroup());
        inviteBtn.setVisible(false);
        leaveBtn.setVisible(false);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(videoBtn);
        right.add(inviteBtn);
        right.add(leaveBtn);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent buildEmptyCard() {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(UI.CHAT_BG);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel(UI.glyph("smile", 64, new Color(0xC8CDDC)));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(icon);
        box.add(Box.createVerticalStrut(16));

        JLabel t = UI.label("Bắt đầu trò chuyện", Font.BOLD, 16f, UI.TEXT);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(t);
        box.add(Box.createVerticalStrut(6));

        JLabel s = UI.label("Chọn một người dùng hoặc một nhóm ở cột bên trái", Font.PLAIN, 13f, UI.MUTED);
        s.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(s);

        empty.add(box);
        return empty;
    }

    private JComponent buildComposer() {
        JPanel composer = new JPanel(new BorderLayout(10, 0));
        composer.setBackground(UI.CARD_BG);
        composer.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, UI.BORDER),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        Color iconColor = new Color(0x6B7185);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tools.setOpaque(false);

        final UI.Btn emojiBtn = toolButton("smile", "Emoji", iconColor);
        emojiBtn.addActionListener(e -> EmojiPicker.show(emojiBtn, emoji -> {
            input.setText(input.getText() + emoji);
            input.requestFocusInWindow();
        }));
        final UI.Btn stickerBtn = toolButton("sticker", "Sticker", iconColor);
        stickerBtn.addActionListener(e -> StickerPicker.show(stickerBtn, this::sendSticker));
        UI.Btn imageBtn = toolButton("image", "Gửi ảnh", iconColor);
        imageBtn.addActionListener(e -> sendFileOrImage(true));
        UI.Btn fileBtn = toolButton("file", "Gửi tệp", iconColor);
        fileBtn.addActionListener(e -> sendFileOrImage(false));
        UI.Btn locationBtn = toolButton("pin", "Gửi vị trí", iconColor);
        locationBtn.addActionListener(e -> sendLocationDialog());

        tools.add(emojiBtn);
        tools.add(stickerBtn);
        tools.add(imageBtn);
        tools.add(fileBtn);
        tools.add(locationBtn);
        composer.add(tools, BorderLayout.WEST);

        input.addActionListener(e -> sendText());
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onTyping(); }
            @Override public void removeUpdate(DocumentEvent e) { onTyping(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        UI.Card inputCard = UI.fieldWrap(input);
        inputCard.setFill(new Color(0xF4F6FB));
        composer.add(inputCard, BorderLayout.CENTER);

        UI.Btn sendBtn = UI.iconButton(UI.glyph("send", 18, Color.WHITE), "Gửi (Enter)", 42,
                UI.PRIMARY, UI.PRIMARY_DARK);
        sendBtn.addActionListener(e -> sendText());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(sendBtn);
        composer.add(right, BorderLayout.EAST);
        return composer;
    }

    private UI.Btn toolButton(String glyph, String tooltip, Color color) {
        return UI.iconButton(UI.glyph(glyph, 19, color), tooltip, 38, null, UI.HOVER);
    }


    private String keyFor(String target, boolean isGroup) {
        return (isGroup ? "group:" : "user:") + target;
    }

    private ChatPanel paneFor(String target, boolean isGroup) {
        final String key = keyFor(target, isGroup);
        ChatPanel pane = panes.get(key);
        if (pane != null) return pane;

        pane = new ChatPanel();
        pane.setDeleteHandler(new ChatPanel.DeleteHandler() {
            @Override
            public void onDelete(String messageId) {
                requestDeleteMessage(key, messageId);
            }
        });

        JLabel statusLabel = UI.label(" ", Font.ITALIC, 11.5f, UI.MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 20, 6, 20));
        statusLabels.put(key, statusLabel);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UI.CHAT_BG);
        wrapper.add(pane, BorderLayout.CENTER);
        wrapper.add(statusLabel, BorderLayout.SOUTH);

        chatCards.add(wrapper, key);
        panes.put(key, pane);
        return pane;
    }

    private void selectConversation(String target, boolean isGroup) {
        currentTarget = target;
        currentIsGroup = isGroup;
        paneFor(target, isGroup);
        ((CardLayout) chatCards.getLayout()).show(chatCards, keyFor(target, isGroup));
        updateHeader();
        input.requestFocusInWindow();
        if (!isGroup) maybeSendSeenReceipt(target, keyFor(target, false));
    }

    private void updateHeader() {
        if (currentTarget == null) {
            headerAvatar.setIcon(UI.glyph("group", 34, UI.OFFLINE));
            titleLabel.setText("Chưa chọn cuộc trò chuyện");
            subtitleLabel.setText("Chọn một người dùng hoặc nhóm ở cột bên trái");
            subtitleLabel.setForeground(UI.MUTED);
            subtitleLabel.setToolTipText(null);
            videoBtn.setVisible(true);
            videoBtn.setEnabled(false);
            inviteBtn.setVisible(false);
            leaveBtn.setVisible(false);
            return;
        }
        headerAvatar.setIcon(UI.avatarCached(currentTarget, 40, false, false));
        titleLabel.setText(currentTarget);
        if (currentIsGroup) {
            Set<String> members = groupMembers.get(currentTarget);
            int n = members == null ? 0 : members.size();
            subtitleLabel.setText("Nhóm \u00b7 " + n + " thành viên");
            subtitleLabel.setForeground(UI.MUTED);
            subtitleLabel.setToolTipText(members == null ? null : String.join(", ", members));
            videoBtn.setVisible(false);
            inviteBtn.setVisible(true);
            leaveBtn.setVisible(true);
        } else {
            boolean online = Boolean.TRUE.equals(onlineState.get(currentTarget));
            subtitleLabel.setText(online ? "Đang hoạt động" : "Ngoại tuyến");
            subtitleLabel.setForeground(online ? UI.ONLINE : UI.MUTED);
            subtitleLabel.setToolTipText(null);
            videoBtn.setVisible(true);
            videoBtn.setEnabled(true);
            inviteBtn.setVisible(false);
            leaveBtn.setVisible(false);
        }
    }


    private void appendText(String target, boolean isGroup, String sender, String text) {
        appendText(target, isGroup, sender, text, null);
    }

    private void appendText(String target, boolean isGroup, String sender, String text, String messageId) {
        boolean mine = "Me".equals(sender);
        String shown = mine ? client.getUsername() : sender;
        paneFor(target, isGroup).addText(shown, mine, text, messageId, isGroup && !mine);
    }

    private void appendSystem(String target, boolean isGroup, String text) {
        paneFor(target, isGroup).addSystem(text);
    }

    private void appendImage(String target, boolean isGroup, String sender, ImageIcon icon, String caption) {
        appendImage(target, isGroup, sender, icon, caption, null);
    }

    private void appendImage(String target, boolean isGroup, String sender, ImageIcon icon,
                             String caption, String messageId) {
        boolean mine = "Me".equals(sender);
        String shown = mine ? client.getUsername() : sender;
        paneFor(target, isGroup).addImage(shown, mine, icon, caption, messageId, isGroup && !mine);
    }

    private void appendFile(String target, boolean isGroup, String sender, String fileName,
                            byte[] data, String messageId) {
        boolean mine = "Me".equals(sender);
        String shown = mine ? client.getUsername() : sender;
        paneFor(target, isGroup).addFile(shown, mine, fileName, data, messageId, isGroup && !mine,
                new java.util.function.Consumer<String>() {
                    @Override
                    public void accept(String text) {
                        statusBar.setForeground(UI.MUTED);
                        statusBar.setText(text);
                    }
                });
    }

    private void requestDeleteMessage(String key, String messageId) {
        boolean isGroup = key.startsWith("group:");
        String target = key.substring(key.indexOf(':') + 1);
        Message.Type type = isGroup ? Message.Type.GROUP_DELETE : Message.Type.PRIVATE_DELETE;
        client.send(new Message(type, client.getUsername(), target, messageId));
        deleteMessageLocally(key, messageId);
    }

    private void deleteMessageLocally(String key, String messageId) {
        ChatPanel pane = panes.get(key);
        if (pane != null) pane.deleteMessage(messageId);
    }

    private ImageIcon scaledIcon(byte[] data, int maxDim) {
        ImageIcon raw = new ImageIcon(data);
        int w = raw.getIconWidth(), h = raw.getIconHeight();
        if (w <= 0 || h <= 0) return raw;
        double scale = Math.min(1.0, maxDim / (double) Math.max(w, h));
        Image scaledImg = raw.getImage().getScaledInstance((int) (w * scale), (int) (h * scale), Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImg);
    }


    private String newMessageId() {
        return UUID.randomUUID().toString();
    }

    private void onTyping() {
        if (currentTarget == null) return;
        long now = System.currentTimeMillis();
        if (now - lastTypingSentAt < 1500) return;
        lastTypingSentAt = now;
        Message.Type type = currentIsGroup ? Message.Type.GROUP_TYPING : Message.Type.PRIVATE_TYPING;
        client.send(new Message(type, client.getUsername(), currentTarget, null));
    }

    private void showTypingIndicator(String target, boolean isGroup, String typer) {
        final String key = keyFor(target, isGroup);
        JLabel label = statusLabels.get(key);
        if (label == null) return;

        label.setForeground(UI.PRIMARY);
        label.setText(typer + " đang soạn tin...");

        javax.swing.Timer existing = typingTimers.get(key);
        if (existing != null) existing.stop();
        javax.swing.Timer t = new javax.swing.Timer(3000, e -> clearTypingIndicator(key));
        t.setRepeats(false);
        t.start();
        typingTimers.put(key, t);
    }

    private void clearTypingIndicator(String key) {
        javax.swing.Timer t = typingTimers.remove(key);
        if (t != null) t.stop();
        JLabel label = statusLabels.get(key);
        if (label == null) return;
        label.setForeground(UI.MUTED);
        label.setText(lastKnownStatusText.containsKey(key) ? lastKnownStatusText.get(key) : " ");
    }

    private void setStatusText(String key, String text) {
        lastKnownStatusText.put(key, text);
        javax.swing.Timer typingTimer = typingTimers.get(key);
        boolean showingTyping = typingTimer != null && typingTimer.isRunning();
        if (!showingTyping) {
            JLabel label = statusLabels.get(key);
            if (label != null) {
                label.setForeground(UI.MUTED);
                label.setText(text);
            }
        }
    }

    private void markSent(String target, boolean isGroup, String messageId) {
        if (isGroup) return;
        String key = keyFor(target, false);
        lastSentMessageId.put(key, messageId);
        setStatusText(key, "Đã gửi \u2713");
    }

    private void recordIncomingTrackable(String partner, boolean isGroup, String messageId) {
        if (isGroup || messageId == null) return;
        String key = keyFor(partner, false);
        lastReceivedMessageId.put(key, messageId);
        maybeSendSeenReceipt(partner, key);
    }

    private void maybeSendSeenReceipt(String partner, String key) {
        if (currentTarget == null || currentIsGroup || !currentTarget.equals(partner)) return;
        if (!isActive()) return;
        String id = lastReceivedMessageId.get(key);
        if (id == null || id.equals(lastSeenSentToId.get(key))) return;
        lastSeenSentToId.put(key, id);
        client.send(new Message(Message.Type.MSG_SEEN, client.getUsername(), partner, id));
    }

    private void updateDeliveryStatus(String partner, String messageId, boolean seen) {
        String key = keyFor(partner, false);
        String lastId = lastSentMessageId.get(key);
        if (lastId == null || !lastId.equals(messageId)) return;
        String text = seen ? ("Đã xem \u2713\u2713 \u00b7 " + timeFmt.format(new Date())) : "Đã nhận \u2713\u2713";
        setStatusText(key, text);
    }

    private boolean requireTarget() {
        if (currentTarget == null) {
            statusBar.setForeground(UI.DANGER);
            statusBar.setText("Hãy chọn một cuộc trò chuyện trước.");
            return false;
        }
        return true;
    }

    private void sendText() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        if (!requireTarget()) return;
        Message.Type type = currentIsGroup ? Message.Type.GROUP_TEXT : Message.Type.PRIVATE_TEXT;
        Message msg = new Message(type, client.getUsername(), currentTarget, text);
        String id = newMessageId();
        msg.setMessageId(id);
        client.send(msg);
        appendText(currentTarget, currentIsGroup, "Me", text, id);
        markSent(currentTarget, currentIsGroup, id);
        input.setText("");
    }

    private void sendSticker(String stickerId, byte[] png) {
        if (!requireTarget()) return;
        Message.Type type = currentIsGroup ? Message.Type.GROUP_STICKER : Message.Type.PRIVATE_STICKER;
        Message msg = new Message(type, client.getUsername(), currentTarget, null);
        msg.setFileName(stickerId);
        msg.setFileData(png);
        String id = newMessageId();
        msg.setMessageId(id);
        client.send(msg);
        appendImage(currentTarget, currentIsGroup, "Me", scaledIcon(png, 84), null, id);
        markSent(currentTarget, currentIsGroup, id);
    }

    private void sendLocationDialog() {
        if (!requireTarget()) return;
        UI.HintField lat = new UI.HintField("16.0544", "");
        UI.HintField lng = new UI.HintField("108.2022", "");
        JPanel p = formPanel(new String[]{"Vĩ độ (latitude)", "Kinh độ (longitude)"},
                new JComponent[]{lat, lng});
        int r = JOptionPane.showConfirmDialog(this, p, "Gửi vị trí", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        String content = lat.getText().trim() + "," + lng.getText().trim();
        Message.Type type = currentIsGroup ? Message.Type.GROUP_LOCATION : Message.Type.PRIVATE_LOCATION;
        Message msg = new Message(type, client.getUsername(), currentTarget, content);
        String id = newMessageId();
        msg.setMessageId(id);
        client.send(msg);
        appendText(currentTarget, currentIsGroup, "Me", locationText(content), id);
        markSent(currentTarget, currentIsGroup, id);
    }

    private String locationText(String latLng) {
        return "Vị trí: " + latLng + "\nhttps://maps.google.com/?q=" + latLng;
    }

    private void sendFileOrImage(boolean imageOnly) {
        if (!requireTarget()) return;
        JFileChooser chooser = new JFileChooser();
        if (imageOnly) {
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Ảnh", "png", "jpg", "jpeg", "gif", "bmp"));
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            Message.Type type;
            if (imageOnly) {
                type = currentIsGroup ? Message.Type.GROUP_IMAGE : Message.Type.PRIVATE_IMAGE;
            } else {
                type = currentIsGroup ? Message.Type.GROUP_FILE : Message.Type.PRIVATE_FILE;
            }
            Message msg = new Message(type, client.getUsername(), currentTarget, null);
            msg.setFileName(file.getName());
            msg.setFileData(data);
            String id = newMessageId();
            msg.setMessageId(id);
            client.send(msg);

            if (imageOnly) {
                appendImage(currentTarget, currentIsGroup, "Me", scaledIcon(data, 240), file.getName(), id);
            } else {
                appendFile(currentTarget, currentIsGroup, "Me", file.getName(), data, id);
            }
            markSent(currentTarget, currentIsGroup, id);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Không đọc được tệp: " + ex.getMessage());
        }
    }

    /** Thành viên nhóm mời thêm người khác (người được mời phải đồng ý mới vào nhóm). */
    private void inviteToCurrentGroup() {
        if (currentTarget == null || !currentIsGroup) return;
        final String group = currentTarget;
        Set<String> members = groupMembers.get(group);

        DefaultListModel<String> candidates = new DefaultListModel<String>();
        for (int i = 0; i < userModel.size(); i++) {
            String u = userModel.get(i).username;
            if (members == null || !members.contains(u)) candidates.addElement(u);
        }
        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tất cả người dùng đều đã ở trong nhóm này.");
            return;
        }

        JList<String> pick = new JList<String>(candidates);
        pick.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        pick.setVisibleRowCount(Math.min(8, candidates.size()));
        JScrollPane sp = new JScrollPane(pick);
        sp.setPreferredSize(new Dimension(280, 170));

        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(UI.label("Chọn người để mời vào nhóm '" + group + "'", Font.BOLD, 12.5f, UI.TEXT),
                BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        p.add(UI.label("Giữ Ctrl / Shift để chọn nhiều người", Font.PLAIN, 11.5f, UI.MUTED),
                BorderLayout.SOUTH);

        int r = JOptionPane.showConfirmDialog(this, p, "Mời thêm thành viên",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        List<String> chosen = pick.getSelectedValuesList();
        if (chosen.isEmpty()) return;
        client.send(new Message(Message.Type.GROUP_INVITE_REQUEST, client.getUsername(), group,
                String.join(",", chosen)));
        statusBar.setForeground(UI.MUTED);
        statusBar.setText("Đã gửi lời mời tới: " + String.join(", ", chosen));
    }

    private void leaveCurrentGroup() {
        if (currentTarget == null || !currentIsGroup) return;
        final String group = currentTarget;
        Set<String> members = groupMembers.get(group);
        String warning = "Rời khỏi nhóm '" + group + "'?\nBạn sẽ không nhận được tin nhắn của nhóm này nữa.";
        if (members != null && members.size() <= 1) {
            warning += "\n\nBạn là thành viên cuối cùng: nhóm và toàn bộ lịch sử của nhóm sẽ bị xoá.";
        }
        int ok = JOptionPane.showConfirmDialog(this, warning, "Rời nhóm",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        client.send(new Message(Message.Type.LEAVE_GROUP, client.getUsername(), group, null));
        // Giao diện tự cập nhật khi server gửi lại danh sách nhóm mới
    }

    private void createGroupDialog() {
        UI.HintField nameField = new UI.HintField("Ví dụ: Nhóm đồ án", "");
        UI.HintField membersField = new UI.HintField("an, binh, chi", "");
        JPanel p = formPanel(new String[]{"Tên nhóm", "Thành viên (cách nhau bởi dấu phẩy)"},
                new JComponent[]{nameField, membersField});
        int r = JOptionPane.showConfirmDialog(this, p, "Tạo nhóm mới", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        Message msg = new Message(Message.Type.CREATE_GROUP, client.getUsername(), name,
                membersField.getText().trim());
        client.send(msg);
    }

    private JPanel formPanel(String[] captions, JComponent[] fields) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(UI.CARD_BG);
        p.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));
        for (int i = 0; i < captions.length; i++) {
            JLabel l = UI.label(captions[i], Font.BOLD, 11.5f, UI.MUTED);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(l);
            p.add(Box.createVerticalStrut(5));
            UI.Card wrap = UI.fieldWrap(fields[i]);
            wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrap.setPreferredSize(new Dimension(280, 38));
            wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            p.add(wrap);
            if (i < captions.length - 1) p.add(Box.createVerticalStrut(12));
        }
        return p;
    }

    private void startVideoCall() {
        if (currentTarget == null || currentIsGroup) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một người dùng (không phải nhóm) để gọi video.");
            return;
        }
        if (!activeCalls.isEmpty() || incomingDialog != null) {
            JOptionPane.showMessageDialog(this, "Bạn đang trong một cuộc gọi khác.");
            return;
        }
        final String peer = currentTarget;
        VideoCallFrame frame = new VideoCallFrame(client, peer, true);
        frame.setOnClosed(new Runnable() {
            @Override
            public void run() {
                activeCalls.remove(peer);
            }
        });
        activeCalls.put(peer, frame);
        frame.setVisible(true);
        client.send(new Message(Message.Type.VIDEO_CALL_REQUEST, client.getUsername(), peer, null));
    }

    /** Hiển thị một tin nhắn cũ lấy từ database (do server gửi lại khi đăng nhập). */
    private void showHistory(Message m) {
        if (m == null || m.getType() == null) return;
        boolean group = m.isGroupType();
        boolean mine = client.getUsername().equals(m.getSender());
        String target = group ? m.getReceiver() : (mine ? m.getReceiver() : m.getSender());
        String who = mine ? "Me" : m.getSender();
        String typeName = m.getType().name();
        byte[] data = m.getFileData();

        if (data != null && typeName.endsWith("_STICKER")) {
            appendImage(target, group, who, scaledIcon(data, 84), null, m.getMessageId());
            return;
        }
        if (data != null && typeName.endsWith("_IMAGE")) {
            appendImage(target, group, who, scaledIcon(data, 240), m.getFileName(), m.getMessageId());
            if (!mine) saveIncomingFile(m, false);
            return;
        }
        if (data != null && typeName.endsWith("_FILE")) {
            appendFile(target, group, who, m.getFileName(), data, m.getMessageId());
            if (!mine) saveIncomingFile(m, false);
            return;
        }
        String text = m.getContent();
        if (typeName.endsWith("_LOCATION")) text = locationText(text);
        appendText(target, group, who, text, m.getMessageId());
    }

    private void handleIncoming(Message msg) {
        switch (msg.getType()) {
            case USER_LIST:
                refreshUserList(msg.getContent());
                break;
            case GROUP_LIST:
                refreshGroupList(msg.getContent());
                break;
            case GROUP_INVITE:
                handleGroupInvite(msg.getSender(), msg.getReceiver());
                break;
            case SYSTEM:
                if (msg.getReceiver() != null) appendSystem(msg.getReceiver(), true, msg.getContent());
                statusBar.setForeground(UI.MUTED);
                statusBar.setText(msg.getContent());
                break;
            case ERROR:
                statusBar.setForeground(UI.DANGER);
                statusBar.setText(msg.getContent());
                if (msg.getContent() != null && msg.getContent().startsWith("Disconnected")) {
                    dismissIncomingDialog();
                    for (VideoCallFrame f : new ArrayList<VideoCallFrame>(activeCalls.values())) {
                        f.onRemoteEnded("Mất kết nối tới server");
                    }
                    activeCalls.clear();
                }
                break;

            case HISTORY:
                showHistory(msg.getInner());
                break;

            case PRIVATE_TYPING:
                showTypingIndicator(msg.getSender(), false, msg.getSender());
                break;
            case GROUP_TYPING:
                showTypingIndicator(msg.getReceiver(), true, msg.getSender());
                break;
            case MSG_DELIVERED:
                updateDeliveryStatus(msg.getSender(), msg.getContent(), false);
                break;
            case MSG_SEEN:
                updateDeliveryStatus(msg.getSender(), msg.getContent(), true);
                break;

            case PRIVATE_TEXT:
            case PRIVATE_EMOJI:
                clearTypingIndicator(keyFor(msg.getSender(), false));
                appendText(msg.getSender(), false, msg.getSender(), msg.getContent(), msg.getMessageId());
                recordIncomingTrackable(msg.getSender(), false, msg.getMessageId());
                break;
            case PRIVATE_LOCATION:
                clearTypingIndicator(keyFor(msg.getSender(), false));
                appendText(msg.getSender(), false, msg.getSender(),
                        locationText(msg.getContent()), msg.getMessageId());
                recordIncomingTrackable(msg.getSender(), false, msg.getMessageId());
                break;
            case PRIVATE_STICKER:
                clearTypingIndicator(keyFor(msg.getSender(), false));
                appendImage(msg.getSender(), false, msg.getSender(),
                        scaledIcon(msg.getFileData(), 84), null, msg.getMessageId());
                recordIncomingTrackable(msg.getSender(), false, msg.getMessageId());
                break;
            case PRIVATE_IMAGE:
                clearTypingIndicator(keyFor(msg.getSender(), false));
                appendImage(msg.getSender(), false, msg.getSender(),
                        scaledIcon(msg.getFileData(), 240), msg.getFileName(), msg.getMessageId());
                saveIncomingFile(msg);
                recordIncomingTrackable(msg.getSender(), false, msg.getMessageId());
                break;
            case PRIVATE_FILE:
                clearTypingIndicator(keyFor(msg.getSender(), false));
                appendFile(msg.getSender(), false, msg.getSender(),
                        msg.getFileName(), msg.getFileData(), msg.getMessageId());
                saveIncomingFile(msg);
                recordIncomingTrackable(msg.getSender(), false, msg.getMessageId());
                break;
            case PRIVATE_DELETE:
                deleteMessageLocally(keyFor(msg.getSender(), false), msg.getContent());
                break;

            case GROUP_TEXT:
            case GROUP_EMOJI:
                clearTypingIndicator(keyFor(msg.getReceiver(), true));
                appendText(msg.getReceiver(), true, msg.getSender(), msg.getContent(), msg.getMessageId());
                break;
            case GROUP_LOCATION:
                clearTypingIndicator(keyFor(msg.getReceiver(), true));
                appendText(msg.getReceiver(), true, msg.getSender(),
                        locationText(msg.getContent()), msg.getMessageId());
                break;
            case GROUP_STICKER:
                clearTypingIndicator(keyFor(msg.getReceiver(), true));
                appendImage(msg.getReceiver(), true, msg.getSender(),
                        scaledIcon(msg.getFileData(), 84), null, msg.getMessageId());
                break;
            case GROUP_IMAGE:
                clearTypingIndicator(keyFor(msg.getReceiver(), true));
                appendImage(msg.getReceiver(), true, msg.getSender(),
                        scaledIcon(msg.getFileData(), 240), msg.getFileName(), msg.getMessageId());
                saveIncomingFile(msg);
                break;
            case GROUP_FILE:
                clearTypingIndicator(keyFor(msg.getReceiver(), true));
                appendFile(msg.getReceiver(), true, msg.getSender(),
                        msg.getFileName(), msg.getFileData(), msg.getMessageId());
                saveIncomingFile(msg);
                break;
            case GROUP_DELETE:
                deleteMessageLocally(keyFor(msg.getReceiver(), true), msg.getContent());
                break;

            case VIDEO_CALL_REQUEST:
                handleIncomingCallRequest(msg.getSender());
                break;
            case VIDEO_CALL_ACCEPT: {
                VideoCallFrame f = activeCalls.get(msg.getSender());
                if (f != null) f.startActive();
                break;
            }
            case VIDEO_CALL_REJECT: {
                VideoCallFrame f = activeCalls.remove(msg.getSender());
                if (f != null) {
                    String why = "offline".equals(msg.getContent()) ? msg.getSender() + " đang offline"
                            : "busy".equals(msg.getContent()) ? msg.getSender() + " đang bận cuộc gọi khác"
                            : "Cuộc gọi bị từ chối bởi " + msg.getSender();
                    f.onRemoteEnded(why);
                }
                break;
            }
            case VIDEO_CALL_END: {
                // Người gọi cúp máy khi mình chưa kịp nghe -> đóng hộp thoại "Cuộc gọi đến"
                if (incomingDialog != null && msg.getSender().equals(incomingCaller)) {
                    dismissIncomingDialog();
                    statusBar.setForeground(UI.MUTED);
                    statusBar.setText("Cuộc gọi nhỡ từ " + msg.getSender());
                }
                VideoCallFrame f = activeCalls.remove(msg.getSender());
                if (f != null) {
                    f.onRemoteEnded("offline".equals(msg.getContent())
                            ? msg.getSender() + " đã mất kết nối"
                            : msg.getSender() + " đã kết thúc cuộc gọi");
                }
                break;
            }
            case VIDEO_FRAME: {
                VideoCallFrame f = activeCalls.get(msg.getSender());
                if (f != null) f.onRemoteFrame(msg);
                break;
            }
            case AUDIO_FRAME: {
                VideoCallFrame f = activeCalls.get(msg.getSender());
                if (f != null) f.onRemoteAudio(msg.getFileData());
                break;
            }

            default:
                // LOGIN_SUCCESS / LOGIN_FAILED đã xử lý ở màn hình đăng nhập
        }
    }

    private void handleGroupInvite(String inviter, String groupName) {
        int choice = JOptionPane.showConfirmDialog(this,
                inviter + " mời bạn vào nhóm '" + groupName + "'. Tham gia?",
                "Lời mời vào nhóm", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            client.send(new Message(Message.Type.GROUP_INVITE_ACCEPT, client.getUsername(), groupName, null));
        } else {
            client.send(new Message(Message.Type.GROUP_INVITE_DECLINE, client.getUsername(), groupName, null));
        }
    }

    private void handleIncomingCallRequest(final String caller) {
        // Đang bận (trong cuộc gọi khác hoặc đang có hộp thoại cuộc gọi đến): tự từ chối
        if (!activeCalls.isEmpty() || incomingDialog != null) {
            client.send(new Message(Message.Type.VIDEO_CALL_REJECT, client.getUsername(), caller, "busy"));
            return;
        }
        final JOptionPane pane = new JOptionPane(caller + " đang gọi video cho bạn. Nhận cuộc gọi?",
                JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
        final JDialog dialog = pane.createDialog(this, "Cuộc gọi đến");
        dialog.setModal(false);
        dialog.setAlwaysOnTop(true);
        incomingDialog = dialog;
        incomingCaller = caller;
        pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, new java.beans.PropertyChangeListener() {
            @Override
            public void propertyChange(java.beans.PropertyChangeEvent ev) {
                Object v = pane.getValue();
                if (v == JOptionPane.UNINITIALIZED_VALUE || incomingDialog != dialog) return;
                dismissIncomingDialog();
                if (v instanceof Integer && ((Integer) v).intValue() == JOptionPane.YES_OPTION) {
                    acceptCall(caller);
                } else {
                    client.send(new Message(Message.Type.VIDEO_CALL_REJECT, client.getUsername(), caller, null));
                }
            }
        });
        dialog.setVisible(true);
    }

    /** Đóng hộp thoại cuộc gọi đến (nếu có) mà không gửi phản hồi nào. */
    private void dismissIncomingDialog() {
        JDialog d = incomingDialog;
        incomingDialog = null;
        incomingCaller = null;
        if (d != null) d.dispose();
    }

    private void acceptCall(final String caller) {
        VideoCallFrame frame = new VideoCallFrame(client, caller, false);
        frame.setOnClosed(new Runnable() {
            @Override
            public void run() {
                activeCalls.remove(caller);
            }
        });
        activeCalls.put(caller, frame);
        frame.setVisible(true);
        client.send(new Message(Message.Type.VIDEO_CALL_ACCEPT, client.getUsername(), caller, null));
    }

    private void saveIncomingFile(Message msg) {
        saveIncomingFile(msg, true);
    }

    private void saveIncomingFile(Message msg, boolean overwrite) {
        if (msg.getFileData() == null || msg.getFileName() == null) return;
        String safeName = new File(msg.getFileName()).getName();   // bỏ mọi thành phần đường dẫn
        if (safeName.isEmpty()) return;
        try {
            Path target = DOWNLOAD_DIR.resolve(safeName);
            if (!overwrite && Files.exists(target)) return;
            Files.write(target, msg.getFileData());
            if (overwrite) {
                statusBar.setForeground(UI.MUTED);
                statusBar.setText("Đã nhận \"" + safeName + "\" - tự lưu vào " + DOWNLOAD_DIR);
            }
        } catch (IOException ignored) {
        }
    }

    private void refreshUserList(String csv) {
        String selectedUsername = (currentTarget != null && !currentIsGroup) ? currentTarget : null;
        userModel.clear();
        onlineState.clear();
        if (csv == null || csv.isEmpty()) return;

        List<UserEntry> online = new ArrayList<UserEntry>();
        List<UserEntry> offline = new ArrayList<UserEntry>();
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            String[] kv = part.split(":", 2);
            String uname = kv[0];
            boolean isOnline = kv.length > 1 && "ONLINE".equalsIgnoreCase(kv[1]);
            onlineState.put(uname, Boolean.valueOf(isOnline));
            if (uname.equals(client.getUsername())) continue;
            (isOnline ? online : offline).add(new UserEntry(uname, isOnline));
        }
        Comparator<UserEntry> byName = new Comparator<UserEntry>() {
            @Override
            public int compare(UserEntry a, UserEntry b) {
                return a.username.toLowerCase().compareTo(b.username.toLowerCase());
            }
        };
        Collections.sort(online, byName);
        Collections.sort(offline, byName);
        for (UserEntry u : online) userModel.addElement(u);
        for (UserEntry u : offline) userModel.addElement(u);

        if (selectedUsername != null) {
            for (int i = 0; i < userModel.size(); i++) {
                if (userModel.get(i).username.equals(selectedUsername)) {
                    userList.setSelectedIndex(i);
                    break;
                }
            }
        }
        updateHeader();
    }

    private void refreshGroupList(String encoded) {
        groupModel.clear();
        groupMembers.clear();
        if (encoded == null || encoded.isEmpty()) {
            dropStaleGroupPanes();
            updateHeader();
            return;
        }
        String[] entries = encoded.split(";");
        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i];
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":", 2);
            String name = parts[0];
            Set<String> members = new LinkedHashSet<String>();
            if (parts.length > 1) {
                String[] ms = parts[1].split(",");
                for (int j = 0; j < ms.length; j++) {
                    if (!ms[j].isEmpty()) members.add(ms[j]);
                }
            }
            groupModel.addElement(name);
            groupMembers.put(name, members);
        }
        dropStaleGroupPanes();
        if (currentTarget != null && currentIsGroup) {
            for (int i = 0; i < groupModel.size(); i++) {
                if (groupModel.get(i).equals(currentTarget)) {
                    groupList.setSelectedIndex(i);
                    break;
                }
            }
        }
        updateHeader();
    }

    /** Bỏ khung chat của những nhóm mà mình không còn là thành viên (đã rời / nhóm giải tán). */
    private void dropStaleGroupPanes() {
        Iterator<Map.Entry<String, ChatPanel>> it = panes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ChatPanel> en = it.next();
            String key = en.getKey();
            if (!key.startsWith("group:")) continue;
            if (groupMembers.containsKey(key.substring("group:".length()))) continue;
            Component wrapper = en.getValue().getParent();
            if (wrapper != null) chatCards.remove(wrapper);
            it.remove();
            statusLabels.remove(key);
            lastKnownStatusText.remove(key);
            javax.swing.Timer t = typingTimers.remove(key);
            if (t != null) t.stop();
        }
        if (currentIsGroup && currentTarget != null && !groupMembers.containsKey(currentTarget)) {
            currentTarget = null;
            currentIsGroup = false;
            groupList.clearSelection();
            ((CardLayout) chatCards.getLayout()).show(chatCards, EMPTY_CARD);
        }
        chatCards.revalidate();
        chatCards.repaint();
    }

    private static class UserEntry {
        final String username;
        final boolean online;

        UserEntry(String username, boolean online) {
            this.username = username;
            this.online = online;
        }

        @Override
        public String toString() {
            return username;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserEntry)) return false;
            return username.equals(((UserEntry) o).username);
        }

        @Override
        public int hashCode() {
            return username.hashCode();
        }
    }

    private class RowRenderer extends JPanel implements ListCellRenderer<Object> {
        private final boolean groupMode;
        private final JLabel avatar = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel subtitle = new JLabel();
        private boolean selected;

        RowRenderer(boolean groupMode) {
            this.groupMode = groupMode;
            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            title.setFont(UI.font(Font.BOLD, 13f));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            subtitle.setFont(UI.font(Font.PLAIN, 11.5f));
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(Box.createVerticalGlue());
            text.add(title);
            text.add(Box.createVerticalStrut(2));
            text.add(subtitle);
            text.add(Box.createVerticalGlue());

            add(avatar, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            selected = isSelected;
            if (value instanceof UserEntry) {
                UserEntry u = (UserEntry) value;
                avatar.setIcon(UI.avatarCached(u.username, 38, true, u.online));
                title.setText(u.username);
                title.setForeground(u.online ? UI.TEXT : UI.MUTED);
                subtitle.setText(u.online ? "Đang hoạt động" : "Ngoại tuyến");
                subtitle.setForeground(u.online ? UI.ONLINE : UI.MUTED);
            } else {
                String name = String.valueOf(value);
                avatar.setIcon(UI.avatarCached(name, 38, false, false));
                title.setText(name);
                title.setForeground(UI.TEXT);
                Set<String> members = groupMembers.get(name);
                int n = members == null ? 0 : members.size();
                subtitle.setText(groupMode ? n + " thành viên" : "Nhóm");
                subtitle.setForeground(UI.MUTED);
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (selected) {
                Graphics2D g2 = UI.aa(g);
                g2.setColor(UI.PRIMARY_SOFT);
                g2.fillRoundRect(0, 1, getWidth(), getHeight() - 2, 12, 12);
                g2.setColor(UI.PRIMARY);
                g2.fillRoundRect(0, 8, 3, getHeight() - 16, 3, 3);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
