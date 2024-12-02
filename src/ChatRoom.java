import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ChatRoom {
    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter port to start server: ");
        int port = Integer.parseInt(console.readLine());
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chatroom server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                Thread clientThread = new Thread(handler);
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }

    }

    public static synchronized void addClient(String clientId, ClientHandler clientHandler) {
        clients.put(clientId, clientHandler);
        String joinMessage = clientId + " has joined the chatroom. (" + clients.size() + " users connected)";
        System.out.println(joinMessage);
        broadcast(joinMessage, null);
        printClientList();
    }

    public static synchronized void removeClient(String clientId) {
        if (clients.containsKey(clientId)) {
            clients.remove(clientId);
            String leaveMessage = clientId + " has left the chatroom. (" + clients.size() + " users connected)";
            System.out.println(leaveMessage);
            broadcast(leaveMessage, null);
            printClientList();
        } else {
            System.out.println("Attempted to remove non-existent client: " + clientId);
        }
    }

    public static synchronized void renameClient(String oldName, String newName, ClientHandler clientHandler) {
        clients.remove(oldName);
        clients.put(newName, clientHandler);
    }

    public static synchronized void broadcast(String message, ClientHandler excludeClient) {
        if (excludeClient != null) {
            System.out.println(message);
        }
        for (ClientHandler client : clients.values()) {
            if (client != excludeClient) {
                client.sendMessage(message);
            }
        }
    }

    public static synchronized void printClientList() {
        System.out.println("Current clients: " + clients.keySet());
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String clientId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientId = "anonymous";
        }

        @Override
        public void run() {
            try {
                in = new ObjectInputStream(socket.getInputStream());
                out = new ObjectOutputStream(socket.getOutputStream());

                this.clientId = ChatRoom.generateUniqueName();
                ChatRoom.addClient(clientId, this);

                sendMessage("Welcome to the chatroom!");

                String message;
                while ((message = (String) in.readObject()) != null) {
                    if (message.startsWith("/rename")) {
                        handleRename(message);
                    } else if (message.equals("/leave")) {
                        handleLeave();
                        break;
                    } else if (message.startsWith("/pm ")) {
                        handlePrivateMessage(message);
                    } else {
                        ChatRoom.broadcast(clientId + ": " + message, this);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println(clientId + " disconnected unexpectedly.");
            } finally {
                handleLeave();
            }
        }

        private void handlePrivateMessage(String clientMessage) {
            String[] parts = clientMessage.substring(4).split(" ", 2);
            if (parts.length < 2) {
                sendMessage("Usage: /pm [username(s)] [message]");
                return;
            }

            String[] targetUsernames = parts[0].split(", ");
            String privateMessage = parts[1];

            for (String username : targetUsernames) {
                username = username.trim();
                ClientHandler targetClient = ChatRoom.clients.get(username);

                if (targetClient != null) {
                    String formattedMessage = clientId + "(pm): " + privateMessage;
                    System.out.println(formattedMessage);
                    targetClient.sendMessage(formattedMessage);
                } else {
                    sendMessage("User '" + username + "' not found.");
                }
            }
        }

        private void sendMessage(String message) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                System.out.println("Error sending message to " + clientId);
            }
        }

        private void handleRename(String command) {
            String[] parts = command.split(" ", 2);
            if (parts.length < 2) {
                sendMessage("Usage: /rename [new_name]");
                return;
            }

            String newName = parts[1].trim();
            if (newName.isEmpty()) {
                sendMessage("Usage: /rename [new_name]");
                return;
            }

            if (ChatRoom.clientExists(newName)) {
                sendMessage("Can't rename, username already taken!");
                return;
            }

            String oldName = this.clientId;
            this.clientId = newName;
            ChatRoom.renameClient(oldName, newName, this);
            sendMessage("You changed your name to " + newName);
            String renameMessage = oldName + " changed their name to " + newName;
            ChatRoom.broadcast(renameMessage, this);
            printClientList();
        }

        private boolean isLeaving = false;

        private void handleLeave() {
            if (isLeaving) {
                return;
            }
            isLeaving = true;
            ChatRoom.removeClient(clientId);
            closeConnections();
        }

        private void closeConnections() {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing input stream for " + clientId);
            }

            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing output stream for " + clientId);
            }

            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing socket for " + clientId);
            }
        }
    }

    public static synchronized boolean clientExists(String clientId) {
        return clients.containsKey(clientId);
    }

    private static synchronized String generateUniqueName() {
        String newName = "anonymous";
        int counter = 1;

        while (clients.containsKey(newName)) {
            newName = "anonymous" + "(" + counter + ")";
            counter++;
        }

        return newName;
    }
}