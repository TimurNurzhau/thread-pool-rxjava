package com.example.threadpool.queue;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Реализация балансировщика Round Robin.
 * Распределяет задачи по кругу между очередями.
 */
public class RoundRobinBalancer implements LoadBalancer {
    private final List<BlockingQueue<?>> queues = new CopyOnWriteArrayList<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    @SuppressWarnings("unchecked")
    public <T> BlockingQueue<T> getNextQueue() {
        if (queues.isEmpty()) {
            throw new IllegalStateException("No queues available");
        }
        int index = Math.abs(counter.getAndIncrement() % queues.size());
        return (BlockingQueue<T>) queues.get(index);
    }

    @Override
    public void addQueue(BlockingQueue<?> queue) {
        queues.add(queue);
    }

    @Override
    public int getQueueCount() {
        return queues.size();
    }
}