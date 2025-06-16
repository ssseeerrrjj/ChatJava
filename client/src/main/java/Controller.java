import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private boolean authorized;
    private String currentNickname;
    private volatile boolean isConnected = false;

    @FXML
    TextField messageField, loginField, nicknameField;

    @FXML
    TextArea chatArea;

    @FXML
    HBox authPanel, messagePanel;

    @FXML
    PasswordField passwordField;

    @FXML
    ListView<String> clientsOnlineList;

    @FXML
    Button changeNickButton;

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
        setVisibleForPanels(authorized);
        if (!authorized) {
            currentNickname = null;
            nicknameField.setVisible(false);
            changeNickButton.setVisible(false);
        } else {
            nicknameField.setVisible(true);
            changeNickButton.setVisible(true);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setAuthorized(false);
        ObservableList<String> clientsList = FXCollections.observableArrayList();
        clientsOnlineList.setItems(clientsList);
        clientsOnlineList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                messageField.clear();
                messageField.appendText("/w ");
                messageField.appendText(clientsOnlineList.getSelectionModel().getSelectedItem());
                messageField.appendText(" ");
                messageField.requestFocus();
                messageField.end();
            }
        });
    }

    public void connect() {
        try {
            socket = new Socket("localhost", 8888);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            Thread tRead = new Thread(() -> {
                try {
                    while (true) {
                        String authAnswer = in.readUTF();
                        if (authAnswer.startsWith("/authsuccess ")) {
                            chatArea.appendText("Сообщение от сервера: Ваш ник " + authAnswer.split("\\s")[1] + "\n");
                            currentNickname = authAnswer.split("\\s")[1];
                            setAuthorized(true);
                            break;
                        }
                        if (authAnswer.startsWith("/regsuccess")) {
                            chatArea.appendText("Регистрация успешна! Теперь вы можете авторизоваться.\n");
                        }
                        if (authAnswer.startsWith("/regerror")) {
                            chatArea.appendText("Ошибка регистрации: " + authAnswer.substring(10) + "\n");
                        }

                    }
                    while (true) {
                        String serverMessage = in.readUTF();
                        if (serverMessage.startsWith("/")) {
                            if (serverMessage.startsWith("/onlineClients ")) {
                                String[] tokens = serverMessage.split("\\s");
                                Platform.runLater(() -> {
                                    clientsOnlineList.getItems().clear();
                                    for (int i = 1; i < tokens.length; i++) {
                                        clientsOnlineList.getItems().add(tokens[i]);
                                    }
                                });
                            }
                            if (serverMessage.startsWith("/changeNicknameOk ")) {
                                currentNickname = serverMessage.split("\\s")[1];
                                Platform.runLater(() -> {
                                    chatArea.appendText("Ваш ник успешно изменен на: " + currentNickname + "\n");
                                });
                            }
                            if (serverMessage.startsWith("/changeNicknameError")) {
                                Platform.runLater(() -> {
                                    chatArea.appendText("Ошибка: " + serverMessage.substring(20) + "\n");
                                });
                            }
                            if (serverMessage.startsWith("/regerror")) {
                                Platform.runLater(() -> {
                                    chatArea.appendText("Ошибка регистрации: " + serverMessage.substring(10) + "\n");
                                });
                            }
                        } else {
                            chatArea.appendText(serverMessage + "\n");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    closeConnection();
                }
            });
            tRead.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {

        try {
            if (out != null && !socket.isClosed()) {
                out.writeUTF("/end");
            }
        } catch (IOException e) {
        }

        try {
            if (in != null) in.close();
        } catch (IOException e) {
        }

        try {
            if (out != null) out.close();
        } catch (IOException e) {
        }

        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
        }

        Platform.runLater(() -> {
            chatArea.clear();
            clientsOnlineList.getItems().clear();
            chatArea.appendText("Вы вышли из чата\n");
        });

        setAuthorized(false);
    }

    public void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            chatArea.appendText("Сообщение не может быть пустым!\n");
            return;
        }
        try {
            out.writeUTF(message);
            messageField.clear();
            messageField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendAuthMessage() {
        if (socket == null || socket.isClosed()) {
            connect();
        }
        try {
            out.writeUTF("/auth " + loginField.getText() + " " + passwordField.getText());
            loginField.clear();
            passwordField.clear();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void sendRegisterMessage() {
        if (socket == null || socket.isClosed()) {
            connect();
        }
        try {
            out.writeUTF("/register " + loginField.getText() + " " + passwordField.getText());
            loginField.clear();
            passwordField.clear();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void changeNickname() {
        String newNick = nicknameField.getText().trim();
        if (newNick.isEmpty()) {
            chatArea.appendText("Ник не может быть пустым!\n");
            return;
        }
        try {
            out.writeUTF("/changeNickname " + newNick);
            nicknameField.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void exit() {
        closeConnection();
    }

    private void setVisibleForPanels(boolean authorized) {
        authPanel.setVisible(!authorized);
        authPanel.setManaged(!authorized);
        messagePanel.setVisible(authorized);
        messagePanel.setManaged(authorized);
    }
}