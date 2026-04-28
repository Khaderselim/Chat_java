import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Server3 {
    private static final int SERVER_PORT = 1234;
    private static Map<PrintWriter, ClientHandler> clientHandlers = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        System.out.println("Chat server started on port " + SERVER_PORT);

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);

            while (true) {
                // Accept incoming client connections
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // Create a new thread to handle this client
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Inner class to handle individual client connections
    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                // Set up input and output streams
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Receive username from client
                username = in.readLine();
                if (username == null || username.trim().isEmpty()) {
                    username = "Anonymous";
                }

                // Add this client handler to the map
                clientHandlers.put(out, this);
                System.out.println("[" + username + "] connected. Total clients: " + clientHandlers.size());

                // Send a welcome message
                out.println("Welcome to the chat server, " + username + "!");

                // Notify other users that this user joined
                broadcastMessageExcludeSender("[Server]: " + username + " has joined the chat", out);

                // Read messages from the client and broadcast to other clients
                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    if (clientMessage.equalsIgnoreCase("exit")) {
                        break;
                    }
                    if (clientMessage.equalsIgnoreCase("list")) {
                        sendListofUsers();
                        continue;
                    }
//                    if(clientMessage.startsWith("send_to")) {
//                        String[] split = clientMessage.split(" ");
//                        split = Arrays.stream(split).skip(1).toArray(String[]::new);
//                        System.out.println(Arrays.toString(split));
//                        continue;
//                    }

                    System.out.println("[" + username + "]: " + clientMessage);

                    // Broadcast message to all connected clients EXCEPT the sender
                    broadcastMessageExcludeSender(username + ": " + clientMessage, out);
                }
            } catch (IOException e) {
                System.out.println("[" + username + "] disconnected unexpectedly");
            } finally {
                // Remove this client and close connections
                try {
                    if (out != null) {
                        clientHandlers.remove(out);
                    }
                    socket.close();
                    System.out.println("[" + username + "] removed. Total clients: " + clientHandlers.size());

                    // Notify other users that this user left
                    broadcastMessageToAll("[Server]: " + username + " has left the chat");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Broadcast message to all connected clients EXCEPT the sender
        private void broadcastMessageExcludeSender(String message, PrintWriter senderOut) {
            for (Map.Entry<PrintWriter, ClientHandler> entry : clientHandlers.entrySet()) {
                PrintWriter writer = entry.getKey();
                // Only send to clients other than the sender
                if (!writer.equals(senderOut)) {
                    writer.println(message);
                }
            }
        }

        // Broadcast message to all connected clients (including sender)
        private void broadcastMessageToAll(String message) {
            for (PrintWriter writer : clientHandlers.keySet()) {
                writer.println(message);
            }
        }

        private void sendListofUsers() {
            for (PrintWriter writer : clientHandlers.keySet()) {
                writer.println("List of users: " + clientHandlers.values().stream().map(handler -> handler.username).toList());
            }
        }

    }
}