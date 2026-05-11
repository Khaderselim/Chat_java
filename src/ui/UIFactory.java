package ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class UIFactory {

    private UIFactory() {}

    public static void setupLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        UIManager.put("Panel.background",    AppColors.BG_DARK);
        UIManager.put("ScrollPane.background", AppColors.BG_DARK);
        UIManager.put("ScrollBar.thumb",     new Color(60, 62, 90));
        UIManager.put("ScrollBar.track",     AppColors.BG_DARK);
        UIManager.put("OptionPane.background", AppColors.BG_SIDEBAR);
        UIManager.put("OptionPane.messageForeground", AppColors.TEXT_PRIMARY);
    }

    public static JButton createAccentButton(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? bg.darker()
                        : getModel().isRollover()  ? bg.brighter() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setBorder(new EmptyBorder(9, 18, 9, 18));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public static JButton createIconButton(String icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        btn.setForeground(AppColors.TEXT_SECONDARY);
        btn.setBackground(AppColors.BG_SIDEBAR);
        btn.setBorder(new EmptyBorder(4, 8, 4, 8));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setForeground(AppColors.ACCENT); }
            public void mouseExited(MouseEvent e)  { btn.setForeground(AppColors.TEXT_SECONDARY); }
        });
        return btn;
    }

    public static JButton createSmallButton(String text, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setForeground(fg);
        btn.setBackground(new Color(20, 21, 32));
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(fg.darker(), 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public static void styleTextField(JTextField f, String placeholder) {
        f.setBackground(AppColors.BG_INPUT);
        f.setForeground(AppColors.TEXT_PRIMARY);
        f.setCaretColor(AppColors.ACCENT);
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (f.getText().equals(placeholder)) {
                    f.setText("");
                    f.setForeground(AppColors.TEXT_PRIMARY);
                }
            }
            public void focusLost(FocusEvent e) {
                if (f.getText().isEmpty()) {
                    f.setText(placeholder);
                    f.setForeground(AppColors.TEXT_SECONDARY);
                }
            }
        });
        f.setText(placeholder);
        f.setForeground(AppColors.TEXT_SECONDARY);
    }

    public static JLabel createSectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(AppColors.TEXT_SECONDARY);
        lbl.setBorder(new EmptyBorder(10, 14, 4, 14));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    public static JSeparator createSeparator() {
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColors.DIVIDER);
        sep.setBackground(AppColors.DIVIDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    public static JScrollPane createScrollPane(JComponent content, Color bg) {
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getViewport().setBackground(bg);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setBackground(bg);
        return sp;
    }
}

