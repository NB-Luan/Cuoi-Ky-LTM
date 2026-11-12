package client;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class UI {

    private UI() {
    }


    public static final Color PRIMARY = new Color(0x4F46E5);
    public static final Color PRIMARY_DARK = new Color(0x4338CA);
    public static final Color PRIMARY_SOFT = new Color(0xEEF0FE);
    public static final Color ACCENT = new Color(0x7C3AED);

    public static final Color APP_BG = new Color(0xF2F4F9);
    public static final Color CARD_BG = Color.WHITE;
    public static final Color CHAT_BG = new Color(0xF7F8FC);
    public static final Color BORDER = new Color(0xE4E8F1);
    public static final Color HOVER = new Color(0xF1F3FA);

    public static final Color TEXT = new Color(0x1E2333);
    public static final Color MUTED = new Color(0x8A90A2);
    public static final Color ONLINE = new Color(0x22C55E);
    public static final Color OFFLINE = new Color(0xB4BAC9);
    public static final Color DANGER = new Color(0xE11D48);
    public static final Color SUCCESS = new Color(0x16A34A);

    private static final Color[] AVATAR_PALETTE = {
            new Color(0x4F46E5), new Color(0x0EA5E9), new Color(0x10B981),
            new Color(0xF59E0B), new Color(0xEF4444), new Color(0x8B5CF6),
            new Color(0xEC4899), new Color(0x14B8A6)
    };

    private static final String FAMILY = pickFamily();

    private static String pickFamily() {
        String[] prefs = {"Segoe UI", "Inter", "Roboto", "Noto Sans", "Helvetica Neue",
                "SansSerif", "DejaVu Sans", "Dialog"};
        try {
            String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            Set<String> available = new HashSet<String>(Arrays.asList(names));
            for (int i = 0; i < prefs.length; i++) {
                if (available.contains(prefs[i])) return prefs[i];
            }
        } catch (Exception ignored) {
        }
        return "Dialog";
    }

    public static Font font(int style, float size) {
        return new Font(FAMILY, style, Math.round(size));
    }

    public static void install() {
        try {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme() {
                @Override protected ColorUIResource getPrimary1() { return new ColorUIResource(PRIMARY_DARK); }
                @Override protected ColorUIResource getPrimary2() { return new ColorUIResource(PRIMARY); }
                @Override protected ColorUIResource getPrimary3() { return new ColorUIResource(PRIMARY_SOFT); }
                @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(0xC9CEDC); }
                @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(0xE4E8F1); }
                @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(0xF2F4F9); }
                @Override protected ColorUIResource getBlack() { return new ColorUIResource(TEXT); }
                @Override public FontUIResource getControlTextFont() { return new FontUIResource(font(Font.PLAIN, 13f)); }
                @Override public FontUIResource getSystemTextFont() { return new FontUIResource(font(Font.PLAIN, 13f)); }
                @Override public FontUIResource getUserTextFont() { return new FontUIResource(font(Font.PLAIN, 13f)); }
                @Override public FontUIResource getMenuTextFont() { return new FontUIResource(font(Font.PLAIN, 13f)); }
                @Override public FontUIResource getSubTextFont() { return new FontUIResource(font(Font.PLAIN, 11f)); }
                @Override public FontUIResource getWindowTitleFont() { return new FontUIResource(font(Font.BOLD, 13f)); }
            });
            UIManager.setLookAndFeel(new MetalLookAndFeel());
            UIManager.put("Panel.background", APP_BG);
            UIManager.put("OptionPane.background", CARD_BG);
            UIManager.put("OptionPane.messageForeground", TEXT);
            UIManager.put("ToolTip.background", TEXT);
            UIManager.put("ToolTip.foreground", Color.WHITE);
            UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(4, 8, 4, 8));
            UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
            UIManager.put("MenuItem.selectionBackground", PRIMARY_SOFT);
            UIManager.put("MenuItem.selectionForeground", PRIMARY_DARK);
            UIManager.put("List.background", CARD_BG);
            UIManager.put("TextField.background", CARD_BG);
            UIManager.put("swing.boldMetal", Boolean.FALSE);
        } catch (Exception ignored) {
        }
    }

    public static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') sb.append("&amp;");
            else if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else if (c == '"') sb.append("&quot;");
            else sb.append(c);
        }
        return sb.toString();
    }

    public static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    public static JLabel wrapLabel(String text, Color fg, int maxWidth, float size) {
        JLabel l = new JLabel();
        l.setFont(font(Font.PLAIN, size));
        l.setForeground(fg);
        FontMetrics fm = l.getFontMetrics(l.getFont());
        String safe = text == null ? "" : text;
        boolean multi = safe.indexOf('\n') >= 0 || fm.stringWidth(safe) > maxWidth;
        String body = esc(safe).replace("\n", "<br>");
        if (multi) {
            l.setText("<html><body style='width:" + maxWidth + "px'>" + body + "</body></html>");
        } else {
            l.setText("<html>" + body + "</html>");
        }
        return l;
    }

    public static Color colorFor(String name) {
        if (name == null || name.isEmpty()) return PRIMARY;
        int h = Math.abs(name.hashCode()) % AVATAR_PALETTE.length;
        return AVATAR_PALETTE[h];
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static final java.util.Map<String, ImageIcon> AVATAR_CACHE =
            new java.util.HashMap<String, ImageIcon>();

    public static ImageIcon avatar(String name, int size) {
        return avatar(name, size, false, false);
    }

    public static ImageIcon avatarCached(String name, int size, boolean showStatus, boolean online) {
        String key = name + "|" + size + "|" + showStatus + "|" + online;
        ImageIcon icon = AVATAR_CACHE.get(key);
        if (icon == null) {
            icon = avatar(name, size, showStatus, online);
            AVATAR_CACHE.put(key, icon);
        }
        return icon;
    }

    public static ImageIcon avatar(String name, int size, boolean showStatus, boolean online) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img.getGraphics());
        Color base = colorFor(name);
        Color light = new Color(
                Math.min(255, base.getRed() + 45),
                Math.min(255, base.getGreen() + 45),
                Math.min(255, base.getBlue() + 45));
        g.setPaint(new GradientPaint(0, 0, light, size, size, base));
        g.fill(new Ellipse2D.Float(0, 0, size, size));

        String initial = "?";
        if (name != null && name.length() > 0) {
            initial = name.substring(0, 1).toUpperCase();
        }
        g.setColor(Color.WHITE);
        g.setFont(font(Font.BOLD, size * 0.44f));
        FontMetrics fm = g.getFontMetrics();
        int tx = (size - fm.stringWidth(initial)) / 2;
        int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(initial, tx, ty);

        if (showStatus) {
            int d = Math.max(8, size / 4);
            int x = size - d;
            int y = size - d;
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(x - 2, y - 2, d + 4, d + 4));
            g.setColor(online ? ONLINE : OFFLINE);
            g.fill(new Ellipse2D.Float(x, y, d, d));
        }
        g.dispose();
        return new ImageIcon(img);
    }

    public static Icon glyph(final String kind, final int size, final Color color) {
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = aa(g);
                g2.translate(x, y);
                g2.setColor(color);
                float s = size;
                g2.setStroke(new BasicStroke(Math.max(1.4f, s / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                draw(g2, kind, s);
                g2.dispose();
            }
        };
    }

    private static void draw(Graphics2D g, String kind, float s) {
        if ("smile".equals(kind)) {
            g.draw(new Ellipse2D.Float(s * 0.08f, s * 0.08f, s * 0.84f, s * 0.84f));
            g.fill(new Ellipse2D.Float(s * 0.31f, s * 0.33f, s * 0.1f, s * 0.13f));
            g.fill(new Ellipse2D.Float(s * 0.59f, s * 0.33f, s * 0.1f, s * 0.13f));
            g.draw(new java.awt.geom.Arc2D.Float(s * 0.26f, s * 0.36f, s * 0.48f, s * 0.38f, 200, 140, java.awt.geom.Arc2D.OPEN));
        } else if ("sticker".equals(kind)) {
            g.draw(new java.awt.geom.RoundRectangle2D.Float(s * 0.1f, s * 0.1f, s * 0.8f, s * 0.8f, s * 0.3f, s * 0.3f));
            GeneralPath star = new GeneralPath();
            float cx = s * 0.5f, cy = s * 0.5f, r1 = s * 0.26f, r2 = s * 0.11f;
            for (int i = 0; i < 10; i++) {
                double ang = Math.PI / 2 + i * Math.PI / 5;
                float r = (i % 2 == 0) ? r1 : r2;
                float px = cx + (float) (Math.cos(ang) * r);
                float py = cy - (float) (Math.sin(ang) * r);
                if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
            }
            star.closePath();
            g.fill(star);
        } else if ("image".equals(kind)) {
            g.draw(new java.awt.geom.RoundRectangle2D.Float(s * 0.1f, s * 0.15f, s * 0.8f, s * 0.7f, s * 0.18f, s * 0.18f));
            g.fill(new Ellipse2D.Float(s * 0.27f, s * 0.29f, s * 0.14f, s * 0.14f));
            GeneralPath hill = new GeneralPath();
            hill.moveTo(s * 0.14f, s * 0.78f);
            hill.lineTo(s * 0.4f, s * 0.48f);
            hill.lineTo(s * 0.58f, s * 0.68f);
            hill.lineTo(s * 0.7f, s * 0.56f);
            hill.lineTo(s * 0.86f, s * 0.78f);
            hill.closePath();
            g.fill(hill);
        } else if ("file".equals(kind)) {
            GeneralPath doc = new GeneralPath();
            doc.moveTo(s * 0.24f, s * 0.1f);
            doc.lineTo(s * 0.58f, s * 0.1f);
            doc.lineTo(s * 0.78f, s * 0.32f);
            doc.lineTo(s * 0.78f, s * 0.9f);
            doc.lineTo(s * 0.24f, s * 0.9f);
            doc.closePath();
            g.draw(doc);
            g.draw(new java.awt.geom.Line2D.Float(s * 0.58f, s * 0.1f, s * 0.58f, s * 0.32f));
            g.draw(new java.awt.geom.Line2D.Float(s * 0.58f, s * 0.32f, s * 0.78f, s * 0.32f));
        } else if ("pin".equals(kind)) {
            GeneralPath pin = new GeneralPath();
            pin.moveTo(s * 0.5f, s * 0.92f);
            pin.curveTo(s * 0.5f, s * 0.92f, s * 0.16f, s * 0.58f, s * 0.16f, s * 0.4f);
            pin.curveTo(s * 0.16f, s * 0.19f, s * 0.32f, s * 0.08f, s * 0.5f, s * 0.08f);
            pin.curveTo(s * 0.68f, s * 0.08f, s * 0.84f, s * 0.19f, s * 0.84f, s * 0.4f);
            pin.curveTo(s * 0.84f, s * 0.58f, s * 0.5f, s * 0.92f, s * 0.5f, s * 0.92f);
            pin.closePath();
            g.draw(pin);
            g.fill(new Ellipse2D.Float(s * 0.39f, s * 0.29f, s * 0.22f, s * 0.22f));
        } else if ("send".equals(kind)) {
            GeneralPath plane = new GeneralPath();
            plane.moveTo(s * 0.1f, s * 0.5f);
            plane.lineTo(s * 0.9f, s * 0.12f);
            plane.lineTo(s * 0.62f, s * 0.9f);
            plane.lineTo(s * 0.48f, s * 0.58f);
            plane.closePath();
            g.fill(plane);
        } else if ("video".equals(kind)) {
            g.fill(new java.awt.geom.RoundRectangle2D.Float(s * 0.08f, s * 0.26f, s * 0.54f, s * 0.48f, s * 0.14f, s * 0.14f));
            GeneralPath lens = new GeneralPath();
            lens.moveTo(s * 0.92f, s * 0.26f);
            lens.lineTo(s * 0.92f, s * 0.74f);
            lens.lineTo(s * 0.68f, s * 0.58f);
            lens.lineTo(s * 0.68f, s * 0.42f);
            lens.closePath();
            g.fill(lens);
        } else if ("plus".equals(kind)) {
            g.draw(new java.awt.geom.Line2D.Float(s * 0.5f, s * 0.16f, s * 0.5f, s * 0.84f));
            g.draw(new java.awt.geom.Line2D.Float(s * 0.16f, s * 0.5f, s * 0.84f, s * 0.5f));
        } else if ("group".equals(kind)) {
            g.draw(new Ellipse2D.Float(s * 0.12f, s * 0.14f, s * 0.32f, s * 0.32f));
            g.draw(new java.awt.geom.Arc2D.Float(s * 0.02f, s * 0.52f, s * 0.52f, s * 0.5f, 0, 180, java.awt.geom.Arc2D.OPEN));
            g.draw(new Ellipse2D.Float(s * 0.56f, s * 0.2f, s * 0.26f, s * 0.26f));
            g.draw(new java.awt.geom.Arc2D.Float(s * 0.46f, s * 0.54f, s * 0.48f, s * 0.44f, 0, 180, java.awt.geom.Arc2D.OPEN));
        } else if ("user".equals(kind)) {
            g.draw(new Ellipse2D.Float(s * 0.3f, s * 0.12f, s * 0.4f, s * 0.4f));
            g.draw(new java.awt.geom.Arc2D.Float(s * 0.14f, s * 0.56f, s * 0.72f, s * 0.6f, 0, 180, java.awt.geom.Arc2D.OPEN));
        } else if ("logout".equals(kind)) {
            g.draw(new java.awt.geom.Line2D.Float(s * 0.4f, s * 0.12f, s * 0.14f, s * 0.12f));
            g.draw(new java.awt.geom.Line2D.Float(s * 0.14f, s * 0.12f, s * 0.14f, s * 0.88f));
            g.draw(new java.awt.geom.Line2D.Float(s * 0.14f, s * 0.88f, s * 0.4f, s * 0.88f));
            g.draw(new java.awt.geom.Line2D.Float(s * 0.44f, s * 0.5f, s * 0.9f, s * 0.5f));
            g.draw(new java.awt.geom.Line2D.Float(s * 0.72f, s * 0.32f, s * 0.9f, s * 0.5f));
            g.draw(new java.awt.geom.Line2D.Float(s * 0.72f, s * 0.68f, s * 0.9f, s * 0.5f));
        } else if ("trash".equals(kind)) {
            g.draw(new java.awt.geom.Line2D.Float(s * 0.14f, s * 0.26f, s * 0.86f, s * 0.26f));
            g.draw(new java.awt.geom.RoundRectangle2D.Float(s * 0.24f, s * 0.26f, s * 0.52f, s * 0.62f, s * 0.12f, s * 0.12f));
            g.draw(new java.awt.geom.Line2D.Float(s * 0.38f, s * 0.14f, s * 0.62f, s * 0.14f));
        } else if ("search".equals(kind)) {
            g.draw(new Ellipse2D.Float(s * 0.14f, s * 0.14f, s * 0.52f, s * 0.52f));
            g.draw(new java.awt.geom.Line2D.Float(s * 0.62f, s * 0.62f, s * 0.88f, s * 0.88f));
        } else if ("phone".equals(kind)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(s * 0.2f, s * 0.14f);
            p.lineTo(s * 0.38f, s * 0.14f);
            p.lineTo(s * 0.46f, s * 0.36f);
            p.lineTo(s * 0.33f, s * 0.46f);
            p.curveTo(s * 0.42f, s * 0.64f, s * 0.56f, s * 0.76f, s * 0.7f, s * 0.82f);
            p.lineTo(s * 0.8f, s * 0.68f);
            p.lineTo(s * 0.94f, s * 0.78f);
            p.lineTo(s * 0.9f, s * 0.94f);
            g.draw(p);
        }
    }

    public static class Card extends JPanel {
        private final int radius;
        private Color fill;
        private Color line;
        private Color gradientTo;

        public Card(int radius, Color fill, Color line) {
            this.radius = radius;
            this.fill = fill;
            this.line = line;
            setOpaque(false);
        }

        public void setFill(Color c) { this.fill = c; repaint(); }
        public void setLine(Color c) { this.line = c; repaint(); }
        public void setGradientTo(Color c) { this.gradientTo = c; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            if (fill != null) {
                if (gradientTo != null) {
                    g2.setPaint(new GradientPaint(0, 0, fill, w, h, gradientTo));
                } else {
                    g2.setPaint(fill);
                }
                g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);
            }
            if (line != null) {
                g2.setColor(line);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class Btn extends JButton {
        private Color bg;
        private Color hover;
        private int radius;

        public Btn(String text, Icon icon, Color bg, Color hover, Color fg, int radius) {
            super(text, icon);
            this.bg = bg;
            this.hover = hover;
            this.radius = radius;
            setForeground(fg);
            setFont(font(Font.BOLD, 13f));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setIconTextGap(8);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        }

        public void setColors(Color bg, Color hover, Color fg) {
            this.bg = bg;
            this.hover = hover;
            setForeground(fg);
            repaint();
        }

        public void setRadius(int r) { this.radius = r; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Color c = bg;
            if (isEnabled()) {
                if (getModel().isPressed() && hover != null) c = hover.darker();
                else if (getModel().isRollover() && hover != null) c = hover;
            } else if (bg != null) {
                c = alpha(bg, 110);
            }
            if (c != null) {
                Graphics2D g2 = aa(g);
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    public static Btn primaryButton(String text) {
        Btn b = new Btn(text, null, PRIMARY, PRIMARY_DARK, Color.WHITE, 12);
        return b;
    }

    public static Btn softButton(String text, Icon icon) {
        Btn b = new Btn(text, icon, PRIMARY_SOFT, new Color(0xE2E5FD), PRIMARY_DARK, 12);
        return b;
    }

    public static Btn ghostButton(String text, Icon icon) {
        Btn b = new Btn(text, icon, null, HOVER, TEXT, 12);
        b.setFont(font(Font.PLAIN, 13f));
        return b;
    }

    public static Btn iconButton(Icon icon, String tooltip, int diameter, Color bg, Color hover) {
        Btn b = new Btn(null, icon, bg, hover, TEXT, diameter);
        b.setToolTipText(tooltip);
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setPreferredSize(new Dimension(diameter, diameter));
        b.setMinimumSize(new Dimension(diameter, diameter));
        b.setMaximumSize(new Dimension(diameter, diameter));
        return b;
    }

    public static class HintField extends JTextField {
        private final String hint;

        public HintField(String hint, String initial) {
            super(initial == null ? "" : initial);
            this.hint = hint;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
            setFont(font(Font.PLAIN, 13.5f));
            setForeground(TEXT);
            setCaretColor(PRIMARY);
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && hint != null) {
                Graphics2D g2 = aa(g);
                g2.setColor(MUTED);
                g2.setFont(getFont());
                Insets in = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, in.left, getHeight() / 2 + fm.getAscent() / 2 - 2);
                g2.dispose();
            }
        }
    }

    public static class HintPassword extends JPasswordField {
        private final String hint;

        public HintPassword(String hint) {
            this.hint = hint;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
            setFont(font(Font.PLAIN, 13.5f));
            setForeground(TEXT);
            setCaretColor(PRIMARY);
            setEchoChar('\u2022');
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getPassword().length == 0 && hint != null) {
                Graphics2D g2 = aa(g);
                g2.setColor(MUTED);
                g2.setFont(getFont());
                Insets in = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, in.left, getHeight() / 2 + fm.getAscent() / 2 - 2);
                g2.dispose();
            }
        }
    }

    public static Card fieldWrap(JComponent field) {
        Card card = new Card(12, CARD_BG, BORDER);
        card.setLayout(new BorderLayout());
        card.add(field, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------- scroll panes ----

    public static void styleScroll(JScrollPane sp) {
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setUI(new FlatScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getHorizontalScrollBar().setUI(new FlatScrollBarUI());
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 9));
        sp.getHorizontalScrollBar().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }

    public static class FlatScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(0xC6CBDA);
            trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) { return zeroButton(); }

        @Override
        protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

        private JButton zeroButton() {
            JButton b = new JButton();
            Dimension d = new Dimension(0, 0);
            b.setPreferredSize(d);
            b.setMinimumSize(d);
            b.setMaximumSize(d);
            b.setBorder(BorderFactory.createEmptyBorder());
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = aa(g);
            g2.setColor(isThumbRollover() ? new Color(0xA8AFC4) : thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, Math.max(1, r.width - 4), Math.max(1, r.height - 4), 8, 8);
            g2.dispose();
        }
    }

    public static JComponent separator() {
        JPanel p = new JPanel();
        p.setBackground(BORDER);
        p.setPreferredSize(new Dimension(1, 1));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return p;
    }

    public static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(font(Font.BOLD, 11f));
        l.setForeground(MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(12, 16, 6, 16));
        return l;
    }
}
