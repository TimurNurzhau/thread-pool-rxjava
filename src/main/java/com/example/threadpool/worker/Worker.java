package com.example.threadpool.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Worker extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(Worker.class);

    private final BlockingQueue<Runnable> taskQueue;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int maxIdleChecks;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private int idleCheckCount = 0;

    public Worker(BlockingQueue<Runnable> taskQueue, String namePrefix,
                  long keepAliveTime, TimeUnit timeUnit,
                  int maxIdleChecks) {
        this.taskQueue = taskQueue;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.maxIdleChecks = maxIdleChecks;
        setName(namePrefix + "-worker-" + getId());
    }

    @Override
    public void run() {
        logger.info("[Worker] {} started for queue {}", getName(), taskQueue.hashCode());

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
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
        setActive(true);
        Thread.yield();

        logger.info("[Worker] {} ACTIVE - executes task from queue {}", getName(), taskQueue.hashCode());

        try {
            task.run();
            logger.info("[Worker] {} completed task", getName());
        } catch (Exception e) {
            // Проверяем, не был ли это InterruptedException
            if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
                logger.warn("[Worker] {} was interrupted during task execution", getName());
                Thread.currentThread().interrupt(); // Восстанавливаем флаг
                isRunning.set(false); // НЕМЕДЛЕННО завершаем поток
            } else {
                logger.error("[Worker] {} task failed: {}", getName(), e.getMessage());
            }
        } finally {
            setActive(false);
            logger.info("[Worker] {} INACTIVE - task finished", getName());
        }
    }

    private void setActive(boolean active) {
        isActive.set(active);
    }

    private void handleIdleState() {
        idleCheckCount++;
        if (idleCheckCount >= maxIdleChecks) {
            logger.info("[Worker] {} idle timeout after {} checks, stopping",
                    getName(), maxIdleChecks);
            isRunning.set(false);
        } else {
            logger.debug("[Worker] {} idle check {}/{}, continuing...",
                    getName(), idleCheckCount, maxIdleChecks);
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
        return isActive.get();
    }
}