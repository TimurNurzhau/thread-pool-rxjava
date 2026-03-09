package com.example.threadpool.queue;

import java.util.concurrent.BlockingQueue;

/**
 * Интерфейс балансировщика нагрузки.
 * Определяет методы для распределения задач между очередями.
 */
public interface LoadBalancer {
    /**
     * Возвращает следующую очередь для отправки задачи.
     *
     * @param <T> тип элементов в очереди
     * @return очередь для отправки задачи
     */
    <T> BlockingQueue<T> getNextQueue();

    /**
     * Добавляет новую очередь в балансировщик.
     *
     * @param queue новая очередь
     */
    void addQueue(BlockingQueue<?> queue);

    /**
     * Возвращает количество очередей.
     *
     * @return количество очередей
     */
    int getQueueCount();
}