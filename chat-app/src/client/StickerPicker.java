package client;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class StickerPicker {

    private static final Map<String, Color> STICKERS = new LinkedHashMap<String, Color>();

    static {
        STICKERS.put("Like", new Color(0x2F80ED));
        STICKERS.put("Love", new Color(0xE74C6F));
        STICKERS.put("Haha", new Color(0xF2C037));
        STICKERS.put("Wow", new Color(0x36B5D8));
        STICKERS.put("Sad", new Color(0x8B7BD8));
        STICKERS.put("Angry", new Color(0xE8743B));
    }

    public static void show(Component invoker, final BiConsumer<String, byte[]> onPick) {
        final JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(UI.BORDER));
        popup.setBackground(UI.CARD_BG);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(UI.CARD_BG);
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        JLabel title = UI.label("Nhãn dán", Font.BOLD, 11f, UI.MUTED);
        title.setBorder(BorderFactory.createEmptyBorder(0, 2, 8, 0));
        content.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 3, 6, 6));
        grid.setBackground(UI.CARD_BG);
        for (Map.Entry<String, Color> e : STICKERS.entrySet()) {
            final String id = e.getKey();
            final byte[] png = renderSticker(id, e.getValue());
            UI.Btn btn = new UI.Btn(null, new ImageIcon(scale(png, 54)), null, UI.HOVER, UI.TEXT, 12);
            btn.setToolTipText(id);
            btn.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            btn.setPreferredSize(new Dimension(66, 66));
            btn.addActionListener(ev -> {
                onPick.accept(id, png);
                popup.setVisible(false);
            });
            grid.add(btn);
        }
        content.add(grid, BorderLayout.CENTER);

        popup.add(content);
        popup.show(invoker, 0, -content.getPreferredSize().height - 14);
    }

    private static Image scale(byte[] png, int size) {
        return new ImageIcon(png).getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
    }

    public static byte[] renderSticker(String label, Color color) {
        int size = 128;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = UI.aa(img.getGraphics());

        Color light = new Color(
                Math.min(255, color.getRed() + 40),
                Math.min(255, color.getGreen() + 40),
                Math.min(255, color.getBlue() + 40));
        g.setPaint(new GradientPaint(0, 0, light, size, size, color));
        g.fill(new Ellipse2D.Float(4, 4, size - 8, size - 8));

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if ("Like".equals(label)) {
            GeneralPath thumb = new GeneralPath();
            thumb.moveTo(44, 108);
            thumb.lineTo(44, 62);
            thumb.lineTo(58, 62);
            thumb.lineTo(70, 24);
            thumb.curveTo(84, 22, 90, 34, 84, 50);
            thumb.lineTo(78, 62);
            thumb.lineTo(102, 62);
            thumb.curveTo(112, 62, 112, 76, 104, 78);
            thumb.curveTo(110, 84, 106, 94, 98, 96);
            thumb.curveTo(102, 104, 94, 110, 86, 108);
            thumb.closePath();
            g.fill(thumb);
            g.fill(new java.awt.geom.RoundRectangle2D.Float(22, 62, 20, 48, 8, 8));
        } else if ("Love".equals(label)) {
            GeneralPath heart = new GeneralPath();
            heart.moveTo(64, 104);
            heart.curveTo(22, 78, 20, 52, 34, 40);
            heart.curveTo(48, 28, 62, 38, 64, 48);
            heart.curveTo(66, 38, 80, 28, 94, 40);
            heart.curveTo(108, 52, 106, 78, 64, 104);
            heart.closePath();
            g.fill(heart);
        } else if ("Haha".equals(label)) {
            g.fill(new Ellipse2D.Float(38, 44, 16, 8));
            g.fill(new Ellipse2D.Float(74, 44, 16, 8));
            GeneralPath mouth = new GeneralPath();
            mouth.moveTo(36, 68);
            mouth.lineTo(92, 68);
            mouth.curveTo(92, 96, 36, 96, 36, 68);
            mouth.closePath();
            g.fill(mouth);
        } else if ("Wow".equals(label)) {
            g.fill(new Ellipse2D.Float(38, 40, 14, 18));
            g.fill(new Ellipse2D.Float(76, 40, 14, 18));
            g.fill(new Ellipse2D.Float(52, 70, 24, 32));
        } else if ("Sad".equals(label)) {
            g.fill(new Ellipse2D.Float(38, 44, 14, 16));
            g.fill(new Ellipse2D.Float(76, 44, 14, 16));
            g.draw(new Arc2D.Float(40, 78, 48, 32, 20, 140, Arc2D.OPEN));
            g.fill(new Ellipse2D.Float(36, 62, 10, 16));
        } else if ("Angry".equals(label)) {
            g.draw(new java.awt.geom.Line2D.Float(34, 38, 54, 48));
            g.draw(new java.awt.geom.Line2D.Float(94, 38, 74, 48));
            g.fill(new Ellipse2D.Float(40, 54, 14, 14));
            g.fill(new Ellipse2D.Float(74, 54, 14, 14));
            g.draw(new Arc2D.Float(40, 80, 48, 28, 20, 140, Arc2D.OPEN));
        } else {
            g.setFont(UI.font(Font.BOLD, 22f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, (size - fm.stringWidth(label)) / 2, size / 2 + fm.getAscent() / 2 - 2);
        }

        g.dispose();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
