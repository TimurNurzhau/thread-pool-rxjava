package com.example.rxjava.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IOScheduler implements Scheduler {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void execute(Runnable task) {
        executor.submit(task);
    }

    public void shutdown() {
        executor.shutdown();
    }
}