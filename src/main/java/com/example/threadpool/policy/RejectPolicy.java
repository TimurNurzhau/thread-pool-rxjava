package com.example.threadpool.policy;

import com.example.threadpool.CustomThreadPool;

/**
 * Интерфейс политики отказа при переполнении пула.
 */
@FunctionalInterface
public interface RejectPolicy {
    /**
     * Обрабатывает отклоненную задачу.
     *
     * @param task отклоненная задача
     * @param pool пул потоков
     */
    void reject(Runnable task, CustomThreadPool pool);
}