package com.example.threadpool.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class Worker extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(Worker.class);
    // Количество последовательных таймаутов перед завершением потока.
    // Значение 3 выбрано как компромисс между быстрым освобождением ресурсов
    // и предотвращением premature termination при кратковременных паузах в нагрузке.
    private static final int MAX_IDLE_CHECKS = 3;

    private final BlockingQueue<Runnable> taskQueue;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final ReentrantLock activeLock = new ReentrantLock();
    private int idleCheckCount = 0;

    public Worker(BlockingQueue<Runnable> taskQueue, String namePrefix,
                  long keepAliveTime, TimeUnit timeUnit) {
        this.taskQueue = taskQueue;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        setName(namePrefix + "-worker-" + getId());
    }

    @Override
    public void run() {
        logger.info("[Worker] {} started for queue {}", getName(), taskQueue.hashCode());

        while (isRunning.get()) {
            try {
                Runnable task = taskQueue.poll(keepAliveTime, timeUnit);

                if (task == null) {
                    handleIdleState();
                } else {
                    executeTask(task);
                }
            } catch (InterruptedException e) {
                handleInterruption();
            }
        }

        logger.info("[Worker] {} terminated", getName());
    }

    private void executeTask(Runnable task) {
        idleCheckCount = 0;

        // Устанавливаем флаг активности ДО выполнения задачи
        setActive(true);

        // Небольшая задержка для обеспечения видимости флага
        Thread.yield();

        logger.info("[Worker] {} ACTIVE - executes task from queue {}", getName(), taskQueue.hashCode());

        try {
            task.run();
            logger.info("[Worker] {} completed task", getName());
        } catch (Exception e) {
            logger.error("[Worker] {} task failed: {}", getName(), e.getMessage());
        } finally {
            // Сбрасываем флаг активности ПОСЛЕ выполнения задачи
            setActive(false);
            logger.info("[Worker] {} INACTIVE - task finished", getName());
        }
    }

 /*   private void setActive(boolean active) {
        activeLock.lock();
        try {
            isActive.set(active);
            // Добавляем барьер памяти
            logger.debug("[Worker] {} set active={}", getName(), active);
        } finally {
            activeLock.unlock();
        }
    }
*/

    private void setActive(boolean active) {
        isActive.set(active);
    }

    private void handleIdleState() {
        idleCheckCount++;
        if (idleCheckCount >= MAX_IDLE_CHECKS) {
            logger.info("[Worker] {} idle timeout after {} checks, stopping",
                    getName(), MAX_IDLE_CHECKS);
            isRunning.set(false);
        } else {
            logger.debug("[Worker] {} idle check {}/{}, continuing...",
                    getName(), idleCheckCount, MAX_IDLE_CHECKS);
        }
    }

    private void handleInterruption() {
        logger.info("[Worker] {} interrupted", getName());
        isRunning.set(false);
        Thread.currentThread().interrupt();
    }

    public void shutdown() {
        isRunning.set(false);
        this.interrupt();
    }

    public boolean isShutdown() {
        return !isRunning.get();
    }

    public boolean isActive() {
        activeLock.lock();
        try {
            return isActive.get();
        } finally {
            activeLock.unlock();
        }
    }
}