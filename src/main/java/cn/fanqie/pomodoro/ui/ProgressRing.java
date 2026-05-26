package cn.fanqie.pomodoro.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

final class ProgressRing extends JComponent {
    private double progress;
    private Color color;

    ProgressRing() {
        this.progress = 0.0;
        this.color = UiTheme.WORK;
        setOpaque(false);
    }

    void setProgress(double progress) {
        if (progress < 0.0) {
            progress = 0.0;
        }
        if (progress > 1.0) {
            progress = 1.0;
        }
        this.progress = progress;
        repaint();
    }

    void setColor(Color color) {
        if (color == null) {
            return;
        }
        this.color = color;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.min(getWidth(), getHeight());
            int stroke = Math.max(6, size / 18);
            int pad = stroke + 6;
            int d = size - pad * 2;
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;

            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(240, 240, 240));
            g2.drawArc(x, y, d, d, 90, -360);

            g2.setColor(color);
            int angle = (int) Math.round(progress * 360.0);
            g2.drawArc(x, y, d, d, 90, -angle);
        } finally {
            g2.dispose();
        }
    }
}

