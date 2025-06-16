package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLHandler {
    private static Connection connection;

    private static Statement statement;

    public static void disconnect() {
        try {
            connection.close();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
        try {
            statement.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public static void start() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:users.db");
        statement = connection.createStatement();

        statement.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "login TEXT UNIQUE, " +
                "password TEXT, " +
                "nickname TEXT UNIQUE)");

        statement.executeUpdate("CREATE TABLE IF NOT EXISTS message_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender_id INTEGER, " +
                "receiver_id INTEGER, " +
                "message TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");

        System.out.println("БД подключена!");
    }

    public static void addToHistory(int senderId, int receiverId, String message) {
        System.out.println("вызов add to history");
        try {
            String escapedMessage = message.replace("'", "''");

            String sql = String.format(
                    "INSERT INTO message_history (sender_id, receiver_id, message) VALUES (%d, %d, '%s');",
                    senderId, receiverId, escapedMessage
            );

            statement.executeUpdate(sql);
            System.out.println("закончил add to history");
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Ошибка при записи в историю: " + e.getMessage());
        }
    }

    public static boolean changeNickname(int id, String newNickname) {
        try {
            statement.executeUpdate(String.format("UPDATE users SET nickname = '%s' WHERE ID = %d;", newNickname, id));
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    public static Statement getStatement() {
        return statement;
    }
}
