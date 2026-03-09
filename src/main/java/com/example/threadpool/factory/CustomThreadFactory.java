package com.example.threadpool.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Фабрика для создания потоков с уникальными именами и логированием.
 * Присваивает потокам имена с префиксом и порядковым номером.
 */
public class CustomThreadFactory implements ThreadFactory {
    private static final Logger logger = LoggerFactory.getLogger(CustomThreadFactory.class);
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    /**
     * Создает новую фабрику потоков.
     *
     * @param namePrefix префикс имени потока
     */
    public CustomThreadFactory(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    /**
     * Создает новый поток с задачей.
     *
     * @param r задача для выполнения
     * @return новый поток с уникальным именем
     */
    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());

        logger.info("[ThreadFactory] Creating new thread: {}", thread.getName());

        thread.setUncaughtExceptionHandler((t, e) ->
                logger.error("[Thread] {} terminated with error: {}", t.getName(), e.getMessage()));

        return thread;
    }
}