package com.venuity.client.ui.components;

import com.venuity.client.ui.Theme;
import net.miginfocom.swing.MigLayout;

import javax.swing.border.EmptyBorder;
import java.awt.Dimension;

public final class KpiCard extends GlassPanel {

    private final ThemeLabel value = new ThemeLabel("0");

    public KpiCard(String caption, String initialValue) {
        super(new MigLayout("insets 0, fillx, wrap 1", "[grow]", "[]6[]"));
        setBorder(new EmptyBorder(Theme.PADDING, Theme.PADDING, Theme.PADDING, Theme.PADDING));
        setPreferredSize(new Dimension(200, 104));
        ThemeLabel title = new ThemeLabel(caption);
        title.setTone(ThemeLabel.Tone.MUTED);
        title.setFont(Theme.small());
        value.setText(initialValue);
        value.setFont(Theme.hero().deriveFont(26f));
        add(title, "growx");
        add(value, "growx");
    }

    public void setValue(String text) {
        value.setText(text);
    }

    public String value() {
        return value.getText();
    }
}
