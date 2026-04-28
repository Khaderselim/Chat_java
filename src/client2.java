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

public class client2 {
    public static JButton connectBtn;
    public static JButton disconnectBtn;
    public static JButton sendBtn;
    public static JButton singlebtn;
    public static JButton multiBtn;
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
    public static JRadioButton[] radioButtons;
    public static String selectedName;
    public static String chatMode;
    public static String username;

    public client2() {
        frame = new JFrame("Text Chat");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLocationRelativeTo(null);

        // Login Panel
        loginPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 20));
        loginPanel.setBackground(new Color(240, 240, 240));

        hostLbl = new JLabel("Host name:");
        hostField = new JTextField(15);
        hostField.setText("username");

        connectBtn = new JButton("Connect");
        statusLbl = new JLabel("");

        loginPanel.add(hostLbl);
        loginPanel.add(hostField);
        loginPanel.add(connectBtn);
        loginPanel.add(statusLbl);

        // Chat Panel (hidden initially)
        chatPanel = new JPanel(new BorderLayout(10, 10));
        chatPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Message display area
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(new Font("Arial", Font.PLAIN, 12));
        messageArea.setBackground(new Color(255, 255, 255));

        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // Input Panel
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputField = new JTextField();
        inputField.setFont(new Font("Arial", Font.PLAIN, 12));

        sendBtn = new JButton("Send");
        disconnectBtn = new JButton("Disconnect");

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        inputPanel.add(disconnectBtn, BorderLayout.WEST);

        chatPanel.add(scrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        // Select Panel (Mode Selection)
        selectPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        selectPanel.setBorder(BorderFactory.createEmptyBorder(50, 100, 50, 100));
        selectPanel.setBackground(new Color(240, 240, 240));

        JLabel selectLbl = new JLabel("Select Chat Mode:");
        selectLbl.setFont(new Font("Arial", Font.BOLD, 18));
        selectLbl.setHorizontalAlignment(JLabel.CENTER);

        singlebtn = new JButton("Single Chat");
        multiBtn = new JButton("Multi Chat");

        singlebtn.setFont(new Font("Arial", Font.PLAIN, 14));
        multiBtn.setFont(new Font("Arial", Font.PLAIN, 14));

        singlebtn.addActionListener(e -> switchToSinglePanel());
        multiBtn.addActionListener(e -> switchToChat("multi"));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.setBackground(new Color(240, 240, 240));
        buttonPanel.add(singlebtn);
        buttonPanel.add(multiBtn);

        selectPanel.add(selectLbl);
        selectPanel.add(buttonPanel);

        // Single Panel (Name Selection)
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
        JButton backBtn = new JButton("Back");

        connectToChat.addActionListener(e -> connectToSingleChat());
        backBtn.addActionListener(e -> switchPanel(selectPanel));

        singleButtonPanel.add(connectToChat);
        singleButtonPanel.add(backBtn);

        singlePanel.add(singleLbl, BorderLayout.NORTH);
        singlePanel.add(nameScrollPane, BorderLayout.CENTER);
        singlePanel.add(singleButtonPanel, BorderLayout.SOUTH);

        frame.add(loginPanel);
        frame.setVisible(true);

        // Connect button action
        connectBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connectToServer();
            }
        });

        // Send button action
        sendBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Enter key to send
        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Disconnect button action
        disconnectBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disconnectFromServer();
            }
        });
    }

    private void connectToServer() {
        try {
            // CONTROL CONNECTION - For username, chat mode, selections
            controlSocket = new Socket("localhost", 1234);
            controlOut = new PrintWriter(controlSocket.getOutputStream(), true);
            controlIn = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));

            // Send username
            username = hostField.getText().trim();
            if (username.isEmpty()) {
                username = "User";
            }
            controlOut.println(username);
            statusLbl.setText("Connected!");

            // Read confirmation
            String confirmation = controlIn.readLine();
            System.out.println("Control response: " + confirmation);

            // Switch UI to select panel
            frame.getContentPane().removeAll();
            frame.add(selectPanel);
            frame.revalidate();
            frame.repaint();

        } catch (Exception ex) {
            statusLbl.setText("Connection failed!");
            statusLbl.setForeground(Color.RED);
            System.out.println("Connection error: " + ex);
        }
    }

    private void switchPanel(JPanel panel) {
        frame.getContentPane().removeAll();
        frame.add(panel);
        frame.revalidate();
        frame.repaint();
    }

    private void switchToChat(String mode) {
        chatMode = mode;

        // Send mode selection via control connection
        controlOut.println(mode);

        // Create message connection for actual chat
        new Thread(() -> {
            try {
                messageSocket = new Socket("localhost", 1235);
                messageOut = new PrintWriter(messageSocket.getOutputStream(), true);
                messageIn = new BufferedReader(new InputStreamReader(messageSocket.getInputStream()));

                // Send session info: username:chatmode:target
                messageOut.println(username + ":" + mode + ":");

                SwingUtilities.invokeLater(() -> {
                    messageArea.setText("Connected to " + mode + " chat\n");
                    messageArea.setCaretPosition(0);
                    switchPanel(chatPanel);
                    inputField.requestFocus();
                });

                // Start listening for server messages
                startListeningToMessages();

            } catch (Exception ex) {
                System.out.println("Message connection error: " + ex);
            }
        }).start();
    }

    private void switchToSinglePanel() {
        // Send single mode request via control connection
        controlOut.println("single");
        singlenamesPanel.removeAll();
        singlenameGroup = new ButtonGroup();

        // Read list of available users from control connection
        new Thread(() -> {
            try {
                String namesResponse;
                while ((namesResponse = controlIn.readLine()) != null) {
                    if (namesResponse.equals("END_LIST")) {
                        break;
                    }
                    if (namesResponse.startsWith("AVAILABLE_USERS:")) {
                        continue; // Skip header
                    }

                    final String name = namesResponse;
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


    private void connectToSingleChat() {
        // Get selected radio button
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

        // Send target selection via control connection
        controlOut.println(selectedName);
        chatMode = "single";

        // Create message connection for actual chat
        new Thread(() -> {
            try {
                messageSocket = new Socket("localhost", 1235);
                messageOut = new PrintWriter(messageSocket.getOutputStream(), true);
                messageIn = new BufferedReader(new InputStreamReader(messageSocket.getInputStream()));

                // Send session info: username:chatmode:target
                messageOut.println(username + ":" + chatMode + ":" + selectedName);

                SwingUtilities.invokeLater(() -> {
                    messageArea.setText("Connected to " + selectedName + " (Single Chat)\n");
                    messageArea.setCaretPosition(0);
                    switchPanel(chatPanel);
                    inputField.requestFocus();
                });

                // Start listening for server messages
                startListeningToMessages();

            } catch (Exception ex) {
                System.out.println("Message connection error: " + ex);
            }
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
                SwingUtilities.invokeLater(() -> {
                    messageArea.append("\nDisconnected from server\n");
                });
            }
        }).start();
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        if (message.equalsIgnoreCase("exit")) {
            disconnectFromServer();
            return;
        }

        messageOut.println(message);
        messageArea.append("You: " + message + "\n");
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
        inputField.setText("");
        inputField.requestFocus();
    }

    private void disconnectFromServer() {
        try {
            if (messageOut != null) {
                messageOut.println("exit");
            }
            if (messageSocket != null && !messageSocket.isClosed()) {
                messageSocket.close();
            }
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
            messageArea.append("\nDisconnected from server\n");
            inputField.setEnabled(false);
            sendBtn.setEnabled(false);
        } catch (IOException ex) {
            System.out.println(ex);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new client2());
    }
}