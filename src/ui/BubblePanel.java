package ui;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;


public class BubblePanel extends JPanel {

    private final boolean isMe;
    private static final AudioFormat FMT = new AudioFormat(16000, 16, 1, true, true);

    public BubblePanel(String sender, String text, boolean isMe) {
        this.isMe = isMe;
        init();
        buildText(sender, text);
    }

    public BubblePanel(String sender, byte[] audio, int durSec, boolean isMe) {
        this.isMe = isMe;
        init();
        buildVoice(sender, audio, durSec);
    }

    private void init() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 14, 10, 14));
    }

    private void buildText(String sender, String text) {
        if (!isMe) {
            JLabel sl = new JLabel(sender);
            sl.setFont(new Font("Segoe UI", Font.BOLD, 11));
            sl.setForeground(AppColors.ACCENT);
            sl.setAlignmentX(LEFT_ALIGNMENT);
            add(sl);
            add(Box.createVerticalStrut(2));
        }
        JLabel ml = new JLabel(esc(text) );
        ml.setFont(new Font("Segoe UI", Font.PLAIN ,13));
        ml.setForeground(AppColors.TEXT_PRIMARY);
        ml.setAlignmentX(LEFT_ALIGNMENT);
        add(ml);
        add(Box.createVerticalStrut(4));
        add(timeLabel());
    }

    private void buildVoice(String sender, byte[] audio, int durSec) {

        if (!isMe) {
            JLabel sl = new JLabel(sender);
            sl.setFont(new Font("Segoe UI", Font.BOLD, 11));
            sl.setForeground(AppColors.ACCENT);
            sl.setAlignmentX(LEFT_ALIGNMENT);
            add(sl);
            add(Box.createVerticalStrut(4));
        }

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);

        JPanel micIcon = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isMe ? new Color(200, 205, 255) : AppColors.ACCENT);
                g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                int cx = getWidth()/2, cy = getHeight()/2;
                g2.drawRoundRect(cx-4, cy-8, 8, 11, 5, 5);
                g2.drawArc(cx-7, cy-1, 14, 10, 0, -180);
                g2.drawLine(cx, cy+9, cx, cy+13);
                g2.drawLine(cx-4, cy+13, cx+4, cy+13);
                g2.dispose();
            }
        };
        micIcon.setOpaque(false);
        micIcon.setPreferredSize(new Dimension(18, 22));

        JLabel titleLbl = new JLabel("Voice message  " + fmt(durSec));
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLbl.setForeground(isMe ? new Color(200, 205, 255) : AppColors.ACCENT);

        titleRow.add(micIcon);
        titleRow.add(titleLbl);
        add(titleRow);
        add(Box.createVerticalStrut(8));

        final boolean[] playing = {false};
        final Thread[]  th      = {null};

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(0);
        bar.setPreferredSize(new Dimension(120, 5));
        bar.setMaximumSize(new Dimension(120, 5));
        bar.setBackground(new Color(70, 72, 100));
        bar.setForeground(isMe ? new Color(200, 210, 255) : AppColors.ACCENT);
        bar.setBorderPainted(false);
        bar.setOpaque(true);
        bar.setUI(new BasicProgressBarUI() {
            @Override protected void paintDeterminate(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bar.getBackground());
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 5, 5);
                int w = (int)(c.getWidth() * bar.getValue() / 100.0);
                if (w > 0) { g2.setColor(bar.getForeground());
                    g2.fillRoundRect(0, 0, w, c.getHeight(), 5, 5); }
                g2.dispose();
            }
        });

        JLabel durLbl = new JLabel(fmt(durSec));
        durLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        durLbl.setForeground(new Color(170, 175, 210));
        durLbl.setPreferredSize(new Dimension(34, 14));

        JButton playBtn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isMe ? new Color(255,255,255,55)
                        : new Color(124,131,253,80));
                g2.fillOval(2, 2, getWidth()-4, getHeight()-4);
                g2.setColor(Color.WHITE);
                int cx = getWidth()/2, cy = getHeight()/2;
                if (!playing[0]) {
                    int[] xp = {cx-5, cx-5, cx+7};
                    int[] yp = {cy-7, cy+7, cy};
                    g2.fillPolygon(xp, yp, 3);
                } else {
                    g2.fillRect(cx-6, cy-6, 4, 12);
                    g2.fillRect(cx+2, cy-6, 4, 12);
                }
                g2.dispose();
            }
        };
        playBtn.setPreferredSize(new Dimension(36, 36));
        playBtn.setContentAreaFilled(false);
        playBtn.setFocusPainted(false);
        playBtn.setBorder(null);
        playBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        playBtn.setToolTipText("Play / Stop");

        JPanel playerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        playerRow.setOpaque(false);
        playerRow.setAlignmentX(LEFT_ALIGNMENT);
        playerRow.add(playBtn);
        playerRow.add(bar);
        playerRow.add(durLbl);
        add(playerRow);
        add(Box.createVerticalStrut(4));
        add(timeLabel());

        playBtn.addActionListener(e -> {
            if (playing[0]) {
                playing[0] = false;
                if (th[0] != null) th[0].interrupt();
                playBtn.repaint();
                bar.setValue(0);
                durLbl.setText(fmt(durSec));
                return;
            }
            if (audio == null || audio.length == 0) {
                JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Audio not available.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            playing[0] = true;
            playBtn.repaint();

            th[0] = new Thread(() -> {
                SourceDataLine sdl = null;
                try {
                    sdl = (SourceDataLine) AudioSystem.getLine(
                            new DataLine.Info(SourceDataLine.class, FMT));
                    sdl.open(FMT);
                    sdl.start();
                    int total = audio.length, offset = 0, chunk = 2048;
                    while (offset < total && playing[0]
                            && !Thread.currentThread().isInterrupted()) {
                        int n = Math.min(chunk, total - offset);
                        sdl.write(audio, offset, n);
                        offset += n;
                        final int pct = (int)(100.0 * offset / total);
                        final int rem = Math.max(0,
                                (int)(durSec * (1.0 - (double)offset / total)));
                        SwingUtilities.invokeLater(() -> {
                            bar.setValue(pct);
                            durLbl.setText(fmt(rem));
                        });
                    }
                    sdl.drain();
                } catch (Exception ex) {
                    System.out.println("[PLAYER] " + ex.getMessage());
                } finally {
                    if (sdl != null && sdl.isOpen()) { sdl.stop(); sdl.close(); }
                    playing[0] = false;
                    SwingUtilities.invokeLater(() -> {
                        playBtn.repaint();
                        bar.setValue(0);
                        durLbl.setText(fmt(durSec));
                    });
                }
            }, "audio-player");
            th[0].setDaemon(true);
            th[0].start();
        });
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isMe ? AppColors.BUBBLE_ME : AppColors.BUBBLE_OTHER);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        g2.dispose();
        super.paintComponent(g);
    }

    private JLabel timeLabel() {
        JLabel l = new JLabel(new SimpleDateFormat("HH:mm").format(new Date()));
        l.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        l.setForeground(new Color(155, 160, 190));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private String esc(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private static String fmt(int s) {
        if (s < 0) s = 0;
        return String.format("%d:%02d", s/60, s%60);
    }
}

