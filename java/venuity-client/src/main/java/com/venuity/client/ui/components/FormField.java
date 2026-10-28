package com.venuity.client.ui.components;

import com.venuity.client.ui.Theme;
import net.miginfocom.swing.MigLayout;

import javax.swing.JComponent;
import javax.swing.JPanel;

public final class FormField extends JPanel {

    private final JComponent input;
    private final ThemeLabel error = new ThemeLabel(" ");

    public FormField(String labelText, JComponent input) {
        super(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]5[]3[]"));
        this.input = input;
        setOpaque(false);
        ThemeLabel label = new ThemeLabel(labelText);
        label.setFont(Theme.label());
        label.setTone(ThemeLabel.Tone.MUTED);
        label.setLabelFor(input);
        error.setFont(Theme.small());
        error.setTone(ThemeLabel.Tone.DANGER);
        error.setVisible(false);
        add(label, "growx");
        add(input, "growx, h 42!");
        add(error, "growx");
    }

    public JComponent input() {
        return input;
    }

    public void setError(String message) {
        if (message == null || message.isBlank()) {
            clearError();
            return;
        }
        error.setText(message);
        error.setVisible(true);
        input.putClientProperty("JComponent.outline", "error");
        revalidate();
        repaint();
    }

    public void clearError() {
        error.setText(" ");
        error.setVisible(false);
        input.putClientProperty("JComponent.outline", null);
        revalidate();
        repaint();
    }
}
