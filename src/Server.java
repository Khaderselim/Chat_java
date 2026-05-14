import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    static final int CONTROL_PORT = 1234;
    static final int MESSAGE_PORT = 1235;
    static final int VOICE_PORT = 1236;
    static final String HISTORY_DIR = "history";

    static final Map<String, ControlHandler> controlHandlers = new ConcurrentHashMap<>();
    static final Map<String, PrintWriter> messageWriters = new ConcurrentHashMap<>();
    static final Map<String, OutputStream> voiceOutputs = new ConcurrentHashMap<>();
    static final Map<String, Group> groups = new ConcurrentHashMap<>();

    private static final AtomicInteger groupIdCounter = new AtomicInteger(1);

    static boolean isValidMessage(String msg) {
        if (msg == null) return false;
        String t = msg.trim().toLowerCase();
        return !t.isEmpty();
    }

    static class Group {
        final String id, name, creator;
        final List<String> members = new CopyOnWriteArrayList<>();
        Group(String id, String name, String creator) {
            this.id = id; this.name = name; this.creator = creator;
            members.add(creator);
        }
        String serialize() {
            return id + ":" + name + ":" + creator + ":" + String.join(",", members);
        }
    }

    public static void main(String[] args) {
        new File(HISTORY_DIR).mkdirs();

        new Thread(Server::startControlServer, "ctrl").start();
        new Thread(Server::startMessageServer, "msg").start();
        new Thread(Server::startVoiceServer, "voice").start();
    }

    static void startControlServer() {
        try (ServerSocket ss = new ServerSocket(CONTROL_PORT)) {
            System.out.println("Control server listening on port " + CONTROL_PORT);
            while (true) new ControlHandler(ss.accept()).start();
        } catch (IOException e) { e.printStackTrace(); }
    }
    static void startMessageServer() {
        try (ServerSocket ss = new ServerSocket(MESSAGE_PORT)) {
            System.out.println("Message server listening on port " + MESSAGE_PORT);
            while (true) new MessageHandler(ss.accept()).start();
        } catch (IOException e) { e.printStackTrace(); }
    }
    static void startVoiceServer() {
        try (ServerSocket ss = new ServerSocket(VOICE_PORT)) {
            System.out.println("Voice server listening on port " + VOICE_PORT);
            while (true) new VoiceHandler(ss.accept()).start();
        } catch (IOException e) { e.printStackTrace(); }
    }


    static class ControlHandler extends Thread {
        final Socket socket;
        PrintWriter out;
        BufferedReader in;
        Connection conn;
        String username;
        String password;
        boolean online = false;
        String url = "jdbc:oracle:thin:@localhost:1521:XE";
        String user = "messanger";
        String pass = "system";


        ControlHandler(Socket s) {
            this.socket = s;
        }

        @Override public void run() {
            try {
                Class.forName("oracle.jdbc.driver.OracleDriver");

                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                conn = DriverManager.getConnection(url, user, pass);
                String first = in.readLine();
                String second = in.readLine();
                String third = in.readLine();
                if (first == null) return;
                username = second.split(":", 2)[1];
                password = third.split(":", 2)[1];
                if (first.equals("REGISTER") && username != null && password != null) {
                    String query = "SELECT COUNT(*) FROM USERS WHERE USERNAME = ?";
                    PreparedStatement ps = conn.prepareStatement(query);
                    ps.setString(1, username);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && rs.getInt(1) > 0) {
                        System.out.println("ERROR:USERNAME_TAKEN");
                        out.println("ERROR:USERNAME_TAKEN");
                        return;
                    }else {
                        query = "INSERT INTO USERS (ID,USERNAME, PASSWORD) VALUES (seq_user.nextval,?, ?)";
                        ps = conn.prepareStatement(query);
                        ps.setString(1, username);
                        ps.setString(2, password);
                        ps.executeUpdate();
                        System.out.println("[DB] User '" + username + "' inserted into database");
                    }
                }else{
                String query = "SELECT PASSWORD FROM USERS WHERE USERNAME = ?";
                PreparedStatement ps = conn.prepareStatement(query);
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (!rs.next() || !rs.getString("PASSWORD").equals(password)) {
                    System.out.println("ERROR:INVALID_USERNAME_OR_PASSWORD");
                    out.println("ERROR:INVALID_USERNAME_OR_PASSWORD");
                    return;
                }
                System.out.println(password);
            }
                ControlHandler existing = controlHandlers.get(username);
                if (existing != null && existing.online) {
                    out.println("ERROR:USERNAME_TAKEN");
                    return;
                }

                controlHandlers.put(username, this);
                online = true;
                out.println("user:" + username);
                System.out.println("[CTRL] + " + username);
                broadcastExcept("USER_ONLINE:" + username, username);

                String line;
                while ((line = in.readLine()) != null) handleCommand(line.trim());

            } catch (IOException e) {
                System.out.println("[CTRL] Disconnected : " + username);
            } catch (ClassNotFoundException | SQLException e) {
                throw new RuntimeException(e);
            } finally { cleanup(); }
        }

        void handleCommand(String cmd) throws SQLException {
            if (cmd.equalsIgnoreCase("DISCONNECT")) { cleanup(); return; }
            if (cmd.equals("LIST_USERS"))  { sendUserList();  return; }
            if (cmd.equals("LIST_GROUPS")) {
                load_groups();
                sendGroupList();
                return;
            }

            if (cmd.startsWith("GET_HISTORY:")) {
                sendPrivateHistory(cmd.split(":",2)[1]);
                return;
            }
            if (cmd.startsWith("GET_GROUP_HISTORY:")) {
                System.out.println(groups);
                sendGroupHistory(cmd.split(":",2)[1]);
                return;
            }
            if (cmd.startsWith("CREATE_GROUP:")) {
                String gname = cmd.split(":",2)[1];
                if (gname.isEmpty()) { out.println("ERROR:EMPTY_GROUP_NAME"); return; }
                String gid = "G" + groupIdCounter.getAndIncrement();
                Group g = new Group(gid, gname, username);
                groups.put(gid, g);

                String filename = "group_" + gid + ".txt";
                String sql = "INSERT INTO GROUP_CHAT (ID, GID, NAME, FILENAME, CREATOR, MEMBERS) VALUES (seq_group.nextval, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, gid);
                    ps.setString(2, gname);
                    ps.setString(3, filename);
                    ps.setString(4, username);
                    ps.setString(5, String.join(",", g.members));
                    ps.executeUpdate();
                    System.out.println("[DB] Group '" + gname + "' inserted into database");
                } catch (SQLException e) {
                    System.err.println("[DB] Error inserting group: " + e.getMessage());
                    out.println("ERROR:DATABASE_INSERT_FAILED");
                    return;
                }

                out.println("GROUP_CREATED:" + g.serialize());
                System.out.println("[GROUP] '" + gname + "' created by " + username);
                return;
            }
            if (cmd.startsWith("ADD_MEMBER:")) {
                String[] p = cmd.substring(11).split(":", 2);
                if (p.length < 2) return;
                String gid = p[0], target = p[1];
                Group g = groups.get(gid);
                if (g == null) { out.println("ERROR:GROUP_NOT_FOUND"); return; }
                if (!g.members.contains(username)) { out.println("ERROR:NOT_MEMBER"); return; }
                if (g.members.contains(target)) { out.println("ERROR:ALREADY_MEMBER"); return; }
                g.members.add(target);
                System.out.println(g.members);
                String sql = "UPDATE GROUP_CHAT SET MEMBERS = ? WHERE GID = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, String.join(",", g.members));
                    ps.setString(2, gid);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    System.err.println("[DB] Error updating group: " + e.getMessage());
                    out.println("ERROR:DATABASE_INSERT_FAILED");
                    return;
                }
                out.println("MEMBER_ADDED:" + gid + ":" + target);
                ControlHandler ch = controlHandlers.get(target);
                if (ch != null && ch.online) ch.send("ADDED_TO_GROUP:" + g.serialize());
                System.out.println("[GROUP] " + target + " added to '" + g.name + "'");
            }
        }

        void sendUserList() {
            out.println("USER_LIST_START");
            for (Map.Entry<String, ControlHandler> e : controlHandlers.entrySet())
                if (!e.getKey().equals(username))
                    out.println("USER:" + e.getKey() + ":" + (e.getValue().online ? "ONLINE" : "OFFLINE"));
            out.println("USER_LIST_END");
        }
        void load_groups() throws SQLException {
            String query = "SELECT * FROM GROUP_CHAT";
            try (PreparedStatement ps = conn.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                int id = 0;
                while (rs.next()) {
                    String gid = rs.getString("GID");
                    String group_name = rs.getString("NAME");
                    String creator = rs.getString("CREATOR");
                    Group g = new Group(gid, group_name, creator);
                    String membersStr = rs.getString("MEMBERS");
                    id = Integer.parseInt(rs.getString("ID"));

                    g.members.clear();

                    if (membersStr != null && !membersStr.trim().isEmpty()) {
                        String[] members = membersStr.split(",");
                        g.members.addAll(Arrays.asList(members));
                    }
                    groups.put(gid, g);
                }
                groupIdCounter.set(id + 1);
            }
        }
        void sendGroupList() {
            out.println("GROUP_LIST_START");
            for (Group g : groups.values())
                if (g.members.contains(username)) out.println("GROUP:" + g.serialize());
            out.println("GROUP_LIST_END");
        }

        void sendPrivateHistory(String target) {
            out.println("HISTORY_START:PRIVATE:" + target);
            File f = new File(HISTORY_DIR, histFile(username, target));
            if (f.exists()) {
                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = r.readLine()) != null) out.println("HISTORY_LINE:" + line);
                } catch (IOException ignored) {}
            }
            out.println("HISTORY_END");
        }

        void sendGroupHistory(String gid) {
            out.println("HISTORY_START:GROUP:" + gid);
            File f = new File(HISTORY_DIR, "group_" + gid + ".txt");
            if (f.exists()) {
                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = r.readLine()) != null) out.println("HISTORY_LINE:" + line);
                } catch (IOException ignored) {}
            }
            out.println("HISTORY_END");
        }

        void send(String msg) { if (out != null) out.println(msg); }

        void cleanup() {
            online = false;
            if (username != null) {
                broadcastExcept("USER_OFFLINE:" + username, username);
                System.out.println("[CTRL] - " + username);
            }
            try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        }

        static void broadcastExcept(String msg, String exclude) {
            for (Map.Entry<String, ControlHandler> e : controlHandlers.entrySet())
                if (!e.getKey().equals(exclude) && e.getValue().online) e.getValue().send(msg);
        }

        static String histFile(String a, String b) {
            return (a.compareTo(b) > 0 ? b + "_" + a : a + "_" + b) + ".txt";
        }
    }

    static class MessageHandler extends Thread {
        final Socket socket;
        String username;

        MessageHandler(Socket s) { this.socket = s; setDaemon(true); }

        @Override public void run() {
            try {
                PrintWriter    out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String reg = in.readLine();
                if (reg == null || !reg.startsWith("REGISTER:")) return;
                username = reg.substring(9).trim();
                messageWriters.put(username, out);
                System.out.println("[MSG] + " + username);

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("PRIVATE:")) {
                        String[] p = line.substring(8).split(":", 3);
                        if (p.length < 3) continue;
                        if (!isValidMessage(p[2])) continue;
                        deliverPrivate(p[0], p[1], p[2]);

                    } else if (line.startsWith("GROUP:")) {
                        String[] p = line.substring(6).split(":", 3);
                        if (p.length < 3) continue;
                        if (!isValidMessage(p[2])) continue;
                        deliverGroup(p[0], p[1], p[2]);

                    } else if (line.startsWith("VOICE_MSG_PRIVATE:")) {
                        String[] p = line.substring(18).split(":", 4);
                        if (p.length < 4) continue;
                        deliverVoicePrivate(p[0], p[1], p[2], p[3]);

                    } else if (line.startsWith("VOICE_MSG_GROUP:")) {
                        String[] p = line.substring(16).split(":", 4);
                        if (p.length < 4) continue;
                        deliverVoiceGroup(p[0], p[1], p[2], p[3]);
                    }
                }
            } catch (IOException e) {
                System.out.println("[MSG] Disconnected : " + username);
            } finally {
                if (username != null) messageWriters.remove(username);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        void deliverPrivate(String from, String to, String msg) {
            PrintWriter w = messageWriters.get(to);
            if (w != null) w.println("PRIVATE:" + from + ":" + msg);
            save(ControlHandler.histFile(from, to), from + ": " + msg);
            System.out.println("[MSG] " + from + "→" + to + ": " + msg);
        }

        void deliverGroup(String from, String gid, String msg) {
            Group g = groups.get(gid);
            if (g == null) return;
            for (String m : g.members) {
                if (!m.equals(from)) {
                    PrintWriter w = messageWriters.get(m);
                    if (w != null) w.println("GROUP:" + gid + ":" + g.name + ":" + from + ":" + msg);
                }
            }
            save("group_" + gid + ".txt", from + ": " + msg);
        }

        void deliverVoicePrivate(String from, String to, String dur, String b64) {
            PrintWriter w = messageWriters.get(to);
            if (w != null) w.println("VOICE_PRIVATE:" + from + ":" + dur + ":" + b64);
            save(ControlHandler.histFile(from, to), from + ": VOICE_MSG:" + dur + ":" + b64);
        }

        void deliverVoiceGroup(String from, String gid, String dur, String b64) {
            Group g = groups.get(gid);
            if (g == null) return;
            for (String m : g.members) {
                if (!m.equals(from)) {
                    PrintWriter w = messageWriters.get(m);
                    if (w != null) w.println("VOICE_GROUP:" + gid + ":" + g.name + ":" + from + ":" + dur + ":" + b64);
                }
            }
            save("group_" + gid + ".txt", from + ": VOICE_MSG:" + dur + ":" + b64);
        }

        void save(String filename, String line) {
            try {
                new File(HISTORY_DIR).mkdirs();
                try (FileWriter fw = new FileWriter(new File(HISTORY_DIR, filename), true)) {
                    fw.write(line + "\n");
                }
            } catch (IOException ignored) {}
        }
    }

    static class VoiceHandler extends Thread {
        final Socket socket;
        String username;

        VoiceHandler(Socket s) { this.socket = s; setDaemon(true); }

        @Override public void run() {
            try {
                InputStream  in  = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                String h1 = readLine(in);
                if (h1 == null || !h1.startsWith("REGISTER_VOICE:")) return;
                username = h1.substring(15).trim();
                voiceOutputs.put(username, out);
                System.out.println("[VOICE] + " + username);

                String h2 = readLine(in);
                if (h2 == null) return;

                if (h2.startsWith("PRIVATE_VOICE:")) {
                    String[] p = h2.substring(14).split(":", 2);
                    if (p.length == 2) relayPrivate(in, p[0], p[1]);
                } else if (h2.startsWith("GROUP_VOICE:")) {
                    String[] p = h2.substring(12).split(":", 2);
                    if (p.length == 2) relayGroup(in, p[0], p[1]);
                }
            } catch (IOException e) {
                System.out.println("[VOICE] Disconnected : " + username);
            } finally {
                if (username != null) voiceOutputs.remove(username);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        void relayPrivate(InputStream in, String from, String to) throws IOException {
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                OutputStream t = voiceOutputs.get(to);
                if (t != null) try { t.write(buf, 0, n); t.flush(); } catch (IOException ignored) {}
            }
        }

        void relayGroup(InputStream in, String from, String gid) throws IOException {
            Group g = groups.get(gid);
            if (g == null) return;
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                final byte[] data = Arrays.copyOf(buf, n);
                for (String m : g.members) {
                    if (!m.equals(from)) {
                        OutputStream o = voiceOutputs.get(m);
                        if (o != null) try { o.write(data); o.flush(); } catch (IOException ignored) {}
                    }
                }
            }
        }

        String readLine(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') break;
                if (b != '\r') sb.append((char)b);
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
    }
}

