package client;

import common.Message;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

public class LoginFrame extends JFrame {

    private final UI.HintField hostField = new UI.HintField("127.0.0.1", "127.0.0.1");
    private final UI.HintField portField = new UI.HintField("9999", "9999");
    private final UI.HintField userField = new UI.HintField("Tên đăng nhập", "");
    private final UI.HintPassword passwordField = new UI.HintPassword("Mật khẩu");
    private final JLabel status = new JLabel(" ");
    private final UI.Btn loginBtn = UI.primaryButton("Đăng nhập");
    private final UI.Btn registerBtn = UI.softButton("Tạo tài khoản", null);

    public LoginFrame() {
        super("Java TCP Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UI.CARD_BG);

        add(buildBrandPanel(), BorderLayout.WEST);
        add(buildFormPanel(), BorderLayout.CENTER);

        loginBtn.addActionListener(e -> doLogin());
        registerBtn.addActionListener(e -> doRegister());
        passwordField.addActionListener(e -> doLogin());
        userField.addActionListener(e -> passwordField.requestFocusInWindow());
        getRootPane().setDefaultButton(null);

        setSize(880, 540);
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(null);
    }

    private JComponent buildBrandPanel() {
        JPanel brand = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = UI.aa(g);
                int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, UI.PRIMARY, w, h, UI.ACCENT));
                g2.fillRect(0, 0, w, h);
                g2.setColor(new Color(255, 255, 255, 26));
                g2.fill(new Ellipse2D.Float(-w * 0.35f, h * 0.55f, w * 1.1f, w * 1.1f));
                g2.fill(new Ellipse2D.Float(w * 0.55f, -w * 0.3f, w * 0.85f, w * 0.85f));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        brand.setOpaque(false);
        brand.setPreferredSize(new Dimension(340, 0));
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBorder(BorderFactory.createEmptyBorder(48, 38, 38, 38));

        JLabel logo = new JLabel(UI.glyph("smile", 44, Color.WHITE));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.add(logo);
        brand.add(Box.createVerticalStrut(18));

        JLabel title = UI.label("Java TCP Chat", Font.BOLD, 26f, Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.add(title);
        brand.add(Box.createVerticalStrut(10));

        JLabel sub = UI.wrapLabel(
                "Nhắn tin, gọi video, gửi ảnh và tệp qua kết nối TCP thuần Java.",
                new Color(255, 255, 255, 225), 250, 13.5f);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.add(sub);
        brand.add(Box.createVerticalStrut(34));

        brand.add(feature("user", "Chat 1-1 và chat nhóm"));
        brand.add(feature("image", "Gửi ảnh, tệp, sticker, vị trí"));
        brand.add(feature("video", "Gọi video giữa hai người"));
        brand.add(feature("trash", "Thu hồi tin nhắn đã gửi"));

        brand.add(Box.createVerticalGlue());
        JLabel foot = UI.label("Đồ án mạng máy tính \u00b7 Java Swing", Font.PLAIN, 11.5f,
                new Color(255, 255, 255, 175));
        foot.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.add(foot);
        return brand;
    }

    private JComponent feature(String icon, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.add(new JLabel(UI.glyph(icon, 17, new Color(255, 255, 255, 230))));
        row.add(UI.label(text, Font.PLAIN, 13f, new Color(255, 255, 255, 235)));
        return row;
    }

    private JComponent buildFormPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(UI.CARD_BG);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(340, 420));

        JLabel hello = UI.label("Chào mừng trở lại!", Font.BOLD, 21f, UI.TEXT);
        hello.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(hello);
        form.add(Box.createVerticalStrut(6));

        JLabel hint = UI.label("Đăng nhập để bắt đầu trò chuyện", Font.PLAIN, 13f, UI.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(hint);
        form.add(Box.createVerticalStrut(26));

        JPanel server = new JPanel(new GridLayout(1, 2, 10, 0));
        server.setOpaque(false);
        server.setAlignmentX(Component.LEFT_ALIGNMENT);
        server.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        server.add(labeled("Máy chủ", hostField));
        server.add(labeled("Cổng", portField));
        form.add(server);
        form.add(Box.createVerticalStrut(14));

        form.add(labeled("Tài khoản", userField));
        form.add(Box.createVerticalStrut(14));
        form.add(labeled("Mật khẩu", passwordField));
        form.add(Box.createVerticalStrut(22));

        loginBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        loginBtn.setFont(UI.font(Font.BOLD, 14f));
        form.add(loginBtn);
        form.add(Box.createVerticalStrut(10));

        registerBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        registerBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        form.add(registerBtn);
        form.add(Box.createVerticalStrut(16));

        status.setFont(UI.font(Font.PLAIN, 12.5f));
        status.setForeground(UI.DANGER);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        form.add(status);

        outer.add(form);
        return outer;
    }

    private JComponent labeled(String caption, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        JLabel l = UI.label(caption, Font.BOLD, 11.5f, UI.MUTED);
        p.add(l, BorderLayout.NORTH);
        p.add(UI.fieldWrap(field), BorderLayout.CENTER);
        return p;
    }

    private String[] readAndValidateFields() {
        String host = hostField.getText().trim();
        String username = userField.getText().trim();
        String password = new String(passwordField.getPassword());
        String portText = portField.getText().trim();

        if (host.isEmpty()) host = "127.0.0.1";
        if (portText.isEmpty()) portText = "9999";

        if (username.isEmpty()) {
            showError("Vui lòng nhập tên đăng nhập");
            return null;
        }
        if (password.isEmpty()) {
            showError("Vui lòng nhập mật khẩu");
            return null;
        }
        try {
            Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            showError("Cổng không hợp lệ");
            return null;
        }
        return new String[]{host, portText, username, password};
    }

    private void showError(String text) {
        status.setForeground(UI.DANGER);
        status.setText(text);
    }

    private void showInfo(String text) {
        status.setForeground(UI.MUTED);
        status.setText(text);
    }

    private void setControlsEnabled(boolean enabled) {
        loginBtn.setEnabled(enabled);
        registerBtn.setEnabled(enabled);
    }

    private void doLogin() {
        String[] fields = readAndValidateFields();
        if (fields == null) return;
        String host = fields[0];
        int port = Integer.parseInt(fields[1]);
        final String username = fields[2];
        final String password = fields[3];

        showInfo("Đang kết nối...");
        setControlsEnabled(false);

        final ChatClient client = new ChatClient(host, port);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                return client.login(username, password);
            }

            @Override
            protected void done() {
                setControlsEnabled(true);
                try {
                    Message reply = get();
                    if (reply.getType() == Message.Type.LOGIN_SUCCESS) {
                        // Tạo MainFrame trước để nó kịp đăng ký listener qua
                        // client.setListener(...) TRƯỚC khi luồng đọc chạy, tránh
                        // mất các message USER_LIST/GROUP_LIST gửi ngay sau login.
                        MainFrame frame = new MainFrame(client);
                        client.startListening();
                        frame.setVisible(true);
                        dispose();
                    } else {
                        showError(reply.getContent() != null ? reply.getContent() : "Đăng nhập thất bại");
                    }
                } catch (Exception ex) {
                    showError("Không kết nối được: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void doRegister() {
        String[] fields = readAndValidateFields();
        if (fields == null) return;
        String host = fields[0];
        int port = Integer.parseInt(fields[1]);
        final String username = fields[2];
        final String password = fields[3];

        showInfo("Đang tạo tài khoản...");
        setControlsEnabled(false);

        final ChatClient client = new ChatClient(host, port);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                return client.register(username, password);
            }

            @Override
            protected void done() {
                setControlsEnabled(true);
                try {
                    Message reply = get();
                    if (reply.getType() == Message.Type.REGISTER_SUCCESS) {
                        status.setForeground(UI.SUCCESS);
                        status.setText("Đã tạo tài khoản! Bấm Đăng nhập để vào.");
                    } else {
                        showError(reply.getContent() != null ? reply.getContent() : "Tạo tài khoản thất bại");
                    }
                } catch (Exception ex) {
                    showError("Không kết nối được: " + ex.getMessage());
                }
            }
        }.execute();
    }

    public static void main(String[] args) {
        UI.install();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LoginFrame().setVisible(true);
            }
        });
    }
}
