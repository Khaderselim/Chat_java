import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.Enumeration;

public class Client {
    // ── UI components ─────────────────────────────────────────────────────────
    public static JButton connectBtn, disconnectBtn, sendBtn, newChatBtn, singlebtn;
    public static JLabel  statusLbl, hostLbl;
    public static JTextField hostField, inputField;
    public static JTextArea  messageArea;
    public static JPanel chatPanel, loginPanel, selectPanel, singlePanel, singlenamesPanel;
    public static ButtonGroup singlenameGroup;
    public static JFrame frame;

    // ── Network ───────────────────────────────────────────────────────────────
    public static Socket        controlSocket, messageSocket, voiceSocket;
    public static PrintWriter   controlOut, messageOut;
    public static BufferedReader controlIn, messageIn;

    // ── Audio ─────────────────────────────────────────────────────────────────
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000, 16, 1, true, true);
    public static TargetDataLine microphone;   // mic  → network
    public static SourceDataLine speakers;     // network → speakers
    private volatile Thread micThread;
    private volatile Thread speakerThread;

    // ── State ─────────────────────────────────────────────────────────────────
    public static String selectedName, username;

    // ─────────────────────────────────────────────────────────────────────────

    public Client() {
        frame = new JFrame("Text Chat");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLocationRelativeTo(null);

        // ── Login Panel ──────────────────────────────────────────────────────
        loginPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 20));
        loginPanel.setBackground(new Color(240, 240, 240));
        hostLbl   = new JLabel("Username:");
        hostField = new JTextField(15);
        hostField.setText("username");
        connectBtn = new JButton("Connect");
        statusLbl  = new JLabel("");
        loginPanel.add(hostLbl);
        loginPanel.add(hostField);
        loginPanel.add(connectBtn);
        loginPanel.add(statusLbl);

        // ── Chat Panel ───────────────────────────────────────────────────────
        chatPanel = new JPanel(new BorderLayout(10, 10));
        chatPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(new Font("Arial", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputField    = new JTextField();
        sendBtn       = new JButton("Send");
        disconnectBtn = new JButton("Disconnect");
        newChatBtn    = new JButton("New Chat");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn,    BorderLayout.EAST);
        JPanel westBtns = new JPanel(new GridLayout(1, 2, 4, 0));
        westBtns.add(newChatBtn);
        westBtns.add(disconnectBtn);
        inputPanel.add(westBtns, BorderLayout.WEST);
        chatPanel.add(scrollPane,  BorderLayout.CENTER);
        chatPanel.add(inputPanel,  BorderLayout.SOUTH);

        // ── Select Panel ─────────────────────────────────────────────────────
        selectPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        selectPanel.setBorder(BorderFactory.createEmptyBorder(50, 100, 50, 100));
        selectPanel.setBackground(new Color(240, 240, 240));
        JLabel selectLbl = new JLabel("Select Chat Mode:");
        selectLbl.setFont(new Font("Arial", Font.BOLD, 18));
        selectLbl.setHorizontalAlignment(JLabel.CENTER);
        singlebtn = new JButton("Single Chat");
        singlebtn.setFont(new Font("Arial", Font.PLAIN, 14));
        singlebtn.addActionListener(e -> switchToSinglePanel());
        JPanel buttonPanel = new JPanel(new GridLayout(1, 1, 10, 0));
        buttonPanel.setBackground(new Color(240, 240, 240));
        buttonPanel.add(singlebtn);
        selectPanel.add(selectLbl);
        selectPanel.add(buttonPanel);

        // ── Single Panel ─────────────────────────────────────────────────────
        singlePanel = new JPanel(new BorderLayout(10, 10));
        singlePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        singlePanel.setBackground(new Color(240, 240, 240));
        JLabel singleLbl = new JLabel("Select a user to chat with:");
        singleLbl.setFont(new Font("Arial", Font.BOLD, 14));
        singlenamesPanel = new JPanel();
        singlenamesPanel.setLayout(new BoxLayout(singlenamesPanel, BoxLayout.Y_AXIS));
        singlenamesPanel.setBackground(new Color(240, 240, 240));
        singlenameGroup = new ButtonGroup();
        JScrollPane nameScrollPane = new JScrollPane(singlenamesPanel);
        nameScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        JPanel singleButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        singleButtonPanel.setBackground(new Color(240, 240, 240));
        JButton connectToChat = new JButton("Connect to Chat");
        JButton backBtn       = new JButton("Back");
        connectToChat.addActionListener(e -> connectToSingleChat());
        backBtn.addActionListener(e -> switchPanel(selectPanel));
        singleButtonPanel.add(connectToChat);
        singleButtonPanel.add(backBtn);
        singlePanel.add(singleLbl,         BorderLayout.NORTH);
        singlePanel.add(nameScrollPane,    BorderLayout.CENTER);
        singlePanel.add(singleButtonPanel, BorderLayout.SOUTH);

        // ── Wire up buttons ───────────────────────────────────────────────────
        frame.add(loginPanel);
        frame.setVisible(true);

        connectBtn.addActionListener(e    -> connectToServer());
        sendBtn.addActionListener(e       -> sendMessage());
        inputField.addActionListener(e    -> sendMessage());
        disconnectBtn.addActionListener(e -> disconnectFromServer());
        newChatBtn.addActionListener(e    -> startNewChat());
    }

    // ── Connection ────────────────────────────────────────────────────────────

    private void connectToServer() {
        try {
            controlSocket = new Socket("localhost", 1234);
            controlOut    = new PrintWriter(controlSocket.getOutputStream(), true);
            controlIn     = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));

            username = hostField.getText().trim();
            if (username.isEmpty()) username = "User";

            controlOut.println(username);
            statusLbl.setText("Connected!");

            String confirmation = controlIn.readLine();
            System.out.println("Control: " + confirmation);

            switchPanel(selectPanel);
        } catch (Exception ex) {
            statusLbl.setText("Connection failed!");
            statusLbl.setForeground(Color.RED);
        }
    }

    // ── Panel switching ───────────────────────────────────────────────────────

    private void switchPanel(JPanel panel) {
        frame.getContentPane().removeAll();
        frame.add(panel);
        frame.revalidate();
        frame.repaint();
    }

    // ── Single-chat flow ──────────────────────────────────────────────────────

    private void switchToSinglePanel() {
        controlOut.println("single");
        singlenamesPanel.removeAll();
        singlenameGroup = new ButtonGroup();

        new Thread(() -> {
            try {
                String line;
                while ((line = controlIn.readLine()) != null) {
                    if (line.equals("END_LIST")) break;
                    if (line.startsWith("AVAILABLE_USERS:")) continue;
                    final String name = line;
                    SwingUtilities.invokeLater(() -> {
                        JRadioButton rb = new JRadioButton(name);
                        rb.setFont(new Font("Arial", Font.PLAIN, 12));
                        singlenameGroup.add(rb);
                        singlenamesPanel.add(rb);
                        singlenamesPanel.revalidate();
                        singlenamesPanel.repaint();
                    });
                }
                SwingUtilities.invokeLater(() -> switchPanel(singlePanel));
            } catch (IOException ex) {
                System.out.println("Error receiving names: " + ex);
            }
        }).start();
    }

    private void connectToSingleChat() {
        selectedName = null;
        for (Enumeration<AbstractButton> e = singlenameGroup.getElements(); e.hasMoreElements();) {
            AbstractButton b = e.nextElement();
            if (b.isSelected()) { selectedName = b.getText(); break; }
        }
        if (selectedName == null || selectedName.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please select a user!", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        controlOut.println(selectedName);

        new Thread(() -> {
            try {
                closeMessageSocket();  // clean up any previous session
                closeVoiceSocket();

                // ── Text socket (port 1235) ───────────────────────────────
                messageSocket = new Socket("localhost", 1235);
                messageOut    = new PrintWriter(messageSocket.getOutputStream(), true);
                messageIn     = new BufferedReader(new InputStreamReader(messageSocket.getInputStream()));
                messageOut.println(username + ":" + selectedName);

                // ── Voice socket (port 1236) ──────────────────────────────
                voiceSocket = new Socket("localhost", 1236);
                // Send header line, then switch to raw binary
                OutputStream voiceOut = voiceSocket.getOutputStream();
                voiceOut.write((username + ":" + selectedName + "\n").getBytes());
                voiceOut.flush();

                // ── Open audio lines ──────────────────────────────────────
                DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
                microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
                microphone.open(AUDIO_FORMAT);
                microphone.start();

                DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
                speakers = (SourceDataLine) AudioSystem.getLine(spkInfo);
                speakers.open(AUDIO_FORMAT);
                speakers.start();

                SwingUtilities.invokeLater(() -> {
                    messageArea.setText("");
                    messageArea.append("=== Chatting with " + selectedName + " ===\n");
                    switchPanel(chatPanel);
                    inputField.requestFocus();
                });

                startListeningToMessages();
                startVoiceThreads(voiceOut, voiceSocket.getInputStream());

            } catch (Exception ex) {
                System.out.println("Connection error: " + ex);
            }
        }).start();
    }



    private void startVoiceThreads(OutputStream voiceOut, InputStream voiceIn) {
        // Mic → network
        micThread = new Thread(() -> {
            byte[] buf = new byte[1024];
            int n;
            try {
                while (!Thread.currentThread().isInterrupted()
                        && microphone != null && microphone.isOpen()) {
                    n = microphone.read(buf, 0, buf.length);
                    if (n > 0) {
                        voiceOut.write(buf, 0, n);
                        voiceOut.flush();
                    }
                }
            } catch (IOException e) {
                System.out.println("[VOICE] Mic thread stopped: " + e.getMessage());
            }
        }, "voice-mic");
        micThread.setDaemon(true);
        micThread.start();

        // Network → speakers
        speakerThread = new Thread(() -> {
            byte[] buf = new byte[1024];
            int n;
            try {
                while ((n = voiceIn.read(buf)) > 0) {
                    if (speakers != null && speakers.isOpen()) {
                        speakers.write(buf, 0, n);
                    }
                }
            } catch (IOException e) {
                System.out.println("[VOICE] Speaker thread stopped: " + e.getMessage());
            }
        }, "voice-speaker");
        speakerThread.setDaemon(true);
        speakerThread.start();
    }

    // ── Message I/O ───────────────────────────────────────────────────────────

    private void startListeningToMessages() {
        new Thread(() -> {
            try {
                String line;
                while ((line = messageIn.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> {
                        messageArea.append(msg + "\n");
                        messageArea.setCaretPosition(messageArea.getDocument().getLength());
                    });
                }
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> messageArea.append("\nDisconnected from server\n"));
            }
        }, "message-listener").start();
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) return;
        if (message.equalsIgnoreCase("exit")) { disconnectFromServer(); return; }
        if (messageOut == null) {
            JOptionPane.showMessageDialog(frame, "Not connected!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        messageOut.println(message);
        messageArea.append("You: " + message + "\n");
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
        inputField.setText("");
        inputField.requestFocus();
    }

    // ── New Chat / Disconnect ─────────────────────────────────────────────────

    private void startNewChat() {
        new Thread(() -> {
            closeMessageSocket();
            closeVoiceSocket();
            SwingUtilities.invokeLater(() -> switchPanel(selectPanel));
        }).start();
    }

    private void disconnectFromServer() {
        closeMessageSocket();
        closeVoiceSocket();
        try {
            if (controlOut    != null) { controlOut.println("disconnect"); controlOut = null; }
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close(); controlSocket = null;
            }
        } catch (IOException ex) {
            System.out.println("Disconnect error: " + ex);
        }
        messageArea.append("\nDisconnected from server\n");
        inputField.setEnabled(false);
        sendBtn.setEnabled(false);
        newChatBtn.setEnabled(false);
    }

    // ── Cleanup helpers ───────────────────────────────────────────────────────

    private void closeMessageSocket() {
        try {
            if (messageOut != null) { messageOut.println("exit"); messageOut = null; }
            messageIn = null;
            if (messageSocket != null && !messageSocket.isClosed()) {
                messageSocket.close(); messageSocket = null;
            }
        } catch (IOException ex) {
            System.out.println("Error closing message socket: " + ex);
        }
    }

    private void closeVoiceSocket() {
        // Stop audio threads first
        if (micThread     != null) { micThread.interrupt();     micThread     = null; }
        if (speakerThread != null) { speakerThread.interrupt(); speakerThread = null; }

        // Close hardware lines
        if (microphone != null && microphone.isOpen()) { microphone.stop(); microphone.close(); microphone = null; }
        if (speakers   != null && speakers.isOpen())   { speakers.stop();   speakers.close();   speakers   = null; }

        // Close socket (will unblock voiceIn.read in speakerThread)
        try {
            if (voiceSocket != null && !voiceSocket.isClosed()) {
                voiceSocket.close(); voiceSocket = null;
            }
        } catch (IOException ex) {
            System.out.println("Error closing voice socket: " + ex);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}