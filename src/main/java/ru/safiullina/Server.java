package ru.safiullina;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT_DEFAULT = 9999; // порт по умолчанию
    private static final int AMOUNT_THREADS = 64; // количество потоков по умолчанию
    protected static final int LIMIT = 4096; // лимит на request line + заголовки
    private static final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html",
            "/events.js");
    protected static final String GET = "GET";
    protected static final String POST = "POST";
    final static List<String> allowedMethods = List.of(GET, POST); // Список разрешенных методов
    private static final ConcurrentHashMap<String, Map<String, Handler>> handlersMap = new ConcurrentHashMap<>();


    public static void start() {

        // Создаем пул потоков для клиентов
        final ExecutorService pool = Executors.newFixedThreadPool(AMOUNT_THREADS);
        // Создаем серверный сокет
        try (final var serverSocket = new ServerSocket(PORT_DEFAULT)) {
            System.out.println("Server is running.");
            // Сервер в бесконечном цикле ждет подключений и обрабатывает их в потоках
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
                final var in = new BufferedInputStream(socket.getInputStream());
                final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            // Помечаем в in позицию, до которой будем читать
            in.mark(LIMIT);
            // Создаем массив байт с заданным ограничением
            final var buffer = new byte[LIMIT];
            // Подсчитываем количество символов, которые по факту прочитали из потока
            final var read = in.read(buffer);
            System.out.printf("Прочитали %d байт \n", read);

            // Ищем request line
            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                response(out, 400, "Bad Request");
                return;
            }

            // Читаем request line (пример: GET /path HTTP/1.1)
            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                response(out, 400, "Bad Request");
                return;
            }

            var method = requestLine[0];
            if (!allowedMethods.contains(method)) {
                response(out, 400, "Bad Request");
                return;
            }
            System.out.println(method);

            var path = requestLine[1];
            if (!path.startsWith("/")) {
                response(out, 400, "Bad Request");
                return;
            }
            System.out.println(path);

            // Ищем заголовки
            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                response(out, 400, "Bad Request");
                return;
            }

            // отматываем на начало буфера
            in.reset();
            // пропускаем requestLine
            in.skip(headersStart);

            final var headersBytes = in.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
            System.out.println(headers);

            // для GET тела нет
            if (!method.equals(GET)) {
                in.skip(headersDelimiter.length);
                // вычитываем Content-Length, чтобы прочитать body
                final var contentLength = extractHeader(headers, "Content-Length");
                if (contentLength.isPresent()) {
                    final var length = Integer.parseInt(contentLength.get());
                    final var bodyBytes = in.readNBytes(length);

                    final var body = new String(bodyBytes);
                    System.out.println(body);
                }
            }


            // Создаем объект Request
            Request request = new Request(requestLine[0], requestLine[1]);
            if (request.getPath() == null || request.getMethod() == null) {
                response(out, 400, "Bad Request");
                return;
            }

            // Ищем handler по методу и пути
            method = request.getMethod();
            path = request.getPath();
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


    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    // from google guava with modifications
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
