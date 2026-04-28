import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private static final int CONTROL_PORT = 1234;
    private static final int MESSAGE_PORT = 1235;
    private static final int VOICE_PORT = 1236;

    private static final Map<String, ClientHandler> clientHandlers = Collections.synchronizedMap(new HashMap<>());
    public  static final Map<String, String>userMap = Collections.synchronizedMap(new HashMap<>());
    private static final Map<PrintWriter, ClientHandler> messageWriters = Collections.synchronizedMap(new HashMap<>());

    private static final Map<String, OutputStream> voiceOutputs = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {

        new Thread(Server::startControlServer).start();
        new Thread(Server::startMessageServer).start();
        new Thread(Server::startVoiceServer).start();
    }


    private static void startControlServer() {
        try (ServerSocket ss = new ServerSocket(CONTROL_PORT)) {
            while (true) { new ControlHandler(ss.accept()).start(); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void startMessageServer() {
        try (ServerSocket ss = new ServerSocket(MESSAGE_PORT)) {
            while (true) { new MessageHandler(ss.accept()).start(); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void startVoiceServer() {
        try (ServerSocket ss = new ServerSocket(VOICE_PORT)) {
            while (true) { new VoiceHandler(ss.accept()).start(); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static class ControlHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        ControlHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                username = in.readLine();
                if (username == null || username.trim().isEmpty()) username = "Anonymous";
                System.out.println("[CONTROL] Registered: " + username);

                clientHandlers.put(username, new ClientHandler(username));
                out.println("USERNAME_CONFIRMED:" + username);

                String command;
                while ((command = in.readLine()) != null) {
                    if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("disconnect")) break;

                    if (command.equalsIgnoreCase("single")) {
                        sendAvailableUsers();
                        String target = in.readLine();
                        if (target == null) break;
                        userMap.put(username, target);
                        out.println("TARGET_CONFIRMED:" + target);
                        System.out.println("[CONTROL] " + username + " -> " + target);
                    }
                }
            } catch (IOException e) {
                System.out.println("[CONTROL] Error for " + username + ": " + e.getMessage());
            } finally {
                clientHandlers.remove(username);
                userMap.remove(username);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void sendAvailableUsers() {
            out.println("AVAILABLE_USERS:");
            for (String name : clientHandlers.keySet()) {
                if (!name.equals(username)) out.println(name);
            }
            out.println("END_LIST");
        }
    }


    private static class MessageHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private String selectedTarget;

        MessageHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String sessionInfo = in.readLine();
                if (sessionInfo != null) {
                    String[] parts = sessionInfo.split(":", 2);
                    username = parts.length > 0 ? parts[0] : "Anonymous";
                    selectedTarget = parts.length > 1 ? parts[1] : "";
                    userMap.put(username, selectedTarget);
                    messageWriters.put(out, clientHandlers.getOrDefault(username, new ClientHandler(username)));
                    System.out.println("[MESSAGE] " + username + " connected, target=" + selectedTarget);
                }

                // Send chat history if it exists
                File file = new File(getFileName(username, selectedTarget));
                if (file.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] split = line.split(": ", 2);
                            out.println(split[0].equals(username) ? "You: " + split[1] : line);
                        }
                    }
                } else {
                    out.println("[Server] Welcome " + username + "! Chatting with: " + selectedTarget);
                }

                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    if (clientMessage.equalsIgnoreCase("exit")) break;
                    System.out.println("[" + username + "] " + clientMessage);
                    sendToSpecificUser(selectedTarget, username + ": " + clientMessage);
                }

            } catch (IOException e) {
                System.out.println("[MESSAGE] Error: " + e.getMessage());
            } finally {
                if (out != null) messageWriters.remove(out);
                if (username != null) userMap.remove(username);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void sendToSpecificUser(String targetUsername, String message) {
            String targetPartner = userMap.get(targetUsername);
            if (targetPartner == null || !targetPartner.equals(username)) {
                System.out.println("[MESSAGE] Dropped (not paired): " + targetUsername);
                return;
            }
            for (Map.Entry<PrintWriter, ClientHandler> entry : messageWriters.entrySet()) {
                if (entry.getValue().username.equals(targetUsername)) {
                    entry.getKey().println(message);
                    try (FileWriter fw = new FileWriter(getFileName(username, targetUsername), true)) {
                        fw.write(message + "\n");
                    } catch (IOException e) {
                        System.out.println("[MESSAGE] History write error: " + e.getMessage());
                    }
                    return;
                }
            }
            System.out.println("[MESSAGE] Target " + targetUsername + " not on message port.");
        }

        private String getFileName(String a, String b) {
            return (a.compareTo(b) > 0) ? b + a + ".txt" : a + b + ".txt";
        }
    }


    private static class VoiceHandler extends Thread {
        private final Socket socket;
        private String username;
        private String selectedTarget;

        VoiceHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                InputStream  in  = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                String header = readLine(in);
                if (header == null) return;

                String[] parts = header.split(":", 2);
                username = parts.length > 0 ? parts[0] : "Unknown";
                selectedTarget = parts.length > 1 ? parts[1] : "";
                voiceOutputs.put(username, out);
                System.out.println("[VOICE] " + username + " connected, target=" + selectedTarget);
                byte[] buf = new byte[1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    OutputStream targetOut = voiceOutputs.get(selectedTarget);
                    if (targetOut != null) {
                        try {
                            targetOut.write(buf, 0, n);
                            targetOut.flush();
                        } catch (IOException e) {
                            System.out.println("[VOICE] Relay write failed: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("[VOICE] Error for " + username + ": " + e.getMessage());
            }
            if (username != null) voiceOutputs.remove(username);
            try {
                socket.close();
            } catch (IOException ex) {

            }
            System.out.println("[VOICE] Closed for: " + username);

        }


        private String readLine(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') break;
                if (b != '\r') sb.append((char) b);
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
    }


    private static class ClientHandler {
        final String username;
        ClientHandler(String username) {
            this.username = username;
        }
    }
}