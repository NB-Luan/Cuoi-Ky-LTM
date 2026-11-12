package client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

/**
 * Nguồn hình ảnh cho cuộc gọi video.
 *
 * Ưu tiên webcam thật qua thư viện "Webcam Capture" (com.github.sarxos.webcam.Webcam). Thư viện được gọi
 * bằng reflection nên project vẫn build được khi CHƯA thêm jar; nếu không có thư viện / không có camera
 * thì tự động chuyển sang chia sẻ màn hình (java.awt.Robot) và ghi rõ lý do trong {@link #getNote()}.
 */
final class CameraSource {

    private Object webcam;
    private Method mGetImage;
    private Method mClose;
    private Robot robot;
    private boolean webcamMode;
    private String note = "";

    static CameraSource open() {
        CameraSource s = new CameraSource();
        s.tryWebcam();
        if (!s.webcamMode) s.tryScreen();
        return s;
    }

    private void tryWebcam() {
        try {
            Class<?> c = Class.forName("com.github.sarxos.webcam.Webcam");
            Object cam = c.getMethod("getDefault").invoke(null);
            if (cam == null) {
                note = "Không tìm thấy camera - đang chia sẻ màn hình";
                return;
            }
            try { // ưu tiên 640x480 nếu camera hỗ trợ (phải đặt trước khi open)
                Dimension vga = new Dimension(640, 480);
                Dimension[] sizes = (Dimension[]) c.getMethod("getViewSizes").invoke(cam);
                for (Dimension d : sizes) {
                    if (d.equals(vga)) {
                        c.getMethod("setViewSize", Dimension.class).invoke(cam, vga);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            Object ok = c.getMethod("open").invoke(cam);
            if (Boolean.FALSE.equals(ok)) {
                note = "Không mở được camera (đang bị ứng dụng khác dùng?) - đang chia sẻ màn hình";
                return;
            }
            webcam = cam;
            mGetImage = c.getMethod("getImage");
            mClose = c.getMethod("close");
            webcamMode = true;
        } catch (ClassNotFoundException e) {
            note = "Chưa thêm thư viện webcam-capture nên đang chia sẻ màn hình";
        } catch (Throwable t) {
            Throwable r = t.getCause() != null ? t.getCause() : t;
            note = "Lỗi camera (" + r.getClass().getSimpleName() + ") - đang chia sẻ màn hình";
        }
    }

    private void tryScreen() {
        try {
            robot = new Robot();
        } catch (Exception e) {
            note = "Không truy cập được camera lẫn màn hình: " + e.getMessage();
        }
    }

    boolean isWebcam() { return webcamMode; }

    /** Lý do phải dùng chế độ dự phòng (rỗng nếu đang dùng webcam bình thường). */
    String getNote() { return note; }

    /** Lấy 1 khung hình; trả về null nếu lỗi. Gọi từ đúng 1 thread. */
    BufferedImage grab() {
        try {
            if (webcamMode) return (BufferedImage) mGetImage.invoke(webcam);
            if (robot != null) {
                return robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    void close() {
        if (webcamMode && mClose != null) {
            try { mClose.invoke(webcam); } catch (Throwable ignored) { }
        }
        webcamMode = false;
        robot = null;
    }
}
