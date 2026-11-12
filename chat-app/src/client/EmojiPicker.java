package client;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class EmojiPicker {

    private static final String[] EMOJIS = {
            "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0A", "\uD83D\uDE0D",
            "\uD83D\uDE0E", "\uD83D\uDE1C", "\uD83D\uDE14", "\uD83D\uDE22",
            "\uD83D\uDE2D", "\uD83D\uDE21", "\uD83D\uDE31", "\uD83E\uDD14",
            "\uD83D\uDE0C", "\uD83D\uDE34", "\uD83E\uDD2F", "\uD83D\uDE07",
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4B", "\uD83D\uDE4F",
            "\uD83D\uDC4F", "\uD83D\uDCAA", "\u270C\uFE0F", "\uD83E\uDD1D",
            "\u2764\uFE0F", "\uD83D\uDD25", "\uD83C\uDF89", "\u2B50",
            "\uD83C\uDF1F", "\uD83C\uDF80", "\uD83C\uDF55", "\u2615"
    };

    private static final String EMOJI_FAMILY = pickEmojiFamily();

    private static String pickEmojiFamily() {
        String[] prefs = {"Segoe UI Emoji", "Noto Color Emoji", "Apple Color Emoji",
                "Noto Emoji", "Segoe UI Symbol", "Dialog"};
        try {
            Set<String> available = new HashSet<String>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (int i = 0; i < prefs.length; i++) {
                if (available.contains(prefs[i])) return prefs[i];
            }
        } catch (Exception ignored) {
        }
        return "Dialog";
    }

    public static void show(Component invoker, final Consumer<String> onPick) {
        final JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(UI.BORDER));
        popup.setBackground(UI.CARD_BG);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(UI.CARD_BG);
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        JLabel title = UI.label("Biểu cảm", Font.BOLD, 11f, UI.MUTED);
        title.setBorder(BorderFactory.createEmptyBorder(0, 2, 8, 0));
        content.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(4, 8, 2, 2));
        grid.setBackground(UI.CARD_BG);
        for (int i = 0; i < EMOJIS.length; i++) {
            final String emoji = EMOJIS[i];
            UI.Btn btn = new UI.Btn(emoji, null, null, UI.HOVER, UI.TEXT, 10);
            btn.setFont(new Font(EMOJI_FAMILY, Font.PLAIN, 20));
            btn.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            btn.setPreferredSize(new Dimension(38, 38));
            btn.addActionListener(e -> {
                onPick.accept(emoji);
                popup.setVisible(false);
            });
            grid.add(btn);
        }
        content.add(grid, BorderLayout.CENTER);

        popup.add(content);
        popup.show(invoker, 0, -content.getPreferredSize().height - 14);
    }
}
