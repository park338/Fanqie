package cn.fanqie.pomodoro.ui;

import java.awt.Color;
import java.awt.Font;

final class UiTheme {
    static final Color BG = new Color(250, 250, 250);
    static final Color PANEL = new Color(255, 255, 255);
    static final Color BORDER = new Color(230, 230, 230);
    static final Color TEXT = new Color(30, 30, 30);
    static final Color MUTED_TEXT = new Color(120, 120, 120);

    static final Color WORK = new Color(231, 76, 60);
    static final Color SHORT_BREAK = new Color(46, 204, 113);
    static final Color LONG_BREAK = new Color(52, 152, 219);

    static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 32);
    static final Font TIMER_FONT = new Font("Monospaced", Font.BOLD, 64);
    static final Font NORMAL_FONT = new Font("SansSerif", Font.PLAIN, 14);
    static final Font SMALL_FONT = new Font("SansSerif", Font.PLAIN, 12);

    private UiTheme() {
    }
}

