package ru.safiullina;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
            "/events.js","/formsfile.html");
    protected static final String GET = "GET";
    protected static final String POST = "POST";
    final static List<String> allowedMethods = List.of(GET, POST); // Список разрешенных методов
    private static final ConcurrentHashMap<String, Map<String, Handler>> handlersMap = new ConcurrentHashMap<>();


    /**
     * Основной метод.
     * Метод start создаёт серверный сокет и в цикле ожидает подключения клиентов.
     * Для обработки запросов клиента выделяет поток.
     */
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


    /**
     * Метод handlers выбирает подходящий обработчик по параметрам запроса клиента.
     *
     * @param socket - получает сокет
     */
    private static void handlers(Socket socket) {
        try (
                final var in = new BufferedInputStream(socket.getInputStream());
                final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            System.out.println("Сокет.порт: " + socket.getPort());
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

            // Получаем метод
            var method = requestLine[0];
            if (!allowedMethods.contains(method)) {
                response(out, 400, "Bad Request");
                return;
            }

            // Разделим путь и строку query params по знаку вопроса
            final var stringPathAndQueryParam = requestLine[1].split("\\?");

            // Получаем путь
            var path = stringPathAndQueryParam[0];
            if (!path.startsWith("/")) {
                response(out, 400, "Bad Request");
                return;
            }

            // Извлекаем Query Params
            // Используем утилитный класс URLEncodedUtils, который позволяет «парсить» Query String
            List<NameValuePair> params = URLEncodedUtils.parse(new URI(requestLine[1]), StandardCharsets.UTF_8);

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

            // Читаем и сохраняем заголовки в список строк
            final var headersBytes = in.readNBytes(headersEnd - headersStart);
            var headers = Arrays.asList(new String(headersBytes).split("\r\n"));

            // Читаем и парсим тело запроса, при этом для GET тела нет
            List<NameValuePair> bodyParams = new ArrayList<>();
            if (!method.equals(GET)) {
                in.skip(headersDelimiter.length);
                // Получим тип контента Content-Type
                final var contentType = extractHeader(headers, "Content-Type");
                if (contentType.isPresent()) {
                    System.out.println("Content-Type : " + contentType);
                    String[] contentTypeList = contentType.toString().split(";");

                    // Обработка типа multipart/form-data
                    if (contentTypeList[0].contains("multipart/form-data")) {
                        System.out.println("multipart/form-data");
                    }

                    // Обработка типа x-www-form-urlencoded
                    if (contentTypeList[0].contains("x-www-form-urlencoded")) {
                        // вычитываем Content-Length, чтобы прочитать body
                        final var contentLength = extractHeader(headers, "Content-Length");
                        if (contentLength.isPresent()) {
                            System.out.println("Content-length : " + contentLength);
                            final var length = Integer.parseInt(contentLength.get());
                            final var bodyBytes = in.readNBytes(length);
                            final var body = new String(bodyBytes);
                            bodyParams = URLEncodedUtils.parse(body, StandardCharsets.UTF_8);
                        }
                    }
                }

            }


            // Создаем объект Request
            Request request = new Request(method, path, params, headers, bodyParams);
            if (request.getPath() == null || request.getMethod() == null) {
                response(out, 400, "Bad Request");
                return;
            }
            // Выводим на экран объект Request
            printRequest(request);


            // Ищем handler по методу и пути
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

        } catch (IOException | URISyntaxException e) {
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


    /**
     * Метод addHandler добавляет обработчик запроса
     *
     * @param method  - метод запроса
     * @param path    - путь, endpoint
     * @param handler - обработчик
     */
    public static void addHandler(String method, String path, Handler handler) {
        if (!handlersMap.containsKey(method)) {
            handlersMap.put(method, new HashMap<>());
        }
        handlersMap.get(method).put(path, handler);
    }


    /**
     * Метод extractHeader ищет и возвращает значение нужного заголовка.
     *
     * @param headers - получает список строк, заголовки
     * @param header  - получает заголовок, который надо найти
     * @return - возвращает значение заголовка
     */
    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    // from Google guava with modifications
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

    private static void printRequest(Request request) {
        System.out.println("\n--- Request consists of:---");
        System.out.println("Метод = " + request.getMethod());
        System.out.println("Путь = " + request.getPath());

        System.out.println("\nПараметры запроса");
        for (NameValuePair param : request.getQueryParams()) {
            System.out.println(param.getName() + " : " + param.getValue());
        }

        System.out.println("\nЗаголовки");
        for (String param : request.getHeaders()) {
            System.out.println(param);
        }

        System.out.println("\nПараметры тела запроса");
        for (NameValuePair param : request.getPostParams()) {
            System.out.println(param.getName() + " : " + param.getValue());
        }
    }

}
