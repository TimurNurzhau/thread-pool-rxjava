THREAD POOL & RXJAVA IMPLEMENTATION

ОПИСАНИЕ ПРОЕКТА

Курсовая работа по многопоточному и асинхронному программированию на Java.
Проект состоит из двух независимых частей.

Часть 1. Custom ThreadPool
Реализация собственного пула потоков с настраиваемыми параметрами:
- corePoolSize, maxPoolSize, keepAliveTime
- queueSize, minSpareThreads
- Три политики отказа (Abort, CallerRuns, Discard)
- Round Robin балансировка между очередями
- Детальное логирование всех событий

Часть 2. RxJava Lite
Упрощенная версия RxJava с базовыми операторами:
- Observable и Observer
- Операторы map, filter, flatMap
- Три типа планировщиков (IO, Computation, Single)
- subscribeOn и observeOn
- Управление подписками через Disposable

БЫСТРЫЙ СТАРТ

Сборка проекта:
mvn clean package

Запуск ThreadPool демо:
mvn exec:java -Dexec.mainClass="com.example.demo.ThreadPoolDemo"

Запуск RxJava демо:
mvn exec:java -Dexec.mainClass="com.example.demo.RxJavaDemo"

СТРУКТУРА ПРОЕКТА

src/main/java/com/example/
demo/                 - Демонстрационные классы
threadpool/           - Реализация ThreadPool
rxjava/               - Реализация RxJava

src/test/java/com/example/
rxjava/               - Тесты RxJava
threadpool/           - Тесты ThreadPool

ТЕСТИРОВАНИЕ

Всего тестов: 32
RxJava тесты: 24
ThreadPool тесты: 8
Статус: все тесты успешно проходят

ТЕХНОЛОГИИ

Java 17
Maven
SLF4J для логирования
JUnit 5 для тестирования

АВТОР

Timur Nurzhau