package ru.safiullina;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT_DEFAULT = 9999; // порт по умолчанию
    private static final int AMOUNT_THREADS = 64; // количество потоков по умолчанию
    private static final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html",
            "/events.js");
    private static final ConcurrentHashMap<String, Map<String, Handler>> handlersMap = new ConcurrentHashMap<>();

    public static void start() {

        // Создаем пул потоков для клиентов
        final ExecutorService pool = Executors.newFixedThreadPool(AMOUNT_THREADS);

        try (final var serverSocket = new ServerSocket(PORT_DEFAULT)) {
            System.out.println("Server is running.");

            // Сервер в бесконечном цикле ждет подключений
            while (!serverSocket.isClosed()) {
                pool.execute(() -> {
                    try {
                        handlers(serverSocket.accept());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }

    }

    private static void handlers(Socket socket) {
        try (
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = in.readLine();
            if (requestLine == null) return;
            final var parts = requestLine.split(" ");
            System.out.println(parts[0] + " " + parts[1]);

            // Проверяем длину request line, если меньше трех, то просто закроем сокет
            if (parts.length != 3) {
                // just close socket
                return;
            }

            // Создаем объект Request
            Request request = new Request(parts[0], parts[1]);
            if (request.getPath() == null || request.getMethod() == null) {
                response(out, 400, "Bad Request");
                return;
            }

            // Ищем handler по методу и пути
            String method = request.getMethod();
            String path = request.getPath();
            if (handlersMap.containsKey(method)) {
                Map<String, Handler> handlerPairsOnMethod = handlersMap.get(method);
                if (handlerPairsOnMethod.containsKey(path)) {
                    Handler handler = handlerPairsOnMethod.get(path);
                    handler.handle(request, out);
                    return;
                }
            }

            // Если мы дошли до этой точки, значит нужного handler не нашли, запустим обработку по умолчанию

            // Проверим, входит ли endpoint в наш список доступных путей
            if (!validPaths.contains(path)) {
                response(out, 404, "Not Found");
                return;
            }
            // Составим путь до ресурса
            final var filePath = Path.of(".", "public", path);

            // Отдельная обработка /classic.html, отвечаем с заменой метки
            if (path.equals("/classic.html")) {
                response(out, 200, "OK", filePath, "time");
                return;
            }

            // На все остальные endpoint отвечаем одинаково
            response(out, 200, "OK", filePath);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static void response(BufferedOutputStream out, int responseCode, String responseStatus) throws
            IOException {
        out.write((
                "HTTP/1.1 " + responseCode + " " + responseStatus + "\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    protected static void response(BufferedOutputStream out, int responseCode,
                                   String responseStatus, Path filePath) throws IOException {
        final var length = Files.size(filePath);
        final var mimeType = Files.probeContentType(filePath);
        out.write((
                "HTTP/1.1 " + responseCode + " " + responseStatus + "\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    protected static void response(BufferedOutputStream out, int responseCode,
                                   String responseStatus, Path filePath, String label) throws IOException {
        final var template = Files.readString(filePath);
        final var content = template.replace(
                "{" + label + "}",
                LocalDateTime.now().toString()
        ).getBytes();
        final var mimeType = Files.probeContentType(filePath);

        out.write((
                "HTTP/1.1" + responseCode + " " + responseStatus + "\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + content.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.write(content);
        out.flush();
    }

    public static void addHandler(String method, String path, Handler handler) {
        if (!handlersMap.containsKey(method)) {
            handlersMap.put(method, new HashMap<>());
        }
        handlersMap.get(method).put(path, handler);
    }
}
