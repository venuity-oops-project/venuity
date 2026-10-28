package com.venuity.client.ui.components;

import com.venuity.client.ui.Theme;
import net.miginfocom.swing.MigLayout;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class StatusIndicator extends JPanel {

    public enum Status {
        CHECKING,
        ONLINE,
        OFFLINE
    }

    private final StatusDot dot = new StatusDot();
    private final ThemeLabel label = new ThemeLabel("Checking");
    private Status status = Status.CHECKING;

    public StatusIndicator() {
        super(new MigLayout("insets 0", "[10!]6[]", "[center]"));
        setOpaque(false);
        label.setFont(Theme.small());
        label.setTone(ThemeLabel.Tone.MUTED);
        add(dot, "w 10!, h 10!");
        add(label);
    }

    public void setStatus(Status status, String text) {
        this.status = status;
        label.setText(text);
        label.setTone(switch (status) {
            case CHECKING -> ThemeLabel.Tone.WARNING;
            case ONLINE -> ThemeLabel.Tone.SUCCESS;
            case OFFLINE -> ThemeLabel.Tone.DANGER;
        });
        dot.repaint();
    }

    public Status status() {
        return status;
    }

    private final class StatusDot extends JComponent {

        private StatusDot() {
            setOpaque(false);
            setPreferredSize(new Dimension(10, 10));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = switch (status) {
                    case CHECKING -> Theme.warning();
                    case ONLINE -> Theme.success();
                    case OFFLINE -> Theme.danger();
                };
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 48));
                g2.fillOval(0, 0, 10, 10);
                g2.setColor(color);
                g2.fillOval(2, 2, 6, 6);
            } finally {
                g2.dispose();
            }
        }
    }
}
