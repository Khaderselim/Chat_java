import ui.*;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.*;
import java.net.*;
import java.util.*;


public class Client {
    static final String HOST = "localhost";
    static final int CONTROL_PORT = 1234;
    static final int MESSAGE_PORT = 1235;
    static final int VOICE_PORT = 1236;
    static final AudioFormat CALL_FORMAT = new AudioFormat(16000, 16, 1, true, true);
    static final AudioFormat VM_FORMAT = new AudioFormat(16000, 16, 1, true, true);
    Socket controlSocket, messageSocket, voiceSocket;
    PrintWriter controlOut, messageOut;
    BufferedReader controlIn, messageIn;
    TargetDataLine microphone;
    SourceDataLine speakers;
    Thread micThread, speakerThread;
    boolean inCall = false;
    boolean muted = false;
    TargetDataLine vmMic;
    Thread vmRecordThread;
    ByteArrayOutputStream vmBuffer;
    long vmStartTime;
    String username;
    String password;
    String currentChatTarget;
    boolean currentChatIsGroup;
    final Map<String, String> groupNames = new LinkedHashMap<>();
    final Map<String, List<String>> groupMembers = new LinkedHashMap<>();
    final Map<String, Boolean> userOnline = new LinkedHashMap<>();
    JFrame frame;
    LoginPanel loginPanel;
    SignPanel signPanel;
    SidebarPanel sidebarPanel;
    ChatPanel chatPanel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }

    public Client() { buildFrame(); }

    void buildFrame() {
        frame = new JFrame("Messenger");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 650);
        frame.setMinimumSize(new Dimension(700, 450));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(AppColors.BG_DARK);
        loginPanel = new LoginPanel();
        signPanel = new SignPanel();
        sidebarPanel = new SidebarPanel();
        chatPanel = new ChatPanel();
        loginPanel.setOnConnect(this::connectToServer);
        loginPanel.setOnRegister(this::showSignup);
        signPanel.setOnRegister(this::registerUser);
        signPanel.setOnBack(this::showLogin);

        sidebarPanel.setOnDisconnect(this::disconnectFromServer);
        sidebarPanel.setOnNewGroup(() -> showCreateGroupDialog());
        sidebarPanel.setOnUserClick(this::openPrivateChat);
        sidebarPanel.setOnGroupClick(this::openGroupChat);
        sidebarPanel.setOnAddMemberClick(this::showAddMemberDialog);
        chatPanel.setOnSend(this::sendMessage);
        chatPanel.setOnToggleCall(this::toggleCall);
        chatPanel.setOnToggleMute(this::toggleMute);
        chatPanel.setOnStartVoiceRecord(this::startVoiceMessage);
        chatPanel.setOnStopVoiceRecord(this::stopAndSendVoiceMessage);

        showLogin();
    }

    void showLogin() {
        frame.setContentPane(loginPanel);
        frame.setVisible(true);
        loginPanel.focusField();
    }

    void showSignup() {
        frame.setContentPane(signPanel);
        frame.revalidate();
        frame.repaint();
        signPanel.focusField();
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

    void registerUser() {
        String username = signPanel.getUsername();
        String password = signPanel.getPassword();
        if (username.isEmpty()) { signPanel.setStatus("Enter a username", true); return; }
        if (password.isEmpty()) { signPanel.setStatus("Enter a password", true); return; }
        if (!signPanel.isPasswordValid()) { signPanel.setStatus("Passwords do not match", true); return;}
        new Thread(() -> {
            try {
                controlSocket = new Socket(HOST, CONTROL_PORT);
                controlOut    = new PrintWriter(controlSocket.getOutputStream(), true);
                controlIn     = new BufferedReader(
                        new InputStreamReader(controlSocket.getInputStream()));
                controlOut.println("REGISTER");
                controlOut.println("LOGIN:" + username);
                controlOut.println("PASSWORD:" + password);
                String resp = controlIn.readLine();

                if (resp == null || resp.startsWith("ERROR:")) {
                    SwingUtilities.invokeLater(() -> loginPanel.setStatus(
                            resp != null && resp.contains("INVALID")? "Invalid Username or Password" : resp.contains("TAKEN")
                                    ? "Username already in use!" : "Connection failed!", true));

                    return;
                }

                messageSocket = new Socket(HOST, MESSAGE_PORT);
                messageOut    = new PrintWriter(messageSocket.getOutputStream(), true);
                messageIn     = new BufferedReader(
                        new InputStreamReader(messageSocket.getInputStream()));
                messageOut.println("REGISTER:" + username);

                SwingUtilities.invokeLater(() -> {
                    frame.setTitle("💬 Messenger — " + username);
                    sidebarPanel.setMyName(username);
                    showMain();
                });

                startControlListener();
                startMessageListener();

                requestUserList();
                requestGroupList();

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        signPanel.setStatus(
                                "Cannot reach server (HOST=" + HOST + ")", true));
            }
        }).start();

    }

    void connectToServer() {
        username = loginPanel.getUsername();
        password = loginPanel.getPassword();

        if (username.isEmpty()) { loginPanel.setStatus("Enter a username", true); return; }
        if (password.isEmpty()) { loginPanel.setStatus("Enter a password", true); return; }

        new Thread(() -> {
            try {
                controlSocket = new Socket(HOST, CONTROL_PORT);
                controlOut    = new PrintWriter(controlSocket.getOutputStream(), true);
                controlIn     = new BufferedReader(
                        new InputStreamReader(controlSocket.getInputStream()));
                controlOut.println("LOGIN");
                controlOut.println("LOGIN:" + username);
                controlOut.println("PASSWORD:" + password);
                String resp = controlIn.readLine();

                if (resp == null || resp.startsWith("ERROR:")) {
                    SwingUtilities.invokeLater(() -> loginPanel.setStatus(
                            resp != null && resp.contains("INVALID")? "Invalid Username or Password" : resp.contains("TAKEN")
                                    ? "Username already in use!" : "Connection failed!", true));

                    return;
                }

                messageSocket = new Socket(HOST, MESSAGE_PORT);
                messageOut    = new PrintWriter(messageSocket.getOutputStream(), true);
                messageIn     = new BufferedReader(
                        new InputStreamReader(messageSocket.getInputStream()));
                messageOut.println("REGISTER:" + username);

                SwingUtilities.invokeLater(() -> {
                    frame.setTitle("💬 Messenger — " + username);
                    sidebarPanel.setMyName(username);
                    showMain();
                });

                startControlListener();
                startMessageListener();

                requestUserList();
                requestGroupList();

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        loginPanel.setStatus(
                                "Cannot reach server (HOST=" + HOST + ")", true));
            }
        }).start();
    }



    void requestUserList()  { controlOut.println("LIST_USERS"); }
    void requestGroupList() { controlOut.println("LIST_GROUPS"); }

    void startControlListener() {
        new Thread(() -> {
            try {
                String line;
                while ((line = controlIn.readLine()) != null) handleControlEvent(line);
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() ->
                        chatPanel.addSystemMessage("Connection to server lost"));
            }
        }, "control-listener").start();
    }

    void handleControlEvent(String line) {
        if (line.equals("USER_LIST_START") || line.equals("USER_LIST_END")
                || line.equals("GROUP_LIST_START") || line.equals("GROUP_LIST_END")) return;

        if (line.startsWith("USER:")) {
            String[] p = line.substring(5).split(":", 2);
            if (p.length < 2) return;
            boolean online = p[1].equalsIgnoreCase("ONLINE");
            userOnline.put(p[0], online);
            SwingUtilities.invokeLater(() -> sidebarPanel.setUser(p[0], online));
            return;
        }
        if (line.startsWith("USER_ONLINE:")) {
            String u = line.substring(12);
            if (!u.equals(username)) {
                userOnline.put(u, true);
                SwingUtilities.invokeLater(() -> {
                    sidebarPanel.setUser(u, true);
                    chatPanel.updateContactStatus(u, true);
                });
            }
            return;
        }
        if (line.startsWith("USER_OFFLINE:")) {
            String u = line.substring(13);
            userOnline.put(u, false);
            SwingUtilities.invokeLater(() -> {
                sidebarPanel.setUserOffline(u);
                chatPanel.updateContactStatus(u, false);
                if (u.equals(currentChatTarget) && !currentChatIsGroup)
                    chatPanel.addSystemMessage(u + " has disconnected.");
            });
            return;
        }
        if (line.startsWith("GROUP:")) { parseAndAddGroup(line.substring(6));  return; }
        if (line.startsWith("GROUP_CREATED:")) { parseAndAddGroup(line.substring(14)); return; }
        if (line.startsWith("ADDED_TO_GROUP:")) {
            parseAndAddGroup(line.substring(15));
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame,
                            "You have been added to a group!",
                            "Notification", JOptionPane.INFORMATION_MESSAGE));
            return;
        }
        if (line.startsWith("MEMBER_ADDED:")) {
            String[] p = line.substring(13).split(":", 2);
            if (p.length == 2) {
                String gid = p[0];
                String newMember = p[1];
                List<String> members = groupMembers.get(gid);
                if (members != null && !members.contains(newMember)) {
                    members.add(newMember);
                    if (gid.equals(currentChatTarget) && currentChatIsGroup) {
                        SwingUtilities.invokeLater(() -> chatPanel.updateGroupMemberCount(members.size()));
                    }
                }
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(frame,
                                newMember + " has been added to the group.",
                                "Member added", JOptionPane.INFORMATION_MESSAGE));
            }
            return;
        }
        if (line.startsWith("HISTORY_START:")) { receiveHistory(line.substring(14)); return; }
        if (line.startsWith("ERROR:"))
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "Error: " + line.substring(6),
                            "Error", JOptionPane.ERROR_MESSAGE));
    }


    void receiveHistory(String typeAndTarget) {
        List<String[]> lines = new ArrayList<>();
        try {
            String l;
            while ((l = controlIn.readLine()) != null) {
                if (l.equals("HISTORY_END")) break;
                if (l.startsWith("HISTORY_LINE:")) {
                    String entry = l.substring(13);
                    int sep = entry.indexOf(": ");
                    if (sep > 0) lines.add(new String[]{
                            entry.substring(0, sep), entry.substring(sep + 2)});
                }
            }
        } catch (IOException ignored) {}

        SwingUtilities.invokeLater(() -> {
            for (String[] msg : lines) {
                boolean isMe = msg[0].equals(username);
                if (msg[1].startsWith("VOICE_MSG:")) {
                    String[] vp = msg[1].split(":", 3);
                    if (vp.length == 3) {
                        try {
                            int dur = Integer.parseInt(vp[1]);
                            byte[] audio = Base64.getDecoder().decode(vp[2]);
                            chatPanel.addVoiceMessage(isMe ? "you" : msg[0], audio, dur, isMe);
                        } catch (Exception ignored) {}
                    }
                } else {
                    chatPanel.addMessage(isMe ? "you" : msg[0], msg[1], isMe);
                }
            }
        });
    }

    void parseAndAddGroup(String data) {
        String[] p = data.split(":", 4);
        if (p.length < 3) return;
        String gid = p[0], gname = p[1];
        List<String> members = p.length > 3
                ? new ArrayList<>(Arrays.asList(p[3].split(",")))
                : new ArrayList<>();
        groupNames.put(gid, gname);
        groupMembers.put(gid, members);
        SwingUtilities.invokeLater(() -> sidebarPanel.addGroup(gid, gname));
    }

    void startMessageListener() {
        new Thread(() -> {
            try {
                String line;
                while ((line = messageIn.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> handleIncomingMessage(msg));
                }
            } catch (IOException ex) {
                System.out.println("[CLIENT] Message channel closed");
            }
        }, "message-listener").start();
    }

    void handleIncomingMessage(String line) {
        if (line.startsWith("PRIVATE:")) {
            String[] p = line.substring(8).split(":", 2);
            if (p.length < 2) return;
            String from = p[0], msg = p[1];
            if (from.equals(currentChatTarget) && !currentChatIsGroup)
                chatPanel.addMessage(from, msg, false);
            else
                sidebarPanel.incrementUnread(from);
            return;
        }

        if (line.startsWith("GROUP:")) {
            String[] p = line.substring(6).split(":", 4);
            if (p.length < 4) return;
            String gid = p[0], from = p[2], msg = p[3];
            if (gid.equals(currentChatTarget) && currentChatIsGroup)
                chatPanel.addMessage(from, msg, false);
            else
                sidebarPanel.incrementGroupUnread(gid);
            return;
        }

        if (line.startsWith("VOICE_PRIVATE:")) {
            String[] p = line.substring(14).split(":", 3);
            if (p.length < 3) return;
            String from = p[0];
            try {
                int    dur   = Integer.parseInt(p[1]);
                byte[] audio = Base64.getDecoder().decode(p[2]);
                if (from.equals(currentChatTarget) && !currentChatIsGroup)
                    chatPanel.addVoiceMessage(from, audio, dur, false);
                else
                    sidebarPanel.incrementUnread(from);
            } catch (Exception ignored) {}
            return;
        }

        if (line.startsWith("VOICE_GROUP:")) {
            String[] p = line.substring(12).split(":", 5);
            if (p.length < 5) return;
            String gid = p[0], from = p[2];
            try {
                int    dur   = Integer.parseInt(p[3]);
                byte[] audio = Base64.getDecoder().decode(p[4]);
                if (gid.equals(currentChatTarget) && currentChatIsGroup)
                    chatPanel.addVoiceMessage(from, audio, dur, false);
                else
                    sidebarPanel.incrementGroupUnread(gid);
            } catch (Exception ignored) {}
        }
    }

    void openPrivateChat(String targetUser) {
        currentChatTarget  = targetUser;
        currentChatIsGroup = false;
        boolean online = userOnline.getOrDefault(targetUser, false);
        chatPanel.openPrivate(targetUser, online);
        sidebarPanel.clearUnread(targetUser);
        controlOut.println("GET_HISTORY:" + targetUser);
        chatPanel.focusInput();
    }

    void openGroupChat(String gid, String gname) {
        currentChatTarget  = gid;
        currentChatIsGroup = true;
        List<String> members = groupMembers.getOrDefault(gid, new ArrayList<>());
        chatPanel.openGroup(gname, members.size());
        sidebarPanel.clearGroupUnread(gid);
        controlOut.println("GET_GROUP_HISTORY:" + gid);
        chatPanel.focusInput();
    }

    void sendMessage() {
        String text = chatPanel.getAndClearInput();
        if (text.isEmpty() || currentChatTarget == null || messageOut == null) return;
        if (currentChatIsGroup)
            messageOut.println("GROUP:" + username + ":" + currentChatTarget + ":" + text);
        else
            messageOut.println("PRIVATE:" + username + ":" + currentChatTarget + ":" + text);
        chatPanel.addMessage("you", text, true);
    }

    void startVoiceMessage() {
        if (currentChatTarget == null || messageOut == null) return;

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, VM_FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(frame,
                                "Microphone not available.",
                                "Audio error", JOptionPane.ERROR_MESSAGE));
                return;
            }

            vmMic    = (TargetDataLine) AudioSystem.getLine(info);
            vmBuffer = new ByteArrayOutputStream();
            vmMic.open(VM_FORMAT);
            vmMic.start();
            vmStartTime = System.currentTimeMillis();

            chatPanel.setRecording(true);
            chatPanel.addSystemMessage("Recording...");

            vmRecordThread = new Thread(() -> {
                byte[] buf = new byte[1024];
                int n;
                try {
                    while (!Thread.currentThread().isInterrupted()
                            && vmMic != null && vmMic.isOpen()) {
                        n = vmMic.read(buf, 0, buf.length);
                        if (n > 0) vmBuffer.write(buf, 0, n);
                    }
                } catch (Exception ignored) {}
            }, "vm-record");
            vmRecordThread.setDaemon(true);
            vmRecordThread.start();

        } catch (LineUnavailableException ex) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame,
                            "Failed to open microphone: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE));
        }
    }

    void stopAndSendVoiceMessage() {
        if (vmMic == null || vmBuffer == null) return;

        if (vmRecordThread != null) { vmRecordThread.interrupt(); vmRecordThread = null; }
        vmMic.stop();
        vmMic.close();
        vmMic = null;

        byte[] audioData = vmBuffer.toByteArray();
        vmBuffer = null;

        int durationSec = (int)((System.currentTimeMillis() - vmStartTime) / 1000);
        if (durationSec < 1) durationSec = 1;

        chatPanel.setRecording(false);

        if (audioData.length == 0) {
            chatPanel.addSystemMessage("Empty voice message, cancelled.");
            return;
        }

        String encoded = Base64.getEncoder().encodeToString(audioData);

        if (currentChatIsGroup) {
            messageOut.println("VOICE_MSG_GROUP:" + username + ":"
                    + currentChatTarget + ":" + durationSec + ":" + encoded);
        } else {
            messageOut.println("VOICE_MSG_PRIVATE:" + username + ":"
                    + currentChatTarget + ":" + durationSec + ":" + encoded);
        }

        final byte[] finalData    = audioData;
        final int    finalDur     = durationSec;
        SwingUtilities.invokeLater(() ->
                chatPanel.addVoiceMessage("you", finalData, finalDur, true));
    }

    void toggleCall() {
        if (!inCall) startCall();
        else         endCall();
    }

    void startCall() {
        if (currentChatTarget == null) return;
        new Thread(() -> {
            try {
                closeVoiceSocket();
                voiceSocket = new Socket(HOST, VOICE_PORT);
                OutputStream voiceOut = voiceSocket.getOutputStream();

                voiceOut.write(("REGISTER_VOICE:" + username + "\n").getBytes());
                voiceOut.flush();
                String mode = currentChatIsGroup
                        ? "GROUP_VOICE:"   + username + ":" + currentChatTarget + "\n"
                        : "PRIVATE_VOICE:" + username + ":" + currentChatTarget + "\n";
                voiceOut.write(mode.getBytes());
                voiceOut.flush();

                DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, CALL_FORMAT);
                microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
                microphone.open(CALL_FORMAT);
                microphone.start();

                DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, CALL_FORMAT);
                speakers = (SourceDataLine) AudioSystem.getLine(spkInfo);
                speakers.open(CALL_FORMAT);
                speakers.start();

                inCall = true;
                SwingUtilities.invokeLater(() -> chatPanel.setCallActive(true));
                startVoiceThreads(voiceOut, voiceSocket.getInputStream());

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(frame,
                                "Cannot start call: " + ex.getMessage(),
                                "Audio error", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    void endCall() {
        inCall = false; muted = false;
        closeVoiceSocket();
        SwingUtilities.invokeLater(() -> chatPanel.setCallActive(false));
    }

    void toggleMute() {
        muted = !muted;
        if (microphone != null) { if (muted) microphone.stop(); else microphone.start(); }
        chatPanel.setMuted(muted);
    }

    void startVoiceThreads(OutputStream voiceOut, InputStream voiceIn) {
        micThread = new Thread(() -> {
            byte[] buf = new byte[1024]; int n;
            try {
                while (!Thread.currentThread().isInterrupted()
                        && microphone != null && microphone.isOpen()) {
                    n = microphone.read(buf, 0, buf.length);
                    if (n > 0 && !muted) { voiceOut.write(buf, 0, n); voiceOut.flush(); }
                }
            } catch (IOException ignored) {}
        }, "mic-thread");
        micThread.setDaemon(true); micThread.start();

        speakerThread = new Thread(() -> {
            byte[] buf = new byte[1024]; int n;
            try {
                while ((n = voiceIn.read(buf)) > 0)
                    if (speakers != null && speakers.isOpen()) speakers.write(buf, 0, n);
            } catch (IOException ignored) {}
        }, "speaker-thread");
        speakerThread.setDaemon(true); speakerThread.start();
    }

    void closeVoiceSocket() {
        if (micThread     != null) { micThread.interrupt();     micThread     = null; }
        if (speakerThread != null) { speakerThread.interrupt(); speakerThread = null; }
        if (microphone != null && microphone.isOpen()) {
            microphone.stop(); microphone.close(); microphone = null; }
        if (speakers   != null && speakers.isOpen()) {
            speakers.stop(); speakers.close(); speakers = null; }
        try { if (voiceSocket != null && !voiceSocket.isClosed()) {
            voiceSocket.close(); voiceSocket = null; }
        } catch (IOException ignored) {}
    }


    void showCreateGroupDialog() {
        if (controlOut == null) return;
        JDialog dialog = new JDialog(frame, "New group", true);
        dialog.setSize(360, 220);
        dialog.setLocationRelativeTo(frame);
        dialog.getContentPane().setBackground(AppColors.BG_SIDEBAR);
        dialog.setLayout(new BorderLayout(0, 0));

        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(AppColors.BG_SIDEBAR);
        content.setBorder(new EmptyBorder(20, 24, 20, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(4, 0, 4, 0);

        JLabel lbl = new JLabel("Group name:");
        lbl.setForeground(AppColors.TEXT_PRIMARY);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JTextField nameField = new JTextField();
        UIFactory.styleTextField(nameField, "Enter group name");
        gbc.gridy = 0;
        content.add(lbl, gbc);
        gbc.gridy = 1;
        content.add(nameField, gbc);

        JButton createBtn = UIFactory.createAccentButton("Create", AppColors.ACCENT);
        gbc.gridy = 2;
        gbc.insets = new Insets(16, 0, 0, 0);
        content.add(createBtn, gbc);

        dialog.add(content, BorderLayout.CENTER);

        createBtn.addActionListener(e -> {
            String gname = nameField.getText().trim();
            if (gname.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter a group name.", "Info", JOptionPane.INFORMATION_MESSAGE);
            } else {
                controlOut.println("CREATE_GROUP:" + gname);
                dialog.dispose();
            }
        });
        dialog.setVisible(true);
    }

    void showAddMemberDialog(String gid) {
        if (controlOut == null || userOnline.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "No users available.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JDialog dialog = new JDialog(frame, "Add member", true);
        dialog.setSize(340, 350);
        dialog.setLocationRelativeTo(frame);
        dialog.getContentPane().setBackground(AppColors.BG_SIDEBAR);
        dialog.setLayout(new BorderLayout(0, 0));

        JLabel title = new JLabel("Select a user:");
        title.setForeground(AppColors.TEXT_PRIMARY);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setBorder(new EmptyBorder(16, 20, 8, 20));

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(AppColors.BG_SIDEBAR);

        ButtonGroup bg = new ButtonGroup();
        List<String> existing = groupMembers.getOrDefault(gid, new ArrayList<>());

        for (Map.Entry<String, Boolean> e : userOnline.entrySet()) {
            String  u      = e.getKey();
            boolean online = e.getValue();
            if (existing.contains(u)) continue;
            JRadioButton rb = new JRadioButton(u + (online ? "" : " (offline)"));
            rb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            rb.setForeground(online ? AppColors.TEXT_PRIMARY : AppColors.OFFLINE_GRAY);
            rb.setBackground(AppColors.BG_SIDEBAR);
            rb.setBorder(new EmptyBorder(8, 20, 8, 20));
            rb.putClientProperty("username", u);
            bg.add(rb); listPanel.add(rb);
        }

        JScrollPane scroll = UIFactory.createScrollPane(listPanel, AppColors.BG_SIDEBAR);
        scroll.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel buttonPanel = new JPanel(new BorderLayout(8, 0));
        buttonPanel.setBackground(AppColors.BG_SIDEBAR);
        buttonPanel.setBorder(new EmptyBorder(12, 20, 12, 20));
        JButton addBtn = UIFactory.createAccentButton("Add", AppColors.ACCENT);
        JButton cancelBtn = UIFactory.createSmallButton("Cancel", AppColors.TEXT_SECONDARY);
        buttonPanel.add(addBtn, BorderLayout.EAST);
        buttonPanel.add(cancelBtn, BorderLayout.WEST);

        addBtn.addActionListener(e -> {
            ButtonModel selected = bg.getSelection();
            if (selected != null) {
                for (Enumeration<AbstractButton> buttons = bg.getElements(); buttons.hasMoreElements();) {
                    JRadioButton rb = (JRadioButton) buttons.nextElement();
                    if (rb.getModel() == selected) {
                        String targetUser = (String) rb.getClientProperty("username");
                        controlOut.println("ADD_MEMBER:" + gid + ":" + targetUser);
                        dialog.dispose();
                        break;
                    }
                }
            }
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.add(title, BorderLayout.NORTH);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    void disconnectFromServer() {
        endCall();
        if (vmMic != null) {
            if (vmRecordThread != null) { vmRecordThread.interrupt(); vmRecordThread = null; }
            vmMic.stop(); vmMic.close(); vmMic = null;
        }
        try {
            if (controlOut != null) controlOut.println("DISCONNECT");
            if (messageSocket != null && !messageSocket.isClosed()) messageSocket.close();
            if (controlSocket != null && !controlSocket.isClosed()) controlSocket.close();
        } catch (IOException ignored) {}
        userOnline.clear(); groupNames.clear(); groupMembers.clear();
        currentChatTarget = null;
        frame.setTitle("💬 Messenger");
        SwingUtilities.invokeLater(() -> { loginPanel.setStatus("Disconnected", false); showLogin(); });
    }
}

