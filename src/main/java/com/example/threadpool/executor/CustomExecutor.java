package com.example.threadpool.executor;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * Расширенный интерфейс Executor с дополнительными методами управления.
 * Предоставляет возможности для отправки задач с результатом и завершения пула.
 */
public interface CustomExecutor extends Executor {

    /**
     * Выполняет задачу без возврата результата.
     *
     * @param command задача для выполнения
     */
    @Override
    void execute(Runnable command);

    /**
     * Отправляет задачу на выполнение и возвращает Future для получения результата.
     *
     * @param callable задача, возвращающая результат
     * @return Future объект для получения результата
     * @param <T> тип результата
     */
    <T> Future<T> submit(Callable<T> callable);

    /**
     * Инициирует плавное завершение работы пула.
     * Новые задачи не принимаются, но уже запущенные завершаются.
     */
    void shutdown();

    /**
     * Инициирует принудительное завершение работы пула.
     * Прерывает все потоки и возвращает список невыполненных задач.
     *
     * @return список невыполненных задач
     */
    List<Runnable> shutdownNow();
}