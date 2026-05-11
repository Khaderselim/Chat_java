package ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class ChatPanel extends JPanel {

    private final JLabel      chatTargetLabel;
    private final JLabel      chatStatusLabel;
    private final JButton     callBtn;
    private final JButton     muteBtn;
    private final JLabel      callTimerLabel;
    private final JPanel      messagesPanel;
    private final JScrollPane messagesScroll;
    private final JTextField  inputField;
    private final JButton     sendBtn;
    private final JButton     voiceRecordBtn;
    private boolean           recording = false;

    private Timer callTimerObj;
    private int callSeconds = 0;

    private Runnable onSend;
    private Runnable onToggleCall;
    private Runnable onToggleMute;
    private Runnable onStartVoiceRecord;
    private Runnable onStopVoiceRecord;

    public ChatPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_CHAT);

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(new Color(22, 23, 36));
        header.setBorder(new EmptyBorder(14, 20, 14, 20));

        JPanel leftH = new JPanel(new BorderLayout(0, 3));
        leftH.setBackground(new Color(22, 23, 36));
        chatTargetLabel = new JLabel("Select a conversation");
        chatTargetLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chatTargetLabel.setForeground(AppColors.TEXT_PRIMARY);
        chatStatusLabel = new JLabel(" ");
        chatStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chatStatusLabel.setForeground(AppColors.ONLINE_GREEN);
        leftH.add(chatTargetLabel, BorderLayout.NORTH);
        leftH.add(chatStatusLabel, BorderLayout.SOUTH);

        JPanel rightH = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightH.setBackground(new Color(22, 23, 36));

        callTimerLabel = new JLabel("");
        callTimerLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        callTimerLabel.setForeground(AppColors.ONLINE_GREEN);
        callTimerLabel.setVisible(false);

        muteBtn = buildMuteButton();
        muteBtn.setVisible(false);
        muteBtn.addActionListener(e -> { if (onToggleMute != null) onToggleMute.run(); });

        callBtn = UIFactory.createAccentButton("Call", AppColors.CALL_GREEN);
        callBtn.setEnabled(false);
        callBtn.addActionListener(e -> { if (onToggleCall != null) onToggleCall.run(); });

        rightH.add(callTimerLabel);
        rightH.add(muteBtn);
        rightH.add(callBtn);
        header.add(leftH,  BorderLayout.WEST);
        header.add(rightH, BorderLayout.EAST);

        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(AppColors.BG_CHAT);
        messagesPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        showPlaceholder();

        messagesScroll = UIFactory.createScrollPane(messagesPanel, AppColors.BG_CHAT);
        messagesScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel inputBar = new JPanel(new BorderLayout(6, 0));
        inputBar.setBackground(new Color(20, 21, 34));
        inputBar.setBorder(new EmptyBorder(10, 14, 10, 14));

        voiceRecordBtn = buildVoiceButton();

        inputField = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppColors.TEXT_SECONDARY);
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString("Type a message…", getInsets().left + 4, y);
                    g2.dispose();
                }
            }
        };
        inputField.setBackground(AppColors.BG_INPUT);
        inputField.setForeground(AppColors.TEXT_PRIMARY);
        inputField.setCaretColor(AppColors.ACCENT);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER, 1, true),
                new EmptyBorder(10, 14, 10, 14)));
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        inputField.setEnabled(false);

        sendBtn = buildSendButton();
        sendBtn.setEnabled(false);

        sendBtn.addActionListener(e  -> { if (onSend != null) onSend.run(); });
        inputField.addActionListener(e -> { if (onSend != null) onSend.run(); });

        inputBar.add(voiceRecordBtn, BorderLayout.WEST);
        inputBar.add(inputField,     BorderLayout.CENTER);
        inputBar.add(sendBtn,        BorderLayout.EAST);

        add(header,         BorderLayout.NORTH);
        add(messagesScroll, BorderLayout.CENTER);
        add(inputBar,       BorderLayout.SOUTH);
    }

    private JButton buildVoiceButton() {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                Color bg;
                if (!isEnabled())       bg = new Color(40, 41, 60);
                else if (recording)     bg = AppColors.CALL_RED;
                else if (getModel().isRollover()) bg = new Color(70, 72, 110);
                else                    bg = new Color(50, 52, 80);
                g2.setColor(bg);
                g2.fillRoundRect(1, 1, getWidth()-2, getHeight()-2, 10, 10);

                Color iconColor = isEnabled() ? Color.WHITE : new Color(80, 82, 110);
                g2.setColor(iconColor);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2, cy = getHeight() / 2;
                g2.drawRoundRect(cx - 5, cy - 10, 10, 13, 6, 6);
                g2.drawArc(cx - 9, cy - 1, 18, 12, 0, -180);
                g2.drawLine(cx, cy + 11, cx, cy + 15);
                g2.drawLine(cx - 5, cy + 15, cx + 5, cy + 15);

                if (recording) {
                    g2.setColor(Color.WHITE);
                    g2.fillOval(cx + 6, cy - 12, 6, 6);
                }

                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(46, 44));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(2, 4, 2, 4));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("Record a voice message");
        btn.setEnabled(false);

        btn.addActionListener(e -> {
            if (!recording) {
                recording = true;
                btn.repaint();
                btn.setToolTipText("Recording... — Click to send");
                if (onStartVoiceRecord != null) onStartVoiceRecord.run();
            } else {
                recording = false;
                btn.repaint();
                btn.setToolTipText("Record a voice message");
                if (onStopVoiceRecord != null) onStopVoiceRecord.run();
            }
        });
        return btn;
    }

    private JButton buildSendButton() {
        JButton btn = new JButton("Send") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = !isEnabled()            ? new Color(60, 62, 100)
                        : getModel().isPressed()  ? AppColors.ACCENT.darker()
                        : getModel().isRollover() ? AppColors.ACCENT.brighter()
                        : AppColors.ACCENT;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton buildMuteButton() {
        JButton btn = new JButton() {
            private boolean muted = false;
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(muted ? new Color(80, 82, 110) : new Color(50, 52, 80));
                g2.fillRoundRect(1, 1, getWidth()-2, getHeight()-2, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.8f));
                int cx = getWidth()/2, cy = getHeight()/2;
                g2.drawRoundRect(cx-4, cy-8, 8, 10, 5, 5);
                g2.drawArc(cx-7, cy-1, 14, 9, 0, -180);
                g2.drawLine(cx, cy+8, cx, cy+11);
                g2.drawLine(cx-4, cy+11, cx+4, cy+11);
                if (muted) {
                    g2.setColor(AppColors.CALL_RED);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawLine(cx-8, cy-8, cx+8, cy+8);
                }
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(38, 38));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(2, 2, 2, 2));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("Mute / Unmute");
        return btn;
    }

    public void openPrivate(String targetName, boolean online) {
        chatTargetLabel.setText(targetName);
        chatStatusLabel.setText(online ? "● online" : "● offline");
        chatStatusLabel.setForeground(online ? AppColors.ONLINE_GREEN : AppColors.OFFLINE_GRAY);
        callBtn.setEnabled(online);
        callBtn.setText("Call");
        callBtn.setBackground(AppColors.CALL_GREEN);
        inputField.setEnabled(true);
        sendBtn.setEnabled(true);
        voiceRecordBtn.setEnabled(true);
        clearMessages();
    }

    public void openGroup(String groupName, int memberCount) {
        chatTargetLabel.setText("Group: " + groupName);
        chatStatusLabel.setText(memberCount + " member(s)");
        chatStatusLabel.setForeground(AppColors.TEXT_SECONDARY);
        callBtn.setEnabled(true);
        callBtn.setText("Group call");
        callBtn.setBackground(AppColors.CALL_GREEN);
        inputField.setEnabled(true);
        sendBtn.setEnabled(true);
        voiceRecordBtn.setEnabled(true);
        clearMessages();
    }

    public void updateContactStatus(String name, boolean online) {
        if (chatTargetLabel.getText().equals(name)) {
            chatStatusLabel.setText(online ? "● online" : "● offline");
            chatStatusLabel.setForeground(online ? AppColors.ONLINE_GREEN : AppColors.OFFLINE_GRAY);
            callBtn.setEnabled(online);
        }
    }
    public void updateGroupMemberCount(int memberCount) {
        chatStatusLabel.setText(memberCount + " member(s)");
    }
    public void addMessage(String sender, String text, boolean isMe) {
        JPanel row = makeRow(isMe);
        row.add(new BubblePanel(sender, text, isMe));
        messagesPanel.add(row);
        messagesPanel.add(Box.createVerticalStrut(4));
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    public void addVoiceMessage(String sender, byte[] audioData, int durationSec, boolean isMe) {
        JPanel row = makeRow(isMe);
        row.add(new BubblePanel(sender, audioData, durationSec, isMe));
        messagesPanel.add(row);
        messagesPanel.add(Box.createVerticalStrut(4));
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    public void addSystemMessage(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lbl.setForeground(AppColors.TEXT_SECONDARY);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER));
        row.setBackground(AppColors.BG_CHAT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(lbl);
        messagesPanel.add(row);
        messagesPanel.revalidate();
        messagesPanel.repaint();

        scrollToBottom();
    }

    public void clearMessages() {
        messagesPanel.removeAll();
        messagesPanel.revalidate();
        messagesPanel.repaint();
    }

    public String getAndClearInput() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            inputField.setText("");
            inputField.repaint();
        }
        return text;
    }

    public void focusInput() {
        SwingUtilities.invokeLater(() -> inputField.requestFocusInWindow());
    }

    public void setRecording(boolean rec) {
        recording = rec;
        voiceRecordBtn.repaint();
    }

    public void setCallActive(boolean active) {
        if (active) {
            callBtn.setText("Hang up");
            callBtn.setBackground(AppColors.CALL_RED);
            muteBtn.setVisible(true);
            callTimerLabel.setVisible(true);
            startTimer();
        } else {
            callBtn.setText("Call");
            callBtn.setBackground(AppColors.CALL_GREEN);
            muteBtn.setVisible(false);
            callTimerLabel.setVisible(false);
            callTimerLabel.setText("");
            stopTimer();
        }
    }

    public void setMuted(boolean muted) { muteBtn.repaint(); }

    public void setOnSend(Runnable r)             { onSend = r; }
    public void setOnToggleCall(Runnable r)       { onToggleCall = r; }
    public void setOnToggleMute(Runnable r)       { onToggleMute = r; }
    public void setOnStartVoiceRecord(Runnable r) { onStartVoiceRecord = r; }
    public void setOnStopVoiceRecord(Runnable r)  { onStopVoiceRecord  = r; }

    private JPanel makeRow(boolean isMe) {
        JPanel row = new JPanel(new FlowLayout(
                isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        row.setBackground(AppColors.BG_CHAT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private void showPlaceholder() {
        JLabel ph = new JLabel("Select a conversation to start");
        ph.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        ph.setForeground(AppColors.TEXT_SECONDARY);
        ph.setAlignmentX(Component.CENTER_ALIGNMENT);
        messagesPanel.add(Box.createVerticalGlue());
        messagesPanel.add(ph);
        messagesPanel.add(Box.createVerticalGlue());
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = messagesScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void startTimer() {
        callSeconds = 0;
        callTimerObj = new Timer(1000, e -> {
            callSeconds++;
            callTimerLabel.setText(String.format("%02d:%02d",
                    callSeconds / 60, callSeconds % 60));
        });
        callTimerObj.start();
    }

    private void stopTimer() {
        if (callTimerObj != null) { callTimerObj.stop(); callTimerObj = null; }
        callSeconds = 0;
    }
}

