package com.venuity.client.ui.components;

import com.venuity.client.ui.Theme;

import javax.swing.JButton;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class PrimaryButton extends JButton {

    private boolean accentStyle;

    public PrimaryButton(String text) {
        super(text);
        accentStyle = true;
        setFont(Theme.body().deriveFont(java.awt.Font.BOLD));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        putClientProperty("JButton.buttonType", "roundRect");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                if (isEnabled()) {
                    setBackground(Theme.accentHover());
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                setBackground(Theme.accent());
            }
        });
        applyStyle();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (accentStyle) {
            applyStyle();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = Math.max(size.height, 42);
        return size;
    }

    private void applyStyle() {
        setBackground(Theme.accent());
        setForeground(Theme.accentText());
    }
}
