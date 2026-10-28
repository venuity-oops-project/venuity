package com.venuity.client.app;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Dimension;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(Main::showWindow);
    }

    private static void showWindow() {
        JFrame frame = new JFrame("Venuity");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setSize(1280, 800);
        frame.setLocationRelativeTo(null);

        JLabel placeholder = new JLabel("Venuity - Java client skeleton", SwingConstants.CENTER);
        placeholder.setFont(placeholder.getFont().deriveFont(24f));
        frame.setContentPane(placeholder);
        frame.setVisible(true);
    }
}
