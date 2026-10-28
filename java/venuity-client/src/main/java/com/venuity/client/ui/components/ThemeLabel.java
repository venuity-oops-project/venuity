package com.venuity.client.ui.components;

import com.venuity.client.ui.Theme;

import javax.swing.JLabel;

public final class ThemeLabel extends JLabel {

    public enum Tone {
        TEXT,
        MUTED,
        ACCENT,
        SUCCESS,
        WARNING,
        DANGER,
        ACCENT_TEXT
    }

    private Tone tone = Tone.TEXT;

    public ThemeLabel(String text) {
        super(text);
        applyTone();
    }

    public void setTone(Tone tone) {
        this.tone = tone == null ? Tone.TEXT : tone;
        applyTone();
    }

    public Tone tone() {
        return tone;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (tone != null) {
            applyTone();
        }
    }

    private void applyTone() {
        setForeground(switch (tone) {
            case TEXT -> Theme.text();
            case MUTED -> Theme.muted();
            case ACCENT -> Theme.accent();
            case SUCCESS -> Theme.success();
            case WARNING -> Theme.warning();
            case DANGER -> Theme.danger();
            case ACCENT_TEXT -> Theme.accentText();
        });
    }
}
