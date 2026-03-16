package com.example.demo;

import com.example.rxjava.core.Observable;
import com.example.rxjava.scheduler.ComputationScheduler;
import com.example.rxjava.scheduler.IOScheduler;
import com.example.rxjava.scheduler.SingleThreadScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RxJavaDemo {
    private static final Logger logger = LoggerFactory.getLogger(RxJavaDemo.class);

    public static void main(String[] args) throws InterruptedException {
        logger.info("=== Демонстрация RxJava библиотеки ===");

        // 1. Простой Observable
        Observable<Integer> observable = Observable.create(emitter -> {
            logger.info("Начинаем эмиссию в потоке: {}", Thread.currentThread().getName());
            try {
                for (int i = 1; i <= 5; i++) {
                    emitter.onNext(i);
                    Thread.sleep(500);
                }
                emitter.onComplete();
            } catch (InterruptedException e) {
                emitter.onError(e);
            }
        });

        // 2. Подписка с операторами
        logger.info("\n=== Тест 1: Базовые операторы ===");
        observable
                .map(x -> x * 2)
                .filter(x -> x > 5)
                .subscribe(
                        item -> logger.info("Получено: {} в потоке {}", item, Thread.currentThread().getName()),
                        error -> logger.error("Ошибка: {}", error.getMessage()),
                        () -> logger.info("Завершено!")
                );

        Thread.sleep(3000);

        // 3. Тест с разными Scheduler
        logger.info("\n=== Тест 2: Работа с планировщиками ===");

        IOScheduler ioScheduler = new IOScheduler();
        ComputationScheduler computationScheduler = new ComputationScheduler();
        SingleThreadScheduler singleScheduler = new SingleThreadScheduler();

        Observable<Integer> numbers = Observable.create(emitter -> {
            logger.info("Эмиттер запущен в: {}", Thread.currentThread().getName());
            try {
                for (int i = 1; i <= 3; i++) {
                    emitter.onNext(i);
                }
                emitter.onComplete();
            } catch (Exception e) {
                emitter.onError(e);
            }
        });

        // 4. Тест subscribeOn и observeOn
        logger.info("\n=== Тест 3: subscribeOn и observeOn ===");

        IOScheduler io = new IOScheduler();
        ComputationScheduler computation = new ComputationScheduler();

        Observable<Integer> source = Observable.create(emitter -> {
            logger.info("Эмиттер выполняется в: {}", Thread.currentThread().getName());
            for (int i = 1; i <= 3; i++) {
                emitter.onNext(i);
            }
            emitter.onComplete();
        });

        source
                .subscribeOn(io)           // Эмиссия в IO потоке
                .observeOn(computation)     // Получение в Computation потоке
                .map(x -> x * 10)
                .subscribe(
                        item -> logger.info("Получено {} в потоке {}", item, Thread.currentThread().getName()),
                        error -> logger.error("Ошибка: {}", error.getMessage()),
                        () -> logger.info("Готово!")
                );

        Thread.sleep(1000);
        io.shutdown();
        computation.shutdown();

        // Подписка в IO потоке
        ioScheduler.execute(() -> {
            numbers.subscribe(
                    item -> logger.info("[IO] Получено: {} в {}", item, Thread.currentThread().getName()),
                    error -> logger.error("[IO] Ошибка: {}", error.getMessage()),
                    () -> logger.info("[IO] Завершено")
            );
        });

        // Подписка в Computation потоке
        computationScheduler.execute(() -> {
            numbers.subscribe(
                    item -> logger.info("[Computation] Получено: {} в {}", item, Thread.currentThread().getName()),
                    error -> logger.error("[Computation] Ошибка: {}", error.getMessage()),
                    () -> logger.info("[Computation] Завершено")
            );
        });

        // Подписка в Single потоке
        singleScheduler.execute(() -> {
            numbers.subscribe(
                    item -> logger.info("[Single] Получено: {} в {}", item, Thread.currentThread().getName()),
                    error -> logger.error("[Single] Ошибка: {}", error.getMessage()),
                    () -> logger.info("[Single] Завершено")
            );
        });

        Thread.sleep(2000);

        // Завершаем планировщики
        ioScheduler.shutdown();
        computationScheduler.shutdown();
        singleScheduler.shutdown();

        logger.info("\n=== Демонстрация завершена ===");
    }
}