Курсовая работа по многопоточному и асинхронному программированию на Java
Общая информация
Данный проект содержит две независимые части курсовой работы:

Реализация собственного ThreadPool с балансировкой и политиками отказа

Реализация упрощенной версии RxJava

Структура проекта
Часть 1: Custom ThreadPool (папка threadpool)
Реализация пула потоков с настраиваемыми параметрами:

corePoolSize, maxPoolSize, keepAliveTime

queueSize, minSpareThreads

Три политики отказа (Abort, CallerRuns, Discard)

Round Robin балансировка между очередями

Детальное логирование всех событий

Подробнее в файле README_THREADPOOL.md

Часть 2: RxJava Lite (папка rxjava)
Упрощенная реализация RxJava:

Observable и Observer

Операторы map, filter, flatMap

Три типа планировщиков (IO, Computation, Single)

subscribeOn и observeOn

Управление подписками через Disposable

Подробнее в файле README_RXJAVA.md

Демонстрационные программы
Для ThreadPool:
com.example.demo.ThreadPoolDemo

Для RxJava:
com.example.demo.RxJavaDemo

Запуск проекта
Сборка проекта:
mvn clean compile

Запуск ThreadPool демо:
mvn exec:java -Dexec.mainClass="com.example.demo.ThreadPoolDemo"

Запуск RxJava демо:
mvn exec:java -Dexec.mainClass="com.example.demo.RxJavaDemo"

Технологии
Java 17

Maven

SLF4J для логирования

JUnit 5 для тестов