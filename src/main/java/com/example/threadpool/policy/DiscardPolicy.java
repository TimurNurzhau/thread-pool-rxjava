package com.example.threadpool.policy;

import com.example.threadpool.CustomThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Политика отказа - молча отбрасывает задачу.
 */
public class DiscardPolicy implements RejectPolicy {
    private static final Logger logger = LoggerFactory.getLogger(DiscardPolicy.class);

    @Override
    public void reject(Runnable task, CustomThreadPool pool) {
        logger.debug("[Rejected] Task {} silently discarded (DiscardPolicy)", task);
        // Ничего не делаем - задача просто теряется
    }
}