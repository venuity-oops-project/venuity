package com.venuity.client.ui.components;

import com.venuity.client.ui.Theme;
import net.miginfocom.swing.MigLayout;

import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;

public final class BusyOverlay extends JPanel {

    private final ThemeLabel message = new ThemeLabel("Working...");
    private final JProgressBar progress = new JProgressBar();

    public BusyOverlay() {
        super(new GridBagLayout());
        setOpaque(false);
        setFocusable(false);
        GlassPanel card = new GlassPanel(new MigLayout("insets 0, fillx, wrap 1, align center", "[220!]",
                "[]12[]"));
        card.setSolid(true);
        card.setBorder(new EmptyBorder(24, 28, 24, 28));
        message.setFont(Theme.body());
        message.setHorizontalAlignment(ThemeLabel.CENTER);
        progress.setIndeterminate(true);
        progress.setBorderPainted(false);
        progress.setPreferredSize(new java.awt.Dimension(220, 6));
        progress.setForeground(Theme.accent());
        progress.setBackground(Theme.border());
        card.add(message, "growx");
        card.add(progress, "growx, h 6!");
        add(card);
        setVisible(false);
    }

    public void showBusy(String text) {
        message.setText(text);
        setVisible(true);
        repaint();
    }

    public void hideBusy() {
        setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = Theme.bgStart();
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 190));
            g2.fillRect(0, 0, getWidth(), getHeight());
        } finally {
            g2.dispose();
        }
        super.paintComponent(graphics);
    }
}
