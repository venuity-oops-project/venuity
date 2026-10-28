package com.venuity.client.ui.components;

import com.venuity.client.ui.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;

public final class AppBackground extends JPanel {

    public AppBackground(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            g2.setPaint(new GradientPaint(0, 0, Theme.bgStart(), width, height, Theme.bgEnd()));
            g2.fillRect(0, 0, width, height);
            paintGlow(g2, width, height);
        } finally {
            g2.dispose();
        }
        super.paintComponent(graphics);
    }

    private void paintGlow(Graphics2D g2, int width, int height) {
        float radius = Math.max(width, height) * 0.65f;
        Color glow = Theme.bgGlow();
        Color transparent = new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 0);
        Point2D center = new Point2D.Float(width * 0.15f, height * 0.08f);
        g2.setPaint(new RadialGradientPaint(center, radius, new float[]{0f, 1f},
                new Color[]{withAlpha(glow, 90), transparent}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g2.fillRect(0, 0, width, height);

        Point2D accentCenter = new Point2D.Float(width * 0.92f, height * 0.95f);
        Color accent = Theme.accent();
        g2.setPaint(new RadialGradientPaint(accentCenter, radius * 0.8f, new float[]{0f, 1f},
                new Color[]{withAlpha(accent, 34), withAlpha(accent, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g2.fillRect(0, 0, width, height);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
