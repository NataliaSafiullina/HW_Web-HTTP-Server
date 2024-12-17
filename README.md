# Домашнее задание к занятию «1.1. HTTP и современный Web»

## Refactoring & MultiThreading

### Легенда

Достаточно часто после того, как прототип проверен (речь о том, что было реализовано на лекции), возникает задача — привести это в должный вид: выделить классы, методы, обеспечить нужную функциональность.

### Задача

Необходимо отрефакторить код, рассмотренный на лекции, и применить все знания, которые у вас есть:

1. Выделить класс `Server` с методами для: 
    - запуска;
    - обработки конкретного подключения.
   
1. Реализовать обработку подключений с помощью `ThreadPool` — выделите фиксированный на 64 потока, и каждое подключение обрабатывайте в потоке из пула.


