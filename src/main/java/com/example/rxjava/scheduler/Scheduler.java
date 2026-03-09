package com.example.rxjava.scheduler;

public interface Scheduler {
    void execute(Runnable task);
}