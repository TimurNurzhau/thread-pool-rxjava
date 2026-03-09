package com.example.threadpool.policy;

import com.example.threadpool.CustomThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Политика отказа - просто отклоняет задачу с логированием ошибки.
 */
public class AbortPolicy implements RejectPolicy {
    private static final Logger logger = LoggerFactory.getLogger(AbortPolicy.class);
    private final AtomicInteger rejectedCount = new AtomicInteger(0);

    @Override
    public void reject(Runnable task, CustomThreadPool pool) {
        rejectedCount.incrementAndGet();
        logger.error("[Rejected] Task {} was rejected due to overload! (AbortPolicy)", task);
    }

    /**
     * Возвращает количество отклоненных задач данной политикой.
     *
     * @return количество отклоненных задач
     */
    public int getRejectedCount() {
        return rejectedCount.get();
    }

    /**
     * Сбрасывает счетчик отклоненных задач.
     */
    public void resetRejectedCount() {
        rejectedCount.set(0);
    }
}