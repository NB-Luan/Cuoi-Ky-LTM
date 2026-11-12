package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ChatPanel extends JPanel {
    public interface DeleteHandler {
        void onDelete(String messageId);
    }

    private static final int BUBBLE_MAX_WIDTH = 340;

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
    private final JPanel list = new JPanel();
    private final JScrollPane scroll;
    private final Map<String, Bubble> bubbles = new HashMap<String, Bubble>();
    private DeleteHandler deleteHandler;

    public ChatPanel() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(UI.CHAT_BG);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(list, BorderLayout.NORTH);

        scroll = new JScrollPane(holder);
        UI.styleScroll(scroll);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(UI.CHAT_BG);
        add(scroll, BorderLayout.CENTER);
    }

    public void setDeleteHandler(DeleteHandler handler) {
        this.deleteHandler = handler;
    }


    public void addText(String sender, boolean mine, String text, String messageId, boolean showSender) {
        Bubble b = new Bubble(mine, showSender ? sender : null);
        b.setContent(UI.wrapLabel(text, mine ? Color.WHITE : UI.TEXT, BUBBLE_MAX_WIDTH, 13.5f));
        push(b, mine, messageId);
    }

    public void addImage(String sender, boolean mine, ImageIcon icon, String caption,
                         String messageId, boolean showSender) {
        Bubble b = new Bubble(mine, showSender ? sender : null);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel pic = new JLabel(icon);
        pic.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(pic);

        if (caption != null && !caption.isEmpty()) {
            JLabel cap = UI.wrapLabel(caption, mine ? new Color(255, 255, 255, 215) : UI.MUTED,
                    BUBBLE_MAX_WIDTH, 11.5f);
            cap.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(Box.createVerticalStrut(5));
            content.add(cap);
        }
        b.setContent(content);
        push(b, mine, messageId);
    }

    /**
     * Bong bóng tệp đính kèm có nút "Lưu về..." (chọn nơi lưu) và "Mở".
     * Dữ liệu tệp được giữ trong bộ nhớ của bong bóng để tải lại bao nhiêu lần cũng được.
     */
    public void addFile(String sender, final boolean mine, final String fileName, final byte[] data,
                        String messageId, boolean showSender, final Consumer<String> onStatus) {
        Bubble b = new Bubble(mine, showSender ? sender : null);
        Color fg = mine ? Color.WHITE : UI.TEXT;
        Color sub = mine ? new Color(255, 255, 255, 205) : UI.MUTED;

        JPanel content = new JPanel(new BorderLayout(10, 0));
        content.setOpaque(false);
        content.add(new JLabel(UI.glyph("file", 30, fg)), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = UI.wrapLabel(fileName, fg, 230, 13.5f);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel size = new JLabel(formatSize(data.length));
        size.setFont(UI.font(Font.PLAIN, 11.5f));
        size.setForeground(sub);
        size.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(name);
        text.add(Box.createVerticalStrut(2));
        text.add(size);
        content.add(text, BorderLayout.CENTER);

        Color btnBg = mine ? new Color(255, 255, 255, 45) : UI.PRIMARY_SOFT;
        Color btnHover = mine ? new Color(255, 255, 255, 90) : new Color(0xE2E5FD);
        Color btnFg = mine ? Color.WHITE : UI.PRIMARY_DARK;

        final UI.Btn save = new UI.Btn("Lưu về...", null, btnBg, btnHover, btnFg, 10);
        final UI.Btn open = new UI.Btn("Mở", null, btnBg, btnHover, btnFg, 10);
        for (UI.Btn btn : new UI.Btn[]{save, open}) {
            btn.setFont(UI.font(Font.BOLD, 12f));
            btn.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        }
        save.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveFileAs(save, fileName, data, onStatus);
            }
        });
        open.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openFile(open, fileName, data, onStatus);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(save);
        buttons.add(open);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(content);
        box.add(Box.createVerticalStrut(8));
        box.add(buttons);

        b.setContent(box);
        push(b, mine, messageId);
    }

    private static String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String safeName(String name) {
        String n = name == null ? "" : new File(name).getName();
        return n.isEmpty() ? "file" : n;
    }

    private void saveFileAs(Component parent, String fileName, byte[] data, Consumer<String> onStatus) {
        JFileChooser chooser = new JFileChooser();
        Path downloads = Paths.get(System.getProperty("user.home"), "Downloads");
        if (Files.isDirectory(downloads)) chooser.setCurrentDirectory(downloads.toFile());
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), safeName(fileName)));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File target = chooser.getSelectedFile();
        if (target.exists() && JOptionPane.showConfirmDialog(parent,
                "Tệp \"" + target.getName() + "\" đã tồn tại. Ghi đè?", "Xác nhận",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            Files.write(target.toPath(), data);
            if (onStatus != null) onStatus.accept("Đã lưu tệp: " + target.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Không lưu được tệp: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openFile(Component parent, String fileName, byte[] data, Consumer<String> onStatus) {
        try {
            Path dir = Paths.get(System.getProperty("user.home"), "chat-downloads");
            Files.createDirectories(dir);
            Path target = dir.resolve(safeName(fileName));
            Files.write(target, data);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(target.toFile());
            } else if (onStatus != null) {
                onStatus.accept("Đã lưu tệp: " + target + " (không mở tự động được trên máy này)");
            }
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(parent, "Không mở được tệp: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void addSystem(String text) {
        Pill pill = new Pill(text);
        JPanel row = row(Component.CENTER_ALIGNMENT);
        row.add(pill);
        appendRow(row);
    }

    public void deleteMessage(String messageId) {
        if (messageId == null) return;
        Bubble b = bubbles.get(messageId);
        if (b == null) return;
        b.markDeleted();
        list.revalidate();
        list.repaint();
    }

    public boolean isEmpty() {
        return list.getComponentCount() == 0;
    }

    private void push(Bubble bubble, boolean mine, String messageId) {
        if (messageId != null) {
            bubbles.put(messageId, bubble);
            if (mine) attachDeleteMenu(bubble, messageId);
        }
        JPanel row = row(mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
        row.add(bubble);
        appendRow(row);
    }

    private JPanel row(float align) {
        int flow = FlowLayout.LEFT;
        if (align == Component.RIGHT_ALIGNMENT) flow = FlowLayout.RIGHT;
        else if (align == Component.CENTER_ALIGNMENT) flow = FlowLayout.CENTER;

        JPanel p = new JPanel(new FlowLayout(flow, 0, 3)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private void appendRow(JComponent row) {
        list.add(row);
        list.revalidate();
        list.repaint();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        JScrollBar bar = scroll.getVerticalScrollBar();
                        bar.setValue(bar.getMaximum());
                    }
                });
            }
        });
    }

    private void attachDeleteMenu(final Bubble bubble, final String messageId) {
        MouseAdapter adapter = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }

            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                if (bubble.isDeleted() || deleteHandler == null) return;
                JPopupMenu menu = new JPopupMenu();
                JMenuItem item = new JMenuItem("Xoá tin nhắn",
                        UI.glyph("trash", 14, UI.DANGER));
                item.setFont(UI.font(Font.PLAIN, 13f));
                item.setForeground(UI.DANGER);
                item.addActionListener(new java.awt.event.ActionListener() {
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent ev) {
                        deleteHandler.onDelete(messageId);
                    }
                });
                menu.add(item);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        };
        addListenerDeep(bubble, adapter);
    }

    private void addListenerDeep(Component c, MouseAdapter adapter) {
        c.addMouseListener(adapter);
        if (c instanceof Container) {
            Container container = (Container) c;
            for (int i = 0; i < container.getComponentCount(); i++) {
                addListenerDeep(container.getComponent(i), adapter);
            }
        }
    }

    private class Bubble extends JPanel {
        private final boolean mine;
        private final JPanel body = new JPanel();
        private final JLabel time = new JLabel();
        private boolean deleted = false;

        Bubble(boolean mine, String senderName) {
            this.mine = mine;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(9, 14, 7, 14));

            if (senderName != null && !mine) {
                JLabel name = new JLabel(senderName);
                name.setFont(UI.font(Font.BOLD, 11.5f));
                name.setForeground(UI.colorFor(senderName));
                name.setAlignmentX(Component.LEFT_ALIGNMENT);
                add(name);
                add(Box.createVerticalStrut(4));
            }

            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(body);

            time.setText(timeFmt.format(new Date()));
            time.setFont(UI.font(Font.PLAIN, 10.5f));
            time.setForeground(mine ? new Color(255, 255, 255, 185) : UI.MUTED);
            time.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(Box.createVerticalStrut(3));
            add(time);
        }

        void setContent(JComponent c) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.removeAll();
            body.add(c);
            body.revalidate();
        }

        boolean isDeleted() {
            return deleted;
        }

        void markDeleted() {
            deleted = true;
            JLabel note = new JLabel("Tin nhắn đã bị xoá");
            note.setFont(UI.font(Font.ITALIC, 12.5f));
            note.setForeground(UI.MUTED);
            setContent(note);
            time.setForeground(UI.MUTED);
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = UI.aa(g);
            int w = getWidth(), h = getHeight();
            int arc = 18;
            if (deleted) {
                g2.setColor(new Color(0xEDEFF5));
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(UI.BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            } else if (mine) {
                g2.setPaint(new GradientPaint(0, 0, UI.PRIMARY, w, h, UI.ACCENT));
                g2.fillRoundRect(0, 0, w, h, arc, arc);
            } else {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(UI.BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class Pill extends JPanel {
        Pill(String text) {
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
            JLabel l = new JLabel(text);
            l.setFont(UI.font(Font.PLAIN, 11.5f));
            l.setForeground(UI.MUTED);
            add(l, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = UI.aa(g);
            g2.setColor(new Color(0xE9ECF5));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
