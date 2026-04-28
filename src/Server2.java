import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Server2 {
    private static final int CONTROL_PORT = 1234;
    private static final int MESSAGE_PORT = 1235;

    private static Map<String, ClientHandler> clientHandlers = Collections.synchronizedMap(new HashMap<>());
    private static Map<PrintWriter, ClientHandler> messageWriters = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        System.out.println("Chat server started:");
        System.out.println("- Control port: " + CONTROL_PORT);
        System.out.println("- Message port: " + MESSAGE_PORT);

        // Start control connection listener
        new Thread(() -> startControlServer()).start();

        // Start message connection listener
        new Thread(() -> startMessageServer()).start();
    }

    // Handle control connections (username, chat mode, selections)
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

    // Handle message connections (actual chat messages)
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

    // Control Connection Handler (Configuration phase)
    private static class ControlHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private String chatMode;
        private String selectedTarget; // For single chat: recipient name, for multi: group name

        public ControlHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Step 1: Receive and register username
                username = in.readLine();
                if (username == null || username.trim().isEmpty()) {
                    username = "Anonymous";
                }
                System.out.println("[CONTROL] User registered: " + username);

                // Add client to registry
                clientHandlers.put(username, new ClientHandler(username));

                // Send confirmation
                out.println("USERNAME_CONFIRMED:" + username);

                // Step 2: Receive chat mode selection
                String modeMessage = in.readLine();
                if (modeMessage != null) {
                    if (modeMessage.equalsIgnoreCase("single")) {
                        chatMode = "single";
                        handleSingleChatMode();
                    } else if (modeMessage.equalsIgnoreCase("multi")) {
                        chatMode = "multi";
                        out.println("CHAT_MODE_CONFIRMED:multi");
                    } else if (modeMessage.equalsIgnoreCase("broadcast")) {
                        chatMode = "broadcast";
                        out.println("CHAT_MODE_CONFIRMED:broadcast");
                    }
                }

                // Step 3: Receive selected target/recipient
                String targetMessage = in.readLine();
                if (targetMessage != null) {
                    selectedTarget = targetMessage;
                    out.println("TARGET_CONFIRMED:" + selectedTarget);
                    System.out.println("[CONTROL] " + username + " -> Mode: " + chatMode + ", Target: " + selectedTarget);
                }

                // Keep connection alive for potential reconfiguration
                String command;
                while ((command = in.readLine()) != null) {
                    if (command.equalsIgnoreCase("exit")) {
                        break;
                    }
                    if (command.equalsIgnoreCase("disconnect")) {
                        break;
                    }
                }

            } catch (IOException e) {
                System.out.println("[CONTROL] Error for user " + username + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                    System.out.println("[CONTROL] Connection closed for: " + username);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleSingleChatMode() {
            // Send list of available users
            out.println("AVAILABLE_USERS:");
            for (String name : clientHandlers.keySet()) {
                if (!name.equals(username)) {
                    out.println(name);
                }
            }
            out.println("END_LIST");
        }
    }

    // Message Connection Handler (Chat phase)
    private static class MessageHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private String chatMode;
        private String selectedTarget;

        public MessageHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Receive session info (username:chatmode:target)
                String sessionInfo = in.readLine();

                if (sessionInfo != null) {
                    String[] parts = sessionInfo.split(":");
                    username = parts.length > 0 ? parts[0] : "Anonymous";
                    chatMode = parts.length > 1 ? parts[1] : "multi";
                    selectedTarget = parts.length > 2 ? parts[2] : "";

                    messageWriters.put(out, clientHandlers.get(username));
                    System.out.println("[MESSAGE] " + username + " started " + chatMode + " chat");
                }

                // Send welcome message
                out.println("[Server] Welcome to " + chatMode + " chat");

                // Read and broadcast messages
                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    if (clientMessage.equalsIgnoreCase("exit")) {
                        break;
                    }

                    System.out.println("[" + username + "] (" + chatMode + "): " + clientMessage);

                    if (chatMode.equalsIgnoreCase("single")) {
                        // Send to specific user only
                        sendToSpecificUser(selectedTarget, username + ": " + clientMessage);
                    } else if (chatMode.equalsIgnoreCase("multi")) {
                        // Broadcast to all except sender
                        broadcastMessageExcludeSender(username + ": " + clientMessage, out);
                    } else if (chatMode.equalsIgnoreCase("broadcast")) {
                        // Send to all including sender
                        broadcastMessageToAll(username + ": " + clientMessage);
                    }
                }

            } catch (IOException e) {
                System.out.println("[MESSAGE] Error: " + e.getMessage());
            } finally {
                try {
                    if (out != null) {
                        messageWriters.remove(out);
                    }
                    socket.close();
                    System.out.println("[MESSAGE] Connection closed for: " + username);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void broadcastMessageExcludeSender(String message, PrintWriter senderOut) {
            for (PrintWriter writer : messageWriters.keySet()) {
                if (!writer.equals(senderOut)) {
                    writer.println(message);
                }
            }
        }

        private void broadcastMessageToAll(String message) {
            for (PrintWriter writer : messageWriters.keySet()) {
                writer.println(message);
            }
        }

        private void sendToSpecificUser(String targetUsername, String message) {
            for (Map.Entry<PrintWriter, ClientHandler> entry : messageWriters.entrySet()) {
                if (entry.getValue().username.equals(targetUsername)) {
                    entry.getKey().println(message);
                    break;
                }
            }
        }
    }

    // Simple client handler for storing info
    private static class ClientHandler {
        String username;

        public ClientHandler(String username) {
            this.username = username;
        }
    }
}