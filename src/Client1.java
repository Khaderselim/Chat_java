import old_ui.AppColors;
import old_ui.ChatPanel;
import old_ui.LoginPanel;
import old_ui.SidebarPanel;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client1 {
    static final AudioFormat CALL_FORMAT = new AudioFormat(16000, 16, 1, true, true);
    static final AudioFormat VM_FORMAT   = new AudioFormat(16000, 16, 1, true, true);
    public static Socket controlSocket, messageSocket, voiceSocket;
    public static PrintWriter controlOut, messageOut;
    public static BufferedReader controlIn, messageIn;
    public static TargetDataLine microphone;
    public static TargetDataLine vmMic;
    public static SourceDataLine speakers;
    private volatile Thread micThread;
    private volatile Thread speakerThread;
    public static String selectedName, username;
    JFrame frame;
    LoginPanel loginPanel;
    SidebarPanel sidebarPanel;
    ChatPanel chatPanel;

    public Client1() { buildFrame(); }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client1::new);
    }
    void buildFrame() {
        frame = new JFrame("Messagerie");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 650);
        frame.setMinimumSize(new Dimension(700, 450));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(AppColors.BG_DARK);

        loginPanel = new LoginPanel();
        sidebarPanel = new SidebarPanel();
        chatPanel = new ChatPanel();

        loginPanel.setOnConnect(this::connectToServer);
//        sidebarPanel.setOnDisconnect(this::disconnectFromServer);
//        sidebarPanel.setOnNewGroup(() -> showCreateGroupDialog());
//        sidebarPanel.setOnUserClick(this::openPrivateChat);
//        sidebarPanel.setOnGroupClick(this::openGroupChat);
//        sidebarPanel.setOnAddMemberClick(this::showAddMemberDialog);
//        chatPanel.setOnSend(this::sendMessage);
//        chatPanel.setOnToggleCall(this::toggleCall);
//        chatPanel.setOnToggleMute(this::toggleMute);
//
//        chatPanel.setOnStartVoiceRecord(this::startVoiceMessage);
//        chatPanel.setOnStopVoiceRecord(this::stopAndSendVoiceMessage);

        showLogin();
    }

    void showLogin() {
        frame.setContentPane(loginPanel);
        frame.setVisible(true);
        loginPanel.focusField();
    }

    void showMain() {
        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, chatPanel);
        split.setDividerLocation(270);
        split.setDividerSize(1);
        split.setBorder(null);
        split.setBackground(new Color(50, 52, 80));

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(AppColors.BG_DARK);
        main.add(split, BorderLayout.CENTER);

        frame.setContentPane(main);
        frame.revalidate();
        frame.repaint();
    }
    private void connectToServer() {
        try {
            controlSocket = new Socket("localhost", 1234);
            controlOut = new PrintWriter(controlSocket.getOutputStream(), true);
            controlIn = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            username = loginPanel.getUsername();
            if (username.isEmpty()) username = "User";
            controlOut.println(username);
            String confirmation = controlIn.readLine();
            System.out.println("Control: " + confirmation);
            SwingUtilities.invokeLater(() -> {
                frame.setTitle("Messagerie — " + username);
                sidebarPanel.setMyName(username);
                showMain();
            });
            switchToSinglePanel();

        } catch (Exception ex) {
            System.out.println("Connection failed: " + ex);
        }
    }


    private void switchToSinglePanel() {
        new Thread(() -> {
            while (true) {controlOut.println("single");}
        }).start();



        new Thread(() -> {
            try {
                String line;
                while ((line = controlIn.readLine()) != null) {
                    System.out.println("Control: " + line);
                    if (line.equals("END_LIST")) return;
                    if (line.startsWith("AVAILABLE_USERS:")) continue;
                    final String name = line;
                    System.out.println("Available user: " + name);
                    SwingUtilities.invokeLater(() -> {
                        sidebarPanel.setUser(name, true);
                    });
                }
            } catch (IOException ex) {
                System.out.println("Error receiving names: " + ex);
            }
        }).start();
    }

}
