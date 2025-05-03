import java.io.*;
import java.net.*;
import java.util.*;
import java.time.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static final String USER_DB = "users.txt";
    private static final String LOGS_DIR = "chat_logs";
    private static final String OFFLINE_MSG_DIR = "offline_messages";
    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Map<String, String> userDatabase = new HashMap<>();

    public static void main(String[] args) {
        loadUserDatabase();
        setupDirectories();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);
            logSystemMessage("Сервер запущен");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            logSystemMessage("Ошибка сервера: " + e.getMessage());
        }
    }

    private static void loadUserDatabase() {
        try (BufferedReader reader = new BufferedReader(new FileReader(USER_DB))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    userDatabase.put(parts[0], parts[1]);
                }
            }
            System.out.println("Загружено " + userDatabase.size() + " пользователей");
        } catch (IOException e) {
            System.err.println("Ошибка загрузки базы пользователей: " + e.getMessage());
        }
    }

    private static void setupDirectories() {
        try {
            Files.createDirectories(Paths.get(LOGS_DIR));
            Files.createDirectories(Paths.get(OFFLINE_MSG_DIR));
        } catch (IOException e) {
            System.err.println("Ошибка создания папок: " + e.getMessage());
        }
    }

    public static void logSystemMessage(String message) {
        logToFile("system", "SYSTEM", message);
    }

    public static void logPrivateMessage(String sender, String receiver, String message) {
        logToFile(getChatId(sender, receiver), sender, message);
    }

    private static void logToFile(String logName, String sender, String message) {
        String logFile = LOGS_DIR + "/" + logName + ".log";
        String logEntry = LocalDateTime.now() + " [" + sender + "]: " + message;

        try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
            out.println(logEntry);
        } catch (IOException e) {
            System.err.println("Ошибка записи в лог: " + e.getMessage());
        }
    }

    private static String getChatId(String user1, String user2) {
        return user1.compareTo(user2) < 0 ? user1 + "_" + user2 : user2 + "_" + user1;
    }

    public static void saveOfflineMessage(String receiver, String message) {
        String offlineFile = OFFLINE_MSG_DIR + "/" + receiver + ".msg";
        try (PrintWriter out = new PrintWriter(new FileWriter(offlineFile, true))) {
            out.println(message);
        } catch (IOException e) {
            System.err.println("Ошибка сохранения оффлайн сообщения: " + e.getMessage());
        }
    }

    public static List<String> loadOfflineMessages(String username) {
        List<String> messages = new ArrayList<>();
        String offlineFile = OFFLINE_MSG_DIR + "/" + username + ".msg";

        try {
            if (Files.exists(Paths.get(offlineFile))) {
                messages.addAll(Files.readAllLines(Paths.get(offlineFile)));
                Files.delete(Paths.get(offlineFile));
            }
        } catch (IOException e) {
            System.err.println("Ошибка загрузки оффлайн сообщений: " + e.getMessage());
        }

        return messages;
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private String currentRecipient;
        private boolean authenticated;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            this.authenticated = false;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                if (!authenticateUser()) {
                    return;
                }

                clients.put(username, this);
                sendWelcomeMessage();
                sendOfflineMessages();
                showAvailableUsers();

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    if (inputLine.startsWith("/select ")) {
                        handleSelectCommand(inputLine);
                    } else if (inputLine.startsWith("/users")) {
                        showAvailableUsers();
                    } else if (inputLine.startsWith("/exit")) {
                        break;
                    } else if (currentRecipient != null) {
                        sendPrivateMessage(inputLine);
                    } else {
                        out.println("ОШИБКА Сначала выберите получателя (/select [user])");
                    }
                }
            } catch (IOException e) {
                System.out.println("Ошибка с клиентом " + username + ": " + e.getMessage());
            } finally {
                disconnectClient();
            }
        }

        private boolean authenticateUser() throws IOException {
            for (int attempts = 0; attempts < 3; attempts++) {
                out.println("AUTH_REQUEST Введите логин:");
                String login = in.readLine();
                out.println("AUTH_REQUEST Введите пароль:");
                String password = in.readLine();

                if (userDatabase.containsKey(login) && userDatabase.get(login).equals(password)) {
                    username = login;
                    authenticated = true;
                    out.println("AUTH_SUCCESS Добро пожаловать, " + username + "!");
                    logSystemMessage(username + " авторизовался");
                    return true;
                }

                out.println("AUTH_FAIL Неверный логин или пароль. Попыток осталось: " + (2 - attempts));
            }

            out.println("AUTH_FAIL Превышено количество попыток");
            clientSocket.close();
            return false;
        }

        private void handleSelectCommand(String command) {
            String[] parts = command.split(" ");
            if (parts.length == 2) {
                String recipient = parts[1];

                if (userDatabase.containsKey(recipient)) {
                    currentRecipient = recipient;
                    out.println("SERVER_MSG Выбран получатель: " + recipient);
                    showChatHistory(recipient);
                } else {
                    out.println("ОШИБКА Пользователь " + recipient + " не существует");
                }
            } else {
                out.println("ОШИБКА Формат: /select [user]");
            }
        }

        private void sendPrivateMessage(String message) {
            ClientHandler recipient = clients.get(currentRecipient);

            if (recipient != null) {
                recipient.out.println("PRIVATE_MSG [" + username + "]: " + message);
                out.println("PRIVATE_MSG [вы → " + currentRecipient + "]: " + message);
                logPrivateMessage(username, currentRecipient, message);
            } else {
                saveOfflineMessage(currentRecipient, "OFFLINE_MSG [" + username + "]: " + message);
                out.println("SERVER_MSG Сообщение для " + currentRecipient + " будет доставлено при входе");
                logPrivateMessage(username, currentRecipient, "(оффлайн) " + message);
            }
        }

        private void sendWelcomeMessage() {
            out.println("SERVER_MSG Добро пожаловать в приватный чат!");
            out.println("SERVER_MSG Доступные команды:");
            out.println("SERVER_MSG /select [user] - выбрать собеседника");
            out.println("SERVER_MSG /users - показать всех пользователей");
            out.println("SERVER_MSG /exit - выйти из чата");
        }

        private void sendOfflineMessages() {
            List<String> messages = loadOfflineMessages(username);
            if (!messages.isEmpty()) {
                out.println("SERVER_MSG У вас новые сообщения:");
                messages.forEach(out::println);
            }
        }

        private void showAvailableUsers() {
            out.println("SERVER_MSG Доступные пользователи:");
            userDatabase.keySet().forEach(user -> {
                String status = clients.containsKey(user) ? " (онлайн)" : " (оффлайн)";
                out.println("SERVER_MSG - " + user + status);
            });
        }

        private void showChatHistory(String recipient) {
            String chatId = getChatId(username, recipient);
            String logFile = LOGS_DIR + "/" + chatId + ".log";

            try {
                if (Files.exists(Paths.get(logFile))) {
                    out.println("HISTORY_START");
                    Files.lines(Paths.get(logFile))
                            .forEach(line -> out.println("HISTORY_LINE " + line));
                    out.println("HISTORY_END");
                } else {
                    out.println("HISTORY_EMPTY");
                }
            } catch (IOException e) {
                System.err.println("Ошибка чтения истории: " + e.getMessage());
                out.println("HISTORY_ERROR");
            }
        }

        private void disconnectClient() {
            if (authenticated) {
                clients.remove(username);
                logSystemMessage(username + " покинул чат");
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Ошибка закрытия сокета: " + e.getMessage());
            }
        }
    }
}