package com.venuity.client.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.venuity.client.config.ThemeMode;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

public final class Theme {

    public static final int RADIUS = 16;
    public static final int GAP = 16;
    public static final int PADDING = 20;

    private static final String FONT_FAMILY = "Segoe UI";
    private static final Palette DARK = new Palette(
            new Color(8, 15, 29),
            new Color(15, 27, 47),
            new Color(29, 78, 120),
            new Color(17, 28, 45),
            new Color(255, 255, 255, 18),
            new Color(255, 255, 255, 46),
            new Color(243, 247, 252),
            new Color(148, 163, 184),
            new Color(45, 212, 191),
            new Color(94, 234, 212),
            new Color(4, 47, 46),
            new Color(34, 197, 94),
            new Color(245, 158, 11),
            new Color(248, 113, 113),
            new Color(36, 52, 76),
            new Color(12, 21, 36),
            new Color(255, 255, 255, 12),
            new Color(0, 0, 0, 90),
            new Color(255, 255, 255, 8));
    private static final Palette LIGHT = new Palette(
            new Color(235, 242, 249),
            new Color(250, 252, 255),
            new Color(177, 209, 238),
            new Color(255, 255, 255),
            new Color(255, 255, 255, 170),
            new Color(15, 23, 42, 35),
            new Color(15, 23, 42),
            new Color(100, 116, 139),
            new Color(15, 118, 110),
            new Color(13, 148, 136),
            new Color(255, 255, 255),
            new Color(21, 128, 61),
            new Color(180, 83, 9),
            new Color(220, 38, 38),
            new Color(216, 226, 237),
            new Color(246, 249, 253),
            new Color(15, 23, 42, 8),
            new Color(15, 23, 42, 34),
            new Color(15, 23, 42, 5));

    private static Palette palette = DARK;
    private static ThemeMode mode = ThemeMode.DARK;

    private Theme() {
    }

    public static void install(ThemeMode value) {
        mode = value == null ? ThemeMode.DARK : value;
        palette = mode == ThemeMode.DARK ? DARK : LIGHT;
        UIManager.put("defaultFont", font(14f, Font.PLAIN));
        FlatLaf.setup(mode == ThemeMode.DARK ? new FlatDarkLaf() : new FlatLightLaf());
        installDefaults();
        FlatLaf.updateUI();
    }

    public static ThemeMode toggle() {
        install(mode == ThemeMode.DARK ? ThemeMode.LIGHT : ThemeMode.DARK);
        return mode;
    }

    public static ThemeMode mode() {
        return mode;
    }

    public static Color bgStart() {
        return palette.bgStart;
    }

    public static Color bgEnd() {
        return palette.bgEnd;
    }

    public static Color bgGlow() {
        return palette.bgGlow;
    }

    public static Color surface() {
        return palette.surface;
    }

    public static Color glassFill() {
        return palette.glassFill;
    }

    public static Color glassBorder() {
        return palette.glassBorder;
    }

    public static Color text() {
        return palette.text;
    }

    public static Color muted() {
        return palette.muted;
    }

    public static Color accent() {
        return palette.accent;
    }

    public static Color accentHover() {
        return palette.accentHover;
    }

    public static Color accentText() {
        return palette.accentText;
    }

    public static Color success() {
        return palette.success;
    }

    public static Color warning() {
        return palette.warning;
    }

    public static Color danger() {
        return palette.danger;
    }

    public static Color border() {
        return palette.border;
    }

    public static Color sidebar() {
        return palette.sidebar;
    }

    public static Color selection() {
        return palette.selection;
    }

    public static Color shadow() {
        return palette.shadow;
    }

    public static Color fieldFill() {
        return palette.fieldFill;
    }

    public static Font font(float size, int style) {
        return new Font(FONT_FAMILY, style, Math.round(size));
    }

    public static Font small() {
        return font(12f, Font.PLAIN);
    }

    public static Font body() {
        return font(14f, Font.PLAIN);
    }

    public static Font label() {
        return font(13f, Font.BOLD);
    }

    public static Font heading() {
        return font(20f, Font.BOLD);
    }

    public static Font hero() {
        return font(30f, Font.BOLD);
    }

    private static void installDefaults() {
        UIManager.put("Component.arc", RADIUS);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.focusColor", accent());
        UIManager.put("Component.borderColor", border());
        UIManager.put("Component.disabledBorderColor", border());
        UIManager.put("Panel.background", bgEnd());
        UIManager.put("Label.foreground", text());
        UIManager.put("Button.margin", new Insets(8, 16, 8, 16));
        UIManager.put("Button.default.background", accent());
        UIManager.put("Button.default.foreground", accentText());
        UIManager.put("Button.default.hoverBackground", accentHover());
        UIManager.put("Button.default.pressedBackground", accentHover());
        UIManager.put("TextField.background", fieldFill());
        UIManager.put("PasswordField.background", fieldFill());
        UIManager.put("TextField.foreground", text());
        UIManager.put("PasswordField.foreground", text());
        UIManager.put("TextField.placeholderForeground", muted());
        UIManager.put("PasswordField.placeholderForeground", muted());
        UIManager.put("TextField.selectionBackground", selection());
        UIManager.put("PasswordField.selectionBackground", selection());
        UIManager.put("Table.background", surface());
        UIManager.put("Table.foreground", text());
        UIManager.put("Table.selectionBackground", selection());
        UIManager.put("Table.selectionForeground", text());
        UIManager.put("Table.gridColor", border());
        UIManager.put("Table.rowHeight", 36);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("TableHeader.background", surface());
        UIManager.put("TableHeader.foreground", muted());
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ToolTip.background", surface());
        UIManager.put("ToolTip.foreground", text());
        UIManager.put("ToolTip.border", border());
        UIManager.put("PopupMenu.background", surface());
        UIManager.put("PopupMenu.foreground", text());
        UIManager.put("OptionPane.background", surface());
        UIManager.put("OptionPane.messageForeground", text());
        UIManager.put("ProgressBar.foreground", accent());
        UIManager.put("TitlePane.foreground", text());
        UIManager.put("TitlePane.background", bgStart());
    }

    private record Palette(
            Color bgStart,
            Color bgEnd,
            Color bgGlow,
            Color surface,
            Color glassFill,
            Color glassBorder,
            Color text,
            Color muted,
            Color accent,
            Color accentHover,
            Color accentText,
            Color success,
            Color warning,
            Color danger,
            Color border,
            Color sidebar,
            Color selection,
            Color shadow,
            Color fieldFill
    ) {
    }
}
