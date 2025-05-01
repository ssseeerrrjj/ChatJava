import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Введите ваше имя: ");
        String name = scanner.nextLine();

        try {
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            System.out.println("Подключено к серверу чата. Можете начинать общение.");

            // Поток для отправки сообщений
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Отправляем имя серверу
            out.println(name);

            // Поток для получения сообщений
            new Thread(new MessageReader(socket)).start();

            // Чтение сообщений с консоли и отправка на сервер
            String userInput;
            while (true) {
                userInput = scanner.nextLine();
                out.println(userInput);

                if ("/exit".equalsIgnoreCase(userInput)) {
                    break;
                }
            }

            socket.close();
            scanner.close();
        } catch (IOException e) {
            System.err.println("Ошибка в клиенте: " + e.getMessage());
        }
    }

    private static class MessageReader implements Runnable {
        private Socket socket;

        public MessageReader(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                String serverResponse;
                while ((serverResponse = in.readLine()) != null) {
                    System.out.println(serverResponse);
                }
            } catch (IOException e) {
                System.out.println("Соединение с сервером прервано.");
            }
        }
    }
}