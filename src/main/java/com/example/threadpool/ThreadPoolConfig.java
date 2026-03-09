package com.example.threadpool;

import java.util.concurrent.TimeUnit;

/**
 * Конфигурация пула потоков.
 * Содержит все настраиваемые параметры для CustomThreadPool.
 */
public class ThreadPoolConfig {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;

    /**
     * Создает новую конфигурацию пула потоков.
     *
     * @param corePoolSize минимальное (базовое) количество потоков
     * @param maxPoolSize максимальное количество потоков
     * @param keepAliveTime время, в течение которого поток может простаивать до завершения
     * @param timeUnit единицы времени для keepAliveTime
     * @param queueSize ограничение на количество задач в очереди
     * @param minSpareThreads минимальное число резервных потоков
     */
    public ThreadPoolConfig(int corePoolSize, int maxPoolSize,
                            long keepAliveTime, TimeUnit timeUnit,
                            int queueSize, int minSpareThreads) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
    }

    /**
     * @return минимальное количество потоков
     */
    public int getCorePoolSize() { return corePoolSize; }

    /**
     * @return максимальное количество потоков
     */
    public int getMaxPoolSize() { return maxPoolSize; }

    /**
     * @return время простоя потока
     */
    public long getKeepAliveTime() { return keepAliveTime; }

    /**
     * @return единицы времени для keepAliveTime
     */
    public TimeUnit getTimeUnit() { return timeUnit; }

    /**
     * @return размер очереди задач
     */
    public int getQueueSize() { return queueSize; }

    /**
     * @return минимальное число резервных потоков
     */
    public int getMinSpareThreads() { return minSpareThreads; }

    @Override
    public String toString() {
        return String.format(
                "ThreadPoolConfig{corePoolSize=%d, maxPoolSize=%d, keepAliveTime=%d %s, queueSize=%d, minSpareThreads=%d}",
                corePoolSize, maxPoolSize, keepAliveTime, timeUnit, queueSize, minSpareThreads
        );
    }
}