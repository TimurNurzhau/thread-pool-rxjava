Курсовая работа. Часть 2: RxJava Lite
Обзор
Реализация упрощенной версии RxJava с базовыми операторами и управлением потоками.

Архитектура
Основные компоненты:
Observable - источник данных

Observer - потребитель данных

Disposable - управление подпиской

Scheduler - управление потоками

Function, Predicate - функциональные интерфейсы

Базовые операторы:
map - преобразование элементов

filter - фильтрация элементов

flatMap - преобразование в новый Observable

subscribeOn - переключение потока для подписки

observeOn - переключение потока для получения данных

Планировщики (Schedulers)
1. IOScheduler
   Использует CachedThreadPool для I/O операций (сеть, файлы). Потоки создаются по необходимости и переиспользуются.

2. ComputationScheduler
   Использует FixedThreadPool с количеством потоков равным числу ядер процессора. Предназначен для вычислительных задач.

3. SingleThreadScheduler
   Использует SingleThreadExecutor для последовательного выполнения задач в одном потоке.

Тестирование
Тест 1: Базовые операторы
Создание Observable с эмиссией чисел от 1 до 5. Применение map(x -> x * 2) и filter(x -> x > 5). Результат: получены числа 6, 8, 10.

Тест 2: Работа с планировщиками
Демонстрация работы всех трех типов планировщиков с параллельной обработкой данных.

Тест 3: subscribeOn и observeOn
Эмиттер выполняется в IO потоке, а получение результатов в Computation потоке. Подтверждает корректную работу переключения контекста.

Примеры использования
Простой Observable:
Observable.create(emitter -> {
for (int i = 1; i <= 5; i++) {
emitter.onNext(i);
}
emitter.onComplete();
}).subscribe(
item -> System.out.println("Получено: " + item),
error -> System.err.println("Ошибка: " + error),
() -> System.out.println("Завершено")
);

С операторами:
Observable.create(emitter -> {
for (int i = 1; i <= 5; i++) {
emitter.onNext(i);
}
emitter.onComplete();
})
.map(x -> x * 2)
.filter(x -> x > 5)
.subscribe(
item -> System.out.println("Получено: " + item)
);

С планировщиками:
Observable.create(emitter -> {
for (int i = 1; i <= 3; i++) {
emitter.onNext(i);
}
emitter.onComplete();
})
.subscribeOn(new IOScheduler())
.observeOn(new ComputationScheduler())
.map(x -> x * 10)
.subscribe(
item -> System.out.println("Получено " + item + " в " + Thread.currentThread().getName())
);

Структура кода
Пакет rxjava содержит следующие классы и интерфейсы:

core/Observable.java - основной класс

core/Observer.java - интерфейс наблюдателя

core/Function.java - функциональный интерфейс для map и flatMap

core/Predicate.java - функциональный интерфейс для filter

disposable/Disposable.java - интерфейс для отмены подписки

disposable/EmptyDisposable.java - пустая реализация

scheduler/Scheduler.java - интерфейс планировщика

scheduler/IOScheduler.java - планировщик для I/O операций

scheduler/ComputationScheduler.java - планировщик для вычислений

scheduler/SingleThreadScheduler.java - однопоточный планировщик

Выводы
Реализованы все базовые компоненты RxJava

Работают операторы map, filter, flatMap

Корректно функционируют subscribeOn и observeOn

Три типа планировщиков покрывают основные сценарии использования

Демонстрационная программа подтверждает работоспособность

Как запустить
mvn clean compile exec:java -Dexec.mainClass="com.example.demo.RxJavaDemo"