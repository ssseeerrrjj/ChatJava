import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 12345);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            new Thread(() -> {
                try {
                    String serverResponse;
                    while ((serverResponse = in.readLine()) != null) {
                        if (serverResponse.startsWith("PRIVATE_MSG")) {
                            System.out.println("\n" + serverResponse.substring(12));
                        }
                        else if (serverResponse.startsWith("HISTORY_START")) {
                            System.out.println("\n--- История переписки ---");
                        }
                        else if (serverResponse.startsWith("HISTORY_LINE")) {
                            System.out.println(serverResponse.substring(12));
                        }
                        else if (serverResponse.startsWith("HISTORY_END")) {
                            System.out.println("--- Конец истории ---\n");
                        }
                        else if (serverResponse.startsWith("HISTORY_EMPTY")) {
                            System.out.println("\nУ вас еще нет истории переписки с этим пользователем\n");
                        }
                        else if (serverResponse.startsWith("SERVER_MSG")) {
                            System.out.println(serverResponse.substring(11));
                        }
                        else if (serverResponse.startsWith("AUTH_")) {
                            System.out.println(serverResponse.substring(10));
                        }
                        else if (serverResponse.startsWith("OFFLINE_MSG")) {
                            System.out.println("\n" + serverResponse.substring(12));
                        }
                        else if (serverResponse.startsWith("ОШИБКА")) {
                            System.out.println(serverResponse);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Соединение с сервером прервано");
                }
            }).start();

            while (true) {
                String input = scanner.nextLine();
                out.println(input);

                if ("/exit".equalsIgnoreCase(input)) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка клиента: " + e.getMessage());
        }
    }
}