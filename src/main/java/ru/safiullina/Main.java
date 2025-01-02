package ru.safiullina;

public class Main {
    public static void main(String[] args) {

        // добавление хендлеров (обработчиков)
        Server.addHandler("GET", "/messages", (request, responseStream) -> {
            Server.response(responseStream, 400, "Bad Request");
        });
        Server.addHandler("POST", "/messages", (request, responseStream) -> {
            Server.response(responseStream, 201, "Created");
        });
        Server.addHandler("POST", "/", (request, responseStream) -> {
            Server.response(responseStream, 201, "Created");
        });

        Server.start();

    }
}


