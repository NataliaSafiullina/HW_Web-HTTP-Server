package ru.safiullina;

import java.io.BufferedOutputStream;
import java.io.IOException;

/**
 * Handler - Функциональный интерфейс.
 * Функциональный интерфейс в Java – это интерфейс, который содержит только 1 абстрактный метод.
 * Основное назначение – использование в лямбда-выражениях и method reference.
 * Аннотация @FunctionalInterface позволит использовать интерфейс в лямбда-выражениях,
 * не остерегаясь того, что кто-то добавит в интерфейс новый абстрактный метод и он перестанет быть функциональным.
 */
@FunctionalInterface
public interface Handler {

    void handle(Request request, BufferedOutputStream responseStream) throws IOException;
}
