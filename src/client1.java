import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class client1 {
    public static JButton connectBtn;
    public static JButton disconnectBtn;
    public static JButton sendBtn;
    public static JLabel statusLbl;
    public static JTextField hostField;
    public static JLabel hostLbl;
    public static JTextArea messageArea;
    public static JTextField inputField;
    public static JPanel chatPanel;
    public static JPanel loginPanel;
    public static String host;
    public static int port;
    public static Socket socket;
    public static PrintWriter out;
    public static JFrame frame;

    public client1() {
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
            socket = new Socket("localhost", 1234);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send username
            out.println(hostField.getText());
            statusLbl.setText("Connected!");

            // Switch UI to chat panel
            loginPanel.setVisible(false);
            frame.getContentPane().removeAll();
            frame.add(chatPanel);
            frame.revalidate();
            frame.repaint();
            inputField.requestFocus();

            messageArea.append("Connected to server as: " + hostField.getText() + "\n");

            // Start thread to listen for server messages
            new Thread(() -> {
                try {
                    String serverResponse;
                    while ((serverResponse = in.readLine()) != null) {
                        final String message = serverResponse;
                        SwingUtilities.invokeLater(() -> {
                            messageArea.append("Server: " + message + "\n");
                            messageArea.setCaretPosition(messageArea.getDocument().getLength());
                        });
                    }
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        messageArea.append("\nDisconnected from server\n");
                    });
                }
            }).start();

        } catch (Exception ex) {
            statusLbl.setText("Connection failed!");
            statusLbl.setForeground(Color.RED);
            System.out.println(ex);
        }
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

        out.println(message);
        messageArea.append("You: " + message + "\n");
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
        inputField.setText("");
        inputField.requestFocus();
    }

    private void disconnectFromServer() {
        try {
            if (out != null) {
                out.println("exit");
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            messageArea.append("\nDisconnected from server\n");
            inputField.setEnabled(false);
            sendBtn.setEnabled(false);
        } catch (IOException ex) {
            System.out.println(ex);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new client1());
    }
}