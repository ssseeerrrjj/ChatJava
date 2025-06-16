import db.SQLHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ClientHandler {
    private int id;
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String nickname;

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            new Thread(() -> {
                try {
                    while (true) {
                        if (checkAuthorizationMessage(in.readUTF())) {
                            break;
                        }
                    }
                    while (true) {
                        String message = in.readUTF();

                        System.out.println("Сообщение от клиента: " + message);
                        if (message.startsWith("/")) {
                            if (message.equals("/end")) {
                                break;
                            }
                            handleCommandMessage(message);
                        } else {
                            SQLHandler.addToHistory(id, -1, message);
                            server.broadcastMsg(nickname + ": " + message);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    server.unsubscribe(this);
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleCommandMessage(String message) {
        if (message.startsWith("/w ")) {
            String[] tokens = message.split("\\s", 3);
            server.sendPrivateMessage(this, tokens[2], tokens[1]);
        }

        if (message.startsWith("/changeNickname ")) {
            String[] tokens = message.split("\\s");
            if (tokens.length == 2) {
                String newNickname = tokens[1];

                try {
                    // Проверяем, свободен ли новый никнейм
                    ResultSet rs = SQLHandler.getStatement().executeQuery(
                            String.format("SELECT id FROM users WHERE nickname = '%s'", newNickname));
                    if (rs.next()) {
                        sendMessage("/changeNicknameError Никнейм уже занят");
                        return;
                    }

                    if (SQLHandler.changeNickname(id, newNickname)) {
                        sendMessage("/changeNicknameOk " + newNickname);
                        nickname = newNickname;
                        server.broadcastOnlineClientsList();
                    } else {
                        sendMessage("/changeNicknameError Не удалось сменить никнейм");
                    }
                } catch (SQLException e) {
                    sendMessage("/changeNicknameError Ошибка базы данных");
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean checkAuthorizationMessage(String message) {
        if (message.startsWith("/auth ")) {
            String[] tokens = message.split("\\s");
            if (tokens.length == 3) {
                nickname = server.getAuthService().getNickname(tokens[1], tokens[2]);
                if (nickname != null) {
                    if (server.isNicknameFree(nickname)) {
                        id = server.getAuthService().getIdByNickname(nickname);
                        sendMessage("/authsuccess " + nickname);
                        server.subscribe(this);
                        return true;
                    } else {
                        sendMessage("Учетная запись в данный момент используется.");
                    }
                } else {
                    sendMessage("Неверный логин или пароль.");
                }
            }
        } else if (message.startsWith("/register ")) {
            String[] tokens = message.split("\\s");
            if (tokens.length == 3) {
                try {
                    // Проверяем занятость логина
                    ResultSet rsLogin = SQLHandler.getStatement().executeQuery(
                            String.format("SELECT id FROM users WHERE login = '%s'", tokens[1]));
                    if (rsLogin.next()) {
                        sendMessage("/regerror Логин уже занят");
                        return false;
                    }

                    ResultSet rsNick = SQLHandler.getStatement().executeQuery(
                            String.format("SELECT id FROM users WHERE nickname = '%s'", tokens[1]));
                    if (rsNick.next()) {
                        sendMessage("/regerror Никнейм уже занят");
                        return false;
                    }

                    SQLHandler.getStatement().executeUpdate(
                            String.format("INSERT INTO users (login, password, nickname) VALUES ('%s', '%s', '%s')",
                                    tokens[1], tokens[2], tokens[1]));
                    sendMessage("/regsuccess");
                } catch (SQLException e) {
                    sendMessage("/regerror " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public void sendMessage(String message) {
        try {
            out.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNickname() {
        return nickname;
    }

    public int getId() {
        return id;
    }
}

