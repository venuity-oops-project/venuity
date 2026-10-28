package com.venuity.client.ui.components;

import com.venuity.client.ui.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

public class GlassPanel extends JPanel {

    private boolean solid;

    public GlassPanel() {
        this(null);
    }

    public GlassPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    public void setSolid(boolean solid) {
        this.solid = solid;
        repaint();
    }

    public boolean isSolid() {
        return solid;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            if (width <= 2 || height <= 2) {
                return;
            }
            paintShadow(g2, width, height);
            g2.setColor(solid ? Theme.surface() : Theme.glassFill());
            g2.fillRoundRect(1, 1, width - 2, height - 2, Theme.RADIUS, Theme.RADIUS);
            g2.setColor(Theme.glassBorder());
            g2.drawRoundRect(1, 1, width - 3, height - 3, Theme.RADIUS, Theme.RADIUS);
        } finally {
            g2.dispose();
        }
        super.paintComponent(graphics);
    }

    private static void paintShadow(Graphics2D g2, int width, int height) {
        Color shadow = Theme.shadow();
        for (int layer = 5; layer >= 1; layer--) {
            int alpha = Math.max(4, shadow.getAlpha() / (layer * 2));
            g2.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), alpha));
            int inset = layer;
            g2.fillRoundRect(inset, inset + 2, width - inset * 2, height - inset * 2,
                    Theme.RADIUS, Theme.RADIUS);
        }
    }
}
