package com.example.threadpool.policy;

import com.example.threadpool.CustomThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

/**
 * Кастомная политика: одна попытка повторно положить задачу в очередь через 100мс.
 * Если не получилось — задача тихо отбрасывается.
 */
public class RetryOncePolicy implements RejectPolicy {
    private static final Logger logger = LoggerFactory.getLogger(RetryOncePolicy.class);

    @Override
    public void reject(Runnable task, CustomThreadPool pool) {
        logger.warn("[RetryPolicy] Task {} rejected, retrying once...", task);

        try {
            Thread.sleep(100); // Даем время освободиться очереди
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Пробуем еще раз получить очередь через балансировщик
        BlockingQueue<Runnable> targetQueue = pool.getLoadBalancer().getNextQueue();
        boolean offered = targetQueue.offer(task);

        if (offered) {
            logger.info("[RetryPolicy] Task accepted on retry into queue {}", targetQueue.hashCode());
        } else {
            logger.error("[RetryPolicy] Task {} finally rejected", task);
            // Здесь можно добавить счетчик отброшенных
        }
    }
}