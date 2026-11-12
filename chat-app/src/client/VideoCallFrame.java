package client;

import common.Message;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Cửa sổ cuộc gọi video 1-1.
 *
 * - Hình ảnh: webcam (nếu có thư viện) hoặc chia sẻ màn hình, chụp + nén + gửi trên thread riêng (không làm đơ giao diện).
 * - Âm thanh: micro/loa qua javax.sound.sampled (có sẵn trong JDK), PCM 16kHz mono, gói 100ms.
 * - Tự kết thúc khi: không ai nghe máy sau 30s, hoặc không nhận được gì từ đối phương quá 10s.
 */
public class VideoCallFrame extends JFrame {

    private static final Color BG = new Color(0x141726);
    private static final Color TILE = new Color(0x1F2338);

    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    private static final int AUDIO_CHUNK = 3200;          // 100ms
    private static final int RING_TIMEOUT_MS = 30000;
    private static final int PEER_TIMEOUT_MS = 10000;
    private static final String CAM_OFF = "CAM_OFF";

    private final ChatClient client;
    private final String peer;
    private final String me;
    private final boolean isCaller;

    private final VideoTile localView = new VideoTile("Bạn");
    private final VideoTile remoteView = new VideoTile(null);
    private final JLabel statusLabel = new JLabel();
    private final UI.Btn micBtn;
    private final UI.Btn camBtn;

    private volatile boolean active = false;
    private volatile boolean closed = false;
    private volatile boolean micOn = true;
    private volatile boolean camOn = true;
    private volatile long lastRemote;

    private final Object wakeLock = new Object();
    private boolean wakeFlag = false;
    private final BlockingQueue<byte[]> playQueue = new ArrayBlockingQueue<byte[]>(8);

    private Timer ringTimer;
    private Timer watchdog;
    private Runnable onClosed;

    public VideoCallFrame(ChatClient client, String peer, boolean isCaller) {
        super("Cuộc gọi video với " + peer);
        this.client = client;
        this.peer = peer;
        this.me = client.getUsername();
        this.isCaller = isCaller;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        remoteView.setCaption(peer);
        remoteView.setPlaceholder("Đang chờ hình ảnh...");

        JPanel videos = new JPanel(new GridLayout(1, 2, 14, 0));
        videos.setBackground(BG);
        videos.setBorder(BorderFactory.createEmptyBorder(16, 16, 10, 16));
        videos.add(localView);
        videos.add(remoteView);
        add(videos, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(BG);
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 20, 18, 20));

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statusRow.setOpaque(false);
        statusRow.add(new JLabel(UI.avatarCached(peer, 34, false, false)));
        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        JLabel nameLabel = UI.label(peer, Font.BOLD, 14f, Color.WHITE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setFont(UI.font(Font.PLAIN, 12f));
        statusLabel.setForeground(new Color(0xA7AEC6));
        statusLabel.setText(isCaller ? "Đang gọi " + peer + "..." : "Đang kết nối...");
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textCol.add(nameLabel);
        textCol.add(Box.createVerticalStrut(2));
        textCol.add(statusLabel);
        statusRow.add(textCol);
        bottom.add(statusRow, BorderLayout.WEST);

        Color neutral = new Color(0x39405C);
        Color neutralHover = new Color(0x4A5275);
        micBtn = new UI.Btn("Tắt mic", null, neutral, neutralHover, Color.WHITE, 22);
        camBtn = new UI.Btn("Tắt camera", UI.glyph("video", 16, Color.WHITE), neutral, neutralHover, Color.WHITE, 22);
        UI.Btn endBtn = new UI.Btn("Kết thúc", UI.glyph("phone", 16, Color.WHITE),
                UI.DANGER, new Color(0xBE123C), Color.WHITE, 22);
        for (UI.Btn b : new UI.Btn[]{micBtn, camBtn, endBtn}) {
            b.setBorder(BorderFactory.createEmptyBorder(11, 20, 11, 20));
        }
        micBtn.addActionListener(e -> toggleMic());
        camBtn.addActionListener(e -> toggleCamera());
        endBtn.addActionListener(e -> hangUp(true));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(micBtn);
        right.add(camBtn);
        right.add(endBtn);
        bottom.add(right, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        setSize(860, 460);
        setLocationRelativeTo(null);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                hangUp(true);
            }
        });

        if (isCaller) {
            // Không ai nghe máy sau 30s thì tự huỷ (và báo cho bên kia tắt hộp thoại "Cuộc gọi đến")
            ringTimer = new Timer(RING_TIMEOUT_MS, e -> {
                if (!active && !closed) {
                    client.send(new Message(Message.Type.VIDEO_CALL_END, me, peer, null));
                    onRemoteEnded("Không có phản hồi từ " + peer);
                }
            });
            ringTimer.setRepeats(false);
            ringTimer.start();
        } else {
            startActive();
        }
    }

    public void setOnClosed(Runnable r) {
        this.onClosed = r;
    }

    /** Bắt đầu gửi hình + tiếng (bên nhận gọi: ngay khi mở cửa sổ; bên gọi: khi nhận VIDEO_CALL_ACCEPT). */
    public void startActive() {
        if (active || closed) return;
        active = true;
        lastRemote = System.currentTimeMillis();
        if (ringTimer != null) ringTimer.stop();
        statusLabel.setText("Đang trong cuộc gọi với " + peer);

        startThread(new Runnable() { public void run() { videoLoop(); } }, "call-video");
        startThread(new Runnable() { public void run() { audioCaptureLoop(); } }, "call-mic");
        startThread(new Runnable() { public void run() { playbackLoop(); } }, "call-speaker");

        watchdog = new Timer(2000, e -> {
            if (!closed && System.currentTimeMillis() - lastRemote > PEER_TIMEOUT_MS) {
                client.send(new Message(Message.Type.VIDEO_CALL_END, me, peer, null));
                onRemoteEnded("Mất kết nối với " + peer);
            }
        });
        watchdog.start();
    }

    private void startThread(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ gửi hình ảnh

    private void videoLoop() {
        final CameraSource src = CameraSource.open();
        final String caption = src.isWebcam() ? "Bạn (camera)" : "Bạn (chia sẻ màn hình)";
        final String note = src.getNote();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                localView.setCaption(caption);
                if (!closed && note.length() > 0) statusLabel.setText(note);
            }
        });
        final long interval = src.isWebcam() ? 125 : 200; // ~8 / ~5 khung hình mỗi giây
        try {
            while (active && !closed) {
                long t0 = System.currentTimeMillis();
                try {
                    if (camOn) {
                        BufferedImage raw = src.grab();
                        if (raw != null) {
                            BufferedImage small = fit(raw, 400, 300);
                            final BufferedImage preview = src.isWebcam() ? mirror(small) : small;
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() { if (camOn) localView.setFrame(preview); }
                            });
                            Message m = new Message(Message.Type.VIDEO_FRAME, me, peer, null);
                            m.setFileData(toJpeg(small, 0.6f));
                            client.send(m);
                        }
                    } else {
                        // Camera tắt: gửi tín hiệu nhỏ mỗi giây để bên kia biết và không tưởng mất kết nối
                        client.send(new Message(Message.Type.VIDEO_FRAME, me, peer, CAM_OFF));
                    }
                } catch (Exception ignored) {
                    // bỏ qua khung hình lỗi
                }
                sleepOrWake((camOn ? interval : 1000) - (System.currentTimeMillis() - t0));
            }
        } finally {
            src.close();
        }
    }

    private void sleepOrWake(long ms) {
        if (ms <= 0) return;
        synchronized (wakeLock) {
            if (!wakeFlag) {
                try { wakeLock.wait(ms); } catch (InterruptedException ignored) { }
            }
            wakeFlag = false;
        }
    }

    private void wakeVideoThread() {
        synchronized (wakeLock) {
            wakeFlag = true;
            wakeLock.notifyAll();
        }
    }

    /** Thu nhỏ vừa khung w x h, giữ nguyên tỉ lệ (không phóng to). */
    private static BufferedImage fit(BufferedImage src, int maxW, int maxH) {
        double s = Math.min(1.0, Math.min(maxW / (double) src.getWidth(), maxH / (double) src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * s));
        int h = Math.max(1, (int) Math.round(src.getHeight() * s));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage mirror(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, src.getWidth(), 0, -src.getWidth(), src.getHeight(), null);
        g.dispose();
        return out;
    }

    private static byte[] toJpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(baos);
        try {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
            ios.flush();
        } finally {
            writer.dispose();
            ios.close();
        }
        return baos.toByteArray();
    }

    // ------------------------------------------------------------------ âm thanh

    private void audioCaptureLoop() {
        TargetDataLine mic = null;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                postNote("Không tìm thấy micro - chỉ gửi hình ảnh");
                return;
            }
            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(AUDIO_FORMAT, AUDIO_CHUNK * 4);
            mic.start();
            byte[] buf = new byte[AUDIO_CHUNK];
            while (active && !closed) {
                int n = mic.read(buf, 0, buf.length);
                if (n <= 0 || !micOn) continue; // tắt mic: vẫn đọc để xả bộ đệm nhưng không gửi
                Message m = new Message(Message.Type.AUDIO_FRAME, me, peer, null);
                m.setFileData(Arrays.copyOf(buf, n));
                client.send(m);
            }
        } catch (Exception e) {
            postNote("Không dùng được micro: " + e.getMessage());
        } finally {
            if (mic != null) {
                mic.stop();
                mic.close();
            }
        }
    }

    private void playbackLoop() {
        SourceDataLine speaker = null;
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
            if (!AudioSystem.isLineSupported(info)) return;
            speaker = (SourceDataLine) AudioSystem.getLine(info);
            speaker.open(AUDIO_FORMAT, AUDIO_CHUNK * 4);
            speaker.start();
            while (active && !closed) {
                byte[] chunk = playQueue.poll(200, TimeUnit.MILLISECONDS);
                if (chunk != null) speaker.write(chunk, 0, chunk.length);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            postNote("Không dùng được loa: " + e.getMessage());
        } finally {
            if (speaker != null) {
                speaker.stop();
                speaker.close();
            }
        }
    }

    private void postNote(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { if (!closed) statusLabel.setText(text); }
        });
    }

    // ------------------------------------------------------------------ nút điều khiển

    private void toggleMic() {
        micOn = !micOn;
        micBtn.setText(micOn ? "Tắt mic" : "Bật mic");
    }

    private void toggleCamera() {
        camOn = !camOn;
        camBtn.setText(camOn ? "Tắt camera" : "Bật camera");
        if (!camOn) {
            localView.setPlaceholder("Camera đã tắt");
            localView.setFrame(null);
        }
        wakeVideoThread(); // gửi trạng thái mới ngay, không chờ hết chu kỳ ngủ
    }

    // ------------------------------------------------------------------ nhận từ đối phương (gọi trên EDT)

    public void onRemoteFrame(Message m) {
        if (closed) return;
        lastRemote = System.currentTimeMillis();
        if (CAM_OFF.equals(m.getContent()) || m.getFileData() == null) {
            remoteView.setPlaceholder(peer + " đã tắt camera");
            remoteView.setFrame(null);
            return;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(m.getFileData()));
            if (img != null) remoteView.setFrame(img);
        } catch (IOException ignored) {
        }
    }

    public void onRemoteAudio(byte[] pcm) {
        if (closed) return;
        lastRemote = System.currentTimeMillis();
        if (pcm == null || !active) return;
        if (!playQueue.offer(pcm)) { // đầy: bỏ gói cũ nhất để không bị trễ tiếng dần
            playQueue.poll();
            playQueue.offer(pcm);
        }
    }

    public void onRemoteEnded(String reason) {
        if (closed) return;
        statusLabel.setText(reason);
        micBtn.setEnabled(false);
        camBtn.setEnabled(false);
        stopMedia();
        Timer closeTimer = new Timer(1400, e -> dispose());
        closeTimer.setRepeats(false);
        closeTimer.start();
    }

    private void hangUp(boolean notifyPeer) {
        if (closed) return;
        if (notifyPeer) {
            client.send(new Message(Message.Type.VIDEO_CALL_END, me, peer, null));
        }
        dispose();
    }

    private void stopMedia() {
        active = false;
        if (ringTimer != null) ringTimer.stop();
        if (watchdog != null) watchdog.stop();
        playQueue.clear();
        wakeVideoThread(); // các thread tự thoát và tự đóng camera / micro / loa
    }

    @Override
    public void dispose() {
        boolean first = !closed;
        stopMedia();
        closed = true;
        if (first && onClosed != null) onClosed.run();
        super.dispose();
    }

    // ------------------------------------------------------------------ khung hiển thị

    private static class VideoTile extends JPanel {
        private String caption;
        private String placeholder = "Chưa có hình ảnh";
        private BufferedImage frame;

        VideoTile(String caption) {
            this.caption = caption;
            setOpaque(false);
        }

        void setCaption(String caption) {
            this.caption = caption;
            repaint();
        }

        void setPlaceholder(String text) {
            this.placeholder = text;
            repaint();
        }

        void setFrame(BufferedImage img) {
            this.frame = img;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = UI.aa(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(TILE);
            g2.fillRoundRect(0, 0, w, h, 18, 18);

            BufferedImage f = frame;
            if (f != null) {
                Shape old = g2.getClip();
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, 18, 18));
                double s = Math.min(w / (double) f.getWidth(), h / (double) f.getHeight());
                int dw = (int) (f.getWidth() * s);
                int dh = (int) (f.getHeight() * s);
                g2.drawImage(f, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                g2.setClip(old);
            } else {
                g2.setColor(new Color(0x8087A6));
                g2.setFont(UI.font(Font.PLAIN, 13f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholder, (w - fm.stringWidth(placeholder)) / 2, h / 2);
            }

            if (caption != null) {
                g2.setFont(UI.font(Font.BOLD, 11.5f));
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(caption);
                int bw = tw + 18, bh = 24;
                g2.setColor(new Color(0, 0, 0, 130));
                g2.fillRoundRect(10, h - bh - 10, bw, bh, bh, bh);
                g2.setColor(Color.WHITE);
                g2.drawString(caption, 19, h - bh - 10 + fm.getAscent() + 4);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
