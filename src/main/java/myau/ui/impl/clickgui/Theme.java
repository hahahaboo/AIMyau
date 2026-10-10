package myau.ui.impl.clickgui;

import java.awt.Color;

public final class Theme {
    // 背景
    public static final Color BG           = new Color(16, 16, 18);
    public static final Color SIDEBAR      = new Color(22, 22, 25);
    public static final Color PANEL        = new Color(28, 28, 32);
    public static final Color MODULE       = new Color(34, 34, 39);
    public static final Color MODULE_HOVER = new Color(42, 42, 48);
    public static final Color SETTING_BG   = new Color(24, 24, 28);

    // 強調色
    public static final Color ACCENT       = new Color(120, 90, 255);
    public static final Color ACCENT_DIM   = new Color(90, 70, 200);

    // 文字
    public static final Color TEXT         = new Color(235, 235, 240);
    public static final Color TEXT_DIM     = new Color(145, 145, 155);
    public static final Color TEXT_OFF     = new Color(100, 100, 110);

    // 其他
    public static final Color BORDER       = new Color(50, 50, 56);
    public static final Color SWITCH_OFF   = new Color(55, 55, 62);
    public static final Color SLIDER_BG    = new Color(45, 45, 52);

    // 尺寸
    public static final int   SIDEBAR_W    = 108;
    public static final int   WINDOW_W     = 520;
    public static final int   WINDOW_H     = 340;
    public static final float RADIUS       = 10f;
    public static final float RADIUS_SM    = 6f;
    public static final int   CAT_H        = 30;
    public static final int   MOD_H        = 45;
    public static final int   SETTING_H    = 30;

    public static int rgba(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha))).getRGB();
    }

    public static int rgb(Color c) {
        return c.getRGB();
    }
}
