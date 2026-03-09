package com.example.threadpool.policy;

import com.example.threadpool.CustomThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Политика отказа - выполняет задачу в вызывающем потоке.
 */
public class CallerRunsPolicy implements RejectPolicy {
    private static final Logger logger = LoggerFactory.getLogger(CallerRunsPolicy.class);

    @Override
    public void reject(Runnable task, CustomThreadPool pool) {
        logger.warn("[Rejected] Task {} will be executed in caller thread (CallerRunsPolicy)", task);
        task.run();
    }
}