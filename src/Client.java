import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Enumeration;

public class Client {
    public static JButton connectBtn;
    public static JButton disconnectBtn;
    public static JButton sendBtn;
    public static JButton newChatBtn;
    public static JButton singlebtn;
    public static JLabel statusLbl;
    public static JTextField hostField;
    public static JLabel hostLbl;
    public static JTextArea messageArea;
    public static JTextField inputField;
    public static JPanel chatPanel;
    public static JPanel loginPanel;
    public static JPanel selectPanel;
    public static JPanel singlePanel;
    public static ButtonGroup singlenameGroup;
    public static JPanel singlenamesPanel;

    // Separate sockets and streams for control and messages
    public static Socket controlSocket;
    public static Socket messageSocket;
    public static PrintWriter controlOut;
    public static BufferedReader controlIn;
    public static PrintWriter messageOut;
    public static BufferedReader messageIn;

    public static JFrame frame;
    public static String selectedName;
    public static String username;

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
        messageArea.setBackground(new Color(255, 255, 255));

        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputField = new JTextField();
        inputField.setFont(new Font("Arial", Font.PLAIN, 12));

        sendBtn     = new JButton("Send");
        disconnectBtn = new JButton("Disconnect");
        newChatBtn  = new JButton("New Chat");   // ← NEW

        inputPanel.add(inputField,    BorderLayout.CENTER);
        inputPanel.add(sendBtn,       BorderLayout.EAST);

        // West side: two buttons stacked
        JPanel westBtns = new JPanel(new GridLayout(1, 2, 4, 0));
        westBtns.add(newChatBtn);
        westBtns.add(disconnectBtn);
        inputPanel.add(westBtns, BorderLayout.WEST);

        chatPanel.add(scrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        // ── Select Panel (mode selection) ────────────────────────────────────
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

        // ── Single Panel (user list) ─────────────────────────────────────────
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

        // ── FIXED Back button ────────────────────────────────────────────────
        // Just switches the panel – does NOT re-send anything to the server.
        // The server's ControlHandler stays in its loop waiting for the next
        // "single" command, which will come when the user clicks "Single Chat" again.
        backBtn.addActionListener(e -> switchPanel(selectPanel));

        singleButtonPanel.add(connectToChat);
        singleButtonPanel.add(backBtn);

        singlePanel.add(singleLbl,        BorderLayout.NORTH);
        singlePanel.add(nameScrollPane,   BorderLayout.CENTER);
        singlePanel.add(singleButtonPanel, BorderLayout.SOUTH);

        // ── Wire up global buttons ───────────────────────────────────────────
        frame.add(loginPanel);
        frame.setVisible(true);

        connectBtn.addActionListener(e -> connectToServer());
        sendBtn.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        disconnectBtn.addActionListener(e -> disconnectFromServer());

        // New Chat: close the current message socket and go back to select mode
        newChatBtn.addActionListener(e -> startNewChat());
    }

    // ── Connection ───────────────────────────────────────────────────────────

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
            System.out.println("Control response: " + confirmation);

            switchPanel(selectPanel);

        } catch (Exception ex) {
            statusLbl.setText("Connection failed!");
            statusLbl.setForeground(Color.RED);
            System.out.println("Connection error: " + ex);
        }
    }

    // ── Panel switching ──────────────────────────────────────────────────────

    private void switchPanel(JPanel panel) {
        frame.getContentPane().removeAll();
        frame.add(panel);
        frame.revalidate();
        frame.repaint();
    }

    // ── Single-chat flow ─────────────────────────────────────────────────────

    /**
     * Called when the user clicks "Single Chat" (or returns from "New Chat").
     * Sends "single" to the server, which triggers a fresh AVAILABLE_USERS list.
     */
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
                        JRadioButton radioBtn = new JRadioButton(name);
                        radioBtn.setFont(new Font("Arial", Font.PLAIN, 12));
                        singlenameGroup.add(radioBtn);
                        singlenamesPanel.add(radioBtn);
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

    /**
     * Called when the user picks a name and clicks "Connect to Chat".
     */
    private void connectToSingleChat() {
        selectedName = null;
        for (Enumeration<AbstractButton> buttons = singlenameGroup.getElements(); buttons.hasMoreElements();) {
            AbstractButton button = buttons.nextElement();
            if (button.isSelected()) {
                selectedName = button.getText();
                break;
            }
        }

        if (selectedName == null || selectedName.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please select a user to chat with!", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Tell the server who we chose (server reads this as the target after "single")
        controlOut.println(selectedName);

        // Open message socket
        new Thread(() -> {
            try {
                // Close any existing message socket (e.g. coming from "New Chat")
                closeMessageSocket();

                messageSocket = new Socket("localhost", 1235);
                messageOut    = new PrintWriter(messageSocket.getOutputStream(), true);
                messageIn     = new BufferedReader(new InputStreamReader(messageSocket.getInputStream()));

                // Session info: username:target
                messageOut.println(username + ":" + selectedName);

                SwingUtilities.invokeLater(() -> {
                    messageArea.setText(""); // clear previous conversation
                    messageArea.append("=== Chatting with " + selectedName + " ===\n");
                    switchPanel(chatPanel);
                    inputField.requestFocus();
                });

                startListeningToMessages();

            } catch (Exception ex) {
                System.out.println("Message connection error: " + ex);
            }
        }).start();
    }


    private void startNewChat() {
        new Thread(() -> {
            closeMessageSocket();
            // Go back to mode-select; user will click "Single Chat" again,
            // which sends "single" to the server for a fresh user list.
            SwingUtilities.invokeLater(() -> switchPanel(selectPanel));
        }).start();
    }


    private void startListeningToMessages() {
        new Thread(() -> {
            try {
                String serverResponse;
                while ((serverResponse = messageIn.readLine()) != null) {
                    final String message = serverResponse;
                    SwingUtilities.invokeLater(() -> {
                        messageArea.append(message + "\n");
                        messageArea.setCaretPosition(messageArea.getDocument().getLength());
                    });
                }
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> messageArea.append("\nDisconnected from server\n"));
            }
        }).start();
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) return;

        if (message.equalsIgnoreCase("exit")) {
            disconnectFromServer();
            return;
        }

        if (messageOut == null) {
            JOptionPane.showMessageDialog(frame, "Not connected to a chat!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        messageOut.println(message);
        messageArea.append("You: " + message + "\n");
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
        inputField.setText("");
        inputField.requestFocus();
    }


    private void closeMessageSocket() {
        try {
            if (messageOut  != null) { messageOut.println("exit"); messageOut = null; }
            if (messageIn   != null) { messageIn  = null; }
            if (messageSocket != null && !messageSocket.isClosed()) {
                messageSocket.close();
                messageSocket = null;
            }
        } catch (IOException ex) {
            System.out.println("Error closing message socket: " + ex);
        }
    }

    private void disconnectFromServer() {
        closeMessageSocket();
        try {
            if (controlOut    != null) { controlOut.println("disconnect"); controlOut = null; }
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
                controlSocket = null;
            }
        } catch (IOException ex) {
            System.out.println("Error disconnecting: " + ex);
        }

        messageArea.append("\nDisconnected from server\n");
        inputField.setEnabled(false);
        sendBtn.setEnabled(false);
        newChatBtn.setEnabled(false);
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client());
    }
}