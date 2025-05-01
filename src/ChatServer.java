import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static Set<ClientHandler> clients = new HashSet<>();

    public static void main(String[] args) {
        System.out.println("Сервер чата запущен...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Новое подключение: " + clientSocket);

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Ошибка в сервере: " + e.getMessage());
        }
    }

    public static void broadcast(String message, ClientHandler excludeClient) {
        for (ClientHandler client : clients) {
            if (client != excludeClient) {
                client.sendMessage(message);
            }
        }
    }

    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Клиент отключен: " + client.getClientSocket());
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public Socket getClientSocket() {
            return clientSocket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // Получаем имя клиента
                clientName = in.readLine();
                System.out.println(clientName + " присоединился к чату.");
                broadcast(clientName + " присоединился к чату.", this);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Сообщение от " + clientName + ": " + inputLine);
                    broadcast(clientName + ": " + inputLine, this);
                }
            } catch (IOException e) {
                System.out.println("Ошибка в обработчике клиента: " + e.getMessage());
            } finally {
                try {
                    if (clientSocket != null) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    System.out.println("Ошибка при закрытии сокета: " + e.getMessage());
                }
                ChatServer.removeClient(this);
                broadcast(clientName + " покинул чат.", this);
                System.out.println(clientName + " покинул чат.");
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }
}