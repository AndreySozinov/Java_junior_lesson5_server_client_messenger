package ru.savrey;

import lombok.Getter;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

public class Server {

    // message broker (kafka, redis, rabbitmq, ...)
    // client sent letter to broker

    // server sent to SMTP-server

    public static final int PORT = 8181;

    private static long clientIdCounter = 1L;
    private static final Map<Long, SocketWrapper> clients = new HashMap<>();

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)){
            System.out.println("Сервер запущен на порту " + PORT);
            while (true) {
                final Socket client = server.accept();
                final long clientId = clientIdCounter++;

                SocketWrapper wrapper = new SocketWrapper(clientId, client);
                System.out.println("Подключился новый клиент[" + wrapper + "]");
                clients.values().stream().filter(it -> it.getId() != wrapper.getId())
                        .forEach(it -> it.getOutput().println("Новый Клиент [" + wrapper + "] подключился"));
                clients.put(clientId, wrapper);

                new Thread(() -> {
                    try (Scanner input = wrapper.getInput(); PrintWriter output = wrapper.getOutput()) {
                        output.println("Подключение успешно. Список клиентов: " + clients.values() + "\n" +
                                "Формат приват-сообщений: @id адресата текст сообщения.\nВыход - q");
                        boolean isAdmin = false;

                        while (true) {
                            String clientInput = input.nextLine();

                            if (Objects.equals("admin", clientInput)) {
                                isAdmin = true;
                                System.out.println("Клиент [" + wrapper + "] является админом");
                                continue;
                            }

                            if (clientInput.length() >= 5 && Objects.equals("kick", clientInput.substring(0, 4)) && isAdmin) {
                                Long kickedClient = Long.parseLong(clientInput.substring(clientInput.indexOf(" ") + 1));
                                clients.values().forEach(it -> it.getOutput().println("Клиент[" + kickedClient + "] отключен администратором"));
                                System.out.println("Клиент [" + clients.get(kickedClient) + "] отключен администратором");
                                clients.get(kickedClient).close();
                                clients.remove(kickedClient);
                                continue;
                            }

                            if (Objects.equals("q", clientInput)) {
                                clients.remove(clientId);
                                clients.values().forEach(it -> it.getOutput().println("Клиент[" + clientId + "] отключился"));
                                System.out.println("Клиент [" + wrapper + "] отключился");
                                break;
                            }

                            // формат приват-сообщения: "@число сообщение"
                            if (Objects.equals("@", clientInput.substring(0, 1))) {
                                long destinationId = Long.parseLong(clientInput.substring(1, clientInput.indexOf(" ")));
                                SocketWrapper destination = clients.get(destinationId);

                                destination.getOutput().println("From " + clientId + ": "
                                        + clientInput.substring(clientInput.indexOf(" ") + 1));
                            } else {
                                clients.values().stream().filter(it -> it.getId() != clientId)
                                        .forEach(it -> it.getOutput().println("From " + clientId + ": " + clientInput));
                            }

                        }
                    } catch (Exception e) {
                        System.out.println("Поток " + Thread.currentThread().getName() + " завершил работу");
                    }
                }).start();
            }
        }
    }
}

@Getter
class SocketWrapper implements AutoCloseable{

    private final long id;
    private final Socket socket;
    private final Scanner input;
    private final PrintWriter output;


    public SocketWrapper(long id, Socket socket) throws IOException {
        this.id = id;
        this.socket = socket;
        this.input = new Scanner(socket.getInputStream());
        this.output = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }

    @Override
    public String toString() {
        return "id:"
                + id + " ip:"
                + socket.getInetAddress() + " port:"
                + socket.getPort();
    }
}