import java.io.*;
import java.net.*;

public class ChatUser {
    public static void main(String[] args) {
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try {
                System.out.print("Enter server IP and port (format: ip:port): ");
                String[] serverDetails = console.readLine().split(":");
                String serverIP = serverDetails[0];
                int serverPort = Integer.parseInt(serverDetails[1]);

                try (Socket socket = new Socket(serverIP, serverPort)) {
                    System.out.println("Connected to chatroom!");

                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    Thread listener = getListener(socket);
                    listener.start();
                    String input;
                    while ((input = console.readLine()) != null) {
                        out.writeObject(input);
                        out.flush();
                    }
                } catch (Exception e) {
                    System.out.println("Failed to connect. Try again.");
                }
            } catch (IOException e) {
                System.out.println("Unable to connect to server: " + e.getMessage());
            }
        }
    }

    private static Thread getListener(Socket socket) throws IOException {
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        return new Thread(() -> {
            try {
                String message;
                while ((message = (String) in.readObject()) != null) {
                    System.out.println(message);
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Disconnected from server.");
                System.exit(0);
            }
        });
    }
}