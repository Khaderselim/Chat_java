import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private static final int CONTROL_PORT = 1234;
    private static final int MESSAGE_PORT = 1235;

    private static Map<String, ClientHandler> clientHandlers = Collections.synchronizedMap(new HashMap<>());
    public static Map<String, String> userMap = Collections.synchronizedMap(new HashMap<>());
    private static Map<PrintWriter, ClientHandler> messageWriters = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        System.out.println("Chat server started:");
        System.out.println("- Control port: " + CONTROL_PORT);
        System.out.println("- Message port: " + MESSAGE_PORT);

        new Thread(() -> startControlServer()).start();
        new Thread(() -> startMessageServer()).start();
    }

    private static void startControlServer() {
        try {
            ServerSocket controlSocket = new ServerSocket(CONTROL_PORT);
            while (true) {
                Socket clientSocket = controlSocket.accept();
                System.out.println("[CONTROL] New control connection: " + clientSocket.getInetAddress());
                new ControlHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startMessageServer() {
        try {
            ServerSocket messageSocket = new ServerSocket(MESSAGE_PORT);
            while (true) {
                Socket clientSocket = messageSocket.accept();
                System.out.println("[MESSAGE] New message connection: " + clientSocket.getInetAddress());
                new MessageHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Control Handler
    // -------------------------------------------------------------------------
    private static class ControlHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ControlHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // --- Step 1: register username ---
                username = in.readLine();
                if (username == null || username.trim().isEmpty()) username = "Anonymous";
                System.out.println("[CONTROL] User registered: " + username);

                clientHandlers.put(username, new ClientHandler(username));
                out.println("USERNAME_CONFIRMED:" + username);

                String command;
                while ((command = in.readLine()) != null) {

                    if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("disconnect")) {
                        break;
                    }

                    if (command.equalsIgnoreCase("single")) {
                        // Send available users list
                        sendAvailableUsers();

                        // Read the chosen target
                        String target = in.readLine();
                        if (target == null) break;

                        // Update userMap so MessageHandlers can do P2P lookup
                        userMap.put(username, target);
                        out.println("TARGET_CONFIRMED:" + target);
                        System.out.println("[CONTROL] " + username + " -> Target: " + target);
                    }
                    // Future: handle "group" or other modes here
                }

            } catch (IOException e) {
                System.out.println("[CONTROL] Error for user " + username + ": " + e.getMessage());
            } finally {
                clientHandlers.remove(username);
                userMap.remove(username);
                try { socket.close(); } catch (IOException ignored) {}
                System.out.println("[CONTROL] Connection closed for: " + username);
            }
        }

        private void sendAvailableUsers() {
            out.println("AVAILABLE_USERS:");
            for (String name : clientHandlers.keySet()) {
                if (!name.equals(username)) {
                    out.println(name);
                }
            }
            out.println("END_LIST");
        }
    }


    private static class MessageHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private String selectedTarget;

        public MessageHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Session info: username:target
                String sessionInfo = in.readLine();
                if (sessionInfo != null) {
                    String[] parts = sessionInfo.split(":", 2);
                    username       = parts.length > 0 ? parts[0] : "Anonymous";
                    selectedTarget = parts.length > 1 ? parts[1] : "";

                    // Update userMap with latest target (may differ from control-phase value
                    // if the user hit "New Chat" and the old message socket is still closing)
                    userMap.put(username, selectedTarget);
                    messageWriters.put(out, clientHandlers.getOrDefault(username, new ClientHandler(username)));
                    System.out.println("[MESSAGE] " + username + " connected, target=" + selectedTarget);
                }
                String fileName = getFileName(selectedTarget);
                File file = new File(fileName);
                if (file.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] split = line.split(": ", 2);
                            if (split[0].equals(username)){
                                line = "You: "+split[1];
                            }
                            out.println(line);
                        }
                    } catch (IOException e) {
                        System.out.println("[MESSAGE] Error reading file: " + e.getMessage());
                    }
                }else {
                    out.println("[Server] Welcome " + username + "! Chatting with: " + selectedTarget);
                }

                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    if (clientMessage.equalsIgnoreCase("exit")) break;

                    System.out.println("[" + username + "] " + clientMessage);

                    // P2P: deliver only to the intended target
                    // Verify via userMap that the target still expects messages from us
                    sendToSpecificUser(selectedTarget, username + ": " + clientMessage);
                }

            } catch (IOException e) {
                System.out.println("[MESSAGE] Error: " + e.getMessage());
            } finally {
                if (out != null) messageWriters.remove(out);
                if (username != null) userMap.remove(username);
                try { socket.close(); } catch (IOException ignored) {}
                System.out.println("[MESSAGE] Connection closed for: " + username);
            }
        }


        private void sendToSpecificUser(String targetUsername, String message) {
            // Check that the target's current partner is the sender (mutual P2P)
            String fileName = getFileName(targetUsername);
            String targetPartner = userMap.get(targetUsername);
            if (targetPartner == null || !targetPartner.equals(username)) {
                // Target is not currently paired with us; drop or optionally queue
                System.out.println("[MESSAGE] Dropped: " + targetUsername + " is not paired with " + username);
                return;
            }

            for (Map.Entry<PrintWriter, ClientHandler> entry : messageWriters.entrySet()) {
                if (entry.getValue().username.equals(targetUsername)) {
                    entry.getKey().println(message);
                    try (FileWriter fw = new FileWriter(fileName, true)) {
                        fw.write(message + "\n");
                    } catch (IOException e) {
                        System.out.println("[MESSAGE] Error writing to file: " + e.getMessage());

                    }
                    return;
                }
            }
            System.out.println("[MESSAGE] Target " + targetUsername + " not connected on message port yet.");
        }
        private String getFileName(String receiver) {
            return (username.compareTo(receiver) > 0) ? receiver + username + ".txt" : username + receiver + ".txt";
        }
    }

    // Simple client info holder
    private static class ClientHandler {
        String username;
        public ClientHandler(String username) { this.username = username; }
    }

}