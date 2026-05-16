package ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class SidebarPanel extends JPanel {

    private final Map<String, Boolean> userStatus = new LinkedHashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();
    private final Map<String, String> groupNames = new LinkedHashMap<>();
    private final Map<String, Integer> groupUnread = new HashMap<>();

    private final JPanel userListPanel;
    private final JPanel groupListPanel;
    private final JLabel myNameLabel;
    private final JButton newGroupBtn;
    private final JButton disconnectBtn;

    private Runnable onDisconnect;
    private Runnable onNewGroup;
    private java.util.function.Consumer<String> onUserClick;
    private java.util.function.BiConsumer<String, String> onGroupClick;
    private java.util.function.Consumer<String> onAddMemberClick;

    public SidebarPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SIDEBAR);
        setPreferredSize(new Dimension(270, 0));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(AppColors.BG_SIDEBAR);
        header.setBorder(new EmptyBorder(16, 16, 12, 16));

        JLabel titleLbl = new JLabel("Messages");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLbl.setForeground(AppColors.TEXT_PRIMARY);

        newGroupBtn = UIFactory.createIconButton("+", "New group");
        newGroupBtn.addActionListener(e -> { if (onNewGroup != null) onNewGroup.run(); });

        header.add(titleLbl,   BorderLayout.WEST);
        header.add(newGroupBtn, BorderLayout.EAST);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(AppColors.BG_SIDEBAR);

        body.add(UIFactory.createSectionLabel("Users"));
        userListPanel = new JPanel();
        userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS));
        userListPanel.setBackground(AppColors.BG_SIDEBAR);
        body.add(userListPanel);

        body.add(Box.createVerticalStrut(12));
        body.add(UIFactory.createSectionLabel("My groups"));
        groupListPanel = new JPanel();
        groupListPanel.setLayout(new BoxLayout(groupListPanel, BoxLayout.Y_AXIS));
        groupListPanel.setBackground(AppColors.BG_SIDEBAR);
        body.add(groupListPanel);
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = UIFactory.createScrollPane(body, AppColors.BG_SIDEBAR);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBackground(new Color(20, 21, 32));
        bottom.setBorder(new EmptyBorder(12, 14, 12, 14));
        myNameLabel = new JLabel("Connected");
        myNameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        myNameLabel.setForeground(AppColors.ONLINE_GREEN);
        disconnectBtn = UIFactory.createSmallButton("Disconnect", AppColors.CALL_RED);
        disconnectBtn.addActionListener(e -> { if (onDisconnect != null) onDisconnect.run(); });
        bottom.add(myNameLabel,   BorderLayout.WEST);
        bottom.add(disconnectBtn, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(scroll,  BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    public void setMyName(String name) {
        myNameLabel.setText("● " + name);
    }

    public void setUser(String username, boolean online) {
        userStatus.put(username, online);
        SwingUtilities.invokeLater(this::refreshUserList);
    }

    public void setUserOffline(String username) {
        userStatus.put(username, false);
        SwingUtilities.invokeLater(this::refreshUserList);
    }

    public void incrementUnread(String username) {
        unreadCounts.merge(username, 1, Integer::sum);
        SwingUtilities.invokeLater(this::refreshUserList);
    }

    public void clearUnread(String username) {
        unreadCounts.remove(username);
        SwingUtilities.invokeLater(this::refreshUserList);
    }

    public void addGroup(String gid, String gname) {
        groupNames.put(gid, gname);
        SwingUtilities.invokeLater(this::refreshGroupList);
    }

    public void incrementGroupUnread(String gid) {
        groupUnread.merge(gid, 1, Integer::sum);
        SwingUtilities.invokeLater(this::refreshGroupList);
    }

    public void clearGroupUnread(String gid) {
        groupUnread.remove(gid);
        SwingUtilities.invokeLater(this::refreshGroupList);
    }
    public void clearGroups() {
        groupNames.clear();
        groupUnread.clear();
        SwingUtilities.invokeLater(this::refreshGroupList);
    }
    public void setOnDisconnect(Runnable r) { onDisconnect = r; }
    public void setOnNewGroup(Runnable r) { onNewGroup = r; }
    public void setOnUserClick(java.util.function.Consumer<String> c) { onUserClick = c; }
    public void setOnGroupClick(java.util.function.BiConsumer<String,String> c) { onGroupClick = c; }
    public void setOnAddMemberClick(java.util.function.Consumer<String> c) { onAddMemberClick = c; }

    private void refreshUserList() {
        userListPanel.removeAll();
        for (Map.Entry<String, Boolean> e : userStatus.entrySet()) {
            String uname  = e.getKey();
            boolean online = e.getValue();
            int unread = unreadCounts.getOrDefault(uname, 0);
            userListPanel.add(buildUserItem(uname, online, unread));
        }
        userListPanel.revalidate();
        userListPanel.repaint();
    }

    private void refreshGroupList() {
        groupListPanel.removeAll();
        for (Map.Entry<String, String> e : groupNames.entrySet()) {
            String gid   = e.getKey();
            String gname = e.getValue();
            int unread   = groupUnread.getOrDefault(gid, 0);
            groupListPanel.add(buildGroupItem(gid, gname, unread));
        }
        groupListPanel.revalidate();
        groupListPanel.repaint();
    }

    private JPanel buildUserItem(String uname, boolean online, int unread) {
        JPanel item = new JPanel(new BorderLayout(8, 0));
        item.setBackground(AppColors.BG_SIDEBAR);
        item.setBorder(new EmptyBorder(9, 14, 9, 14));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel dot = new JLabel(online ? "●" : "○");
        dot.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        dot.setForeground(online ? AppColors.ONLINE_GREEN : AppColors.OFFLINE_GRAY);

        JPanel nameBlock = new JPanel();
        nameBlock.setLayout(new BoxLayout(nameBlock, BoxLayout.Y_AXIS));
        nameBlock.setBackground(AppColors.BG_SIDEBAR);
        JLabel nameLbl = new JLabel(uname);
        nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        nameLbl.setForeground(online ? AppColors.TEXT_PRIMARY : AppColors.OFFLINE_GRAY);
        JLabel statusLbl = new JLabel(online ? "Online" : "Offline");
        statusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        statusLbl.setForeground(online ? AppColors.ONLINE_GREEN : AppColors.OFFLINE_GRAY);
        nameBlock.add(nameLbl);
        nameBlock.add(statusLbl);

        item.add(dot, BorderLayout.WEST);
        item.add(nameBlock, BorderLayout.CENTER);

        if (unread > 0) {
            JLabel badge = createBadge(String.valueOf(unread));
            item.add(badge, BorderLayout.EAST);
        }

        item.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { item.setBackground(new Color(35, 36, 55)); nameBlock.setBackground(new Color(35, 36, 55)); }
            public void mouseExited(MouseEvent e)  { item.setBackground(AppColors.BG_SIDEBAR); nameBlock.setBackground(AppColors.BG_SIDEBAR); }
            public void mouseClicked(MouseEvent e) {
                clearUnread(uname);
                if (onUserClick != null) onUserClick.accept(uname);
            }
        });

        return item;
    }

    private JPanel buildGroupItem(String gid, String gname, int unread) {
        JPanel item = new JPanel(new BorderLayout(8, 0));
        item.setBackground(AppColors.BG_SIDEBAR);
        item.setBorder(new EmptyBorder(9, 14, 9, 14));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel nameLbl = new JLabel(gname);
        nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        nameLbl.setForeground(AppColors.TEXT_PRIMARY);
        item.add(nameLbl, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.setBackground(AppColors.BG_SIDEBAR);

        if (unread > 0) rightPanel.add(createBadge(String.valueOf(unread)));

        JButton addBtn = UIFactory.createIconButton("+", "Add member");
        addBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
        addBtn.addActionListener(e -> { if (onAddMemberClick != null) onAddMemberClick.accept(gid); });
        rightPanel.add(addBtn);

        item.add(rightPanel, BorderLayout.EAST);

        item.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { item.setBackground(new Color(35, 36, 55)); rightPanel.setBackground(new Color(35, 36, 55)); }
            public void mouseExited(MouseEvent e)  { item.setBackground(AppColors.BG_SIDEBAR); rightPanel.setBackground(AppColors.BG_SIDEBAR); }
            public void mouseClicked(MouseEvent e) {
                if (!(e.getSource() instanceof JButton)) {
                    clearGroupUnread(gid);
                    if (onGroupClick != null) onGroupClick.accept(gid, gname);
                }
            }
        });

        return item;
    }

    private JLabel createBadge(String text) {
        JLabel badge = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColors.UNREAD_BADGE);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        badge.setForeground(Color.WHITE);
        badge.setHorizontalAlignment(SwingConstants.CENTER);
        badge.setOpaque(false);
        int size = text.length() > 1 ? 20 : 18;
        badge.setPreferredSize(new Dimension(size, size));
        return badge;
    }
}

