package com.venuity.client.ui.components;

import com.venuity.client.ui.Theme;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class NavButton extends JButton {

    private boolean active;
    private boolean hovered;

    public NavButton(String text) {
        super(text);
        setHorizontalAlignment(LEFT);
        setFont(Theme.body());
        setBorder(new EmptyBorder(11, 16, 11, 16));
        setContentAreaFilled(false);
        setOpaque(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                repaint();
            }
        });
        applyForeground();
    }

    public void setActive(boolean active) {
        this.active = active;
        applyForeground();
        repaint();
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (getFont() != null) {
            setFont(Theme.body());
        }
        applyForeground();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active) {
                g2.setColor(withAlpha(Theme.accent(), 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(Theme.accent());
                g2.fillRoundRect(2, 8, 3, Math.max(8, getHeight() - 16), 3, 3);
            } else if (hovered) {
                g2.setColor(Theme.selection());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(graphics);
    }

    private void applyForeground() {
        setForeground(active ? Theme.accent() : Theme.muted());
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
