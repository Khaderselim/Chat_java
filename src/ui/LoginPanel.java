package ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

public class LoginPanel extends JPanel {

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JLabel statusLabel;
    private Runnable onConnect;

    public LoginPanel() {
        setLayout(new GridBagLayout());
        setBackground(AppColors.BG_DARK);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColors.BG_SIDEBAR);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER, 1, true),
                new EmptyBorder(40, 50, 40, 50)
        ));

        JLabel title = new JLabel("Messenger", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(AppColors.TEXT_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = new JLabel("Enter your username", SwingConstants.CENTER);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(AppColors.TEXT_SECONDARY);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        usernameField = new JTextField(20);
        UIFactory.styleTextField(usernameField, "Username…");
        usernameField.setMaximumSize(new Dimension(280, 42));
        usernameField.setAlignmentX(Component.CENTER_ALIGNMENT);
        passwordField = new JPasswordField(20);
        UIFactory.styleTextField(passwordField, "Password…");
        passwordField.setMaximumSize(new Dimension(280, 42));
        passwordField.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton connectBtn = UIFactory.createAccentButton("Connect", AppColors.ACCENT);
        connectBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        connectBtn.setMaximumSize(new Dimension(280, 42));

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(AppColors.CALL_RED);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(title);
        card.add(Box.createVerticalStrut(4));
        card.add(sub);
        card.add(Box.createVerticalStrut(24));
        card.add(usernameField);
        card.add(Box.createVerticalStrut(14));
        card.add(passwordField);
        card.add(Box.createVerticalStrut(24));
        card.add(connectBtn);
        card.add(Box.createVerticalStrut(10));
        card.add(statusLabel);
        add(card);
        connectBtn.addActionListener(e -> doConnect());
        usernameField.addActionListener(e -> doConnect());
    }

    private void doConnect() {
        if (onConnect != null) onConnect.run();
    }

    public String getUsername() {
        String t = usernameField.getText().trim();
        return t.equals("Username") ? "" : t;
    }
    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    public void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setForeground(isError ? AppColors.CALL_RED : AppColors.TEXT_SECONDARY);
    }

    public void focusField() { usernameField.requestFocus(); }

    public void setOnConnect(Runnable r) { onConnect = r; }
}

