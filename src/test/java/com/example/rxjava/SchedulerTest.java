package com.example.rxjava;

import com.example.rxjava.core.Observable;
import com.example.rxjava.core.Observer;
import com.example.rxjava.scheduler.ComputationScheduler;
import com.example.rxjava.scheduler.IOScheduler;
import com.example.rxjava.scheduler.SingleThreadScheduler;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerTest {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerTest.class);

    @Test
    void testIOScheduler() throws InterruptedException {
        IOScheduler scheduler = new IOScheduler();
        AtomicBoolean executed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        scheduler.execute(() -> {
            executed.set(true);
            logger.info("IO Scheduler executed in thread: {}", Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(executed.get());
        scheduler.shutdown();
    }

    @Test
    void testComputationScheduler() throws InterruptedException {
        ComputationScheduler scheduler = new ComputationScheduler();
        AtomicBoolean executed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        scheduler.execute(() -> {
            executed.set(true);
            logger.info("Computation Scheduler executed in thread: {}", Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(executed.get());
        scheduler.shutdown();
    }

    @Test
    void testSingleThreadScheduler() throws InterruptedException {
        SingleThreadScheduler scheduler = new SingleThreadScheduler();
        AtomicBoolean executed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        scheduler.execute(() -> {
            executed.set(true);
            logger.info("SingleThread Scheduler executed in thread: {}", Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(executed.get());
        scheduler.shutdown();
    }

    @Test
    void testSubscribeOn() throws InterruptedException {
        IOScheduler io = new IOScheduler();
        ComputationScheduler computation = new ComputationScheduler();

        List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        Observable<Integer> observable = Observable.create(emitter -> {
            threadNames.add("emitter:" + Thread.currentThread().getName());
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onComplete();
        });

        observable
                .subscribeOn(io)
                .observeOn(computation)
                .subscribe(
                        item -> threadNames.add("receive:" + Thread.currentThread().getName()),
                        error -> fail("Should not throw error"),
                        () -> latch.countDown()
                );

        assertTrue(latch.await(1, TimeUnit.SECONDS));

        // Проверяем, что эмиттер был в IO потоке
        assertTrue(threadNames.stream().anyMatch(name -> name.contains("emitter:pool-")));
        // Проверяем, что получение было в Computation потоке
        assertTrue(threadNames.stream().anyMatch(name -> name.contains("receive:pool-")));

        io.shutdown();
        computation.shutdown();
    }

    @Test
    void testObserveOnWithBuffer() throws InterruptedException {
        ComputationScheduler computation = new ComputationScheduler();

        // Используем синхронизированный список для потокобезопасности
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        Observable<Integer> observable = Observable.create(emitter -> {
            for (int i = 1; i <= 10; i++) {
                emitter.onNext(i);
                // Небольшая задержка, чтобы эмиссия не была мгновенной
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            emitter.onComplete();
        });

        observable
                .observeOn(computation)
                .subscribe(
                        results::add,
                        error -> fail("Should not throw error: " + error.getMessage()),
                        () -> {
                            logger.info("Complete called, results size: {}", results.size());
                            latch.countDown();
                        }
                );

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Timed out waiting for completion");

        // Проверяем, что все 10 элементов были получены
        assertEquals(10, results.size(), "Should receive all 10 elements");

        // Сортируем результаты для проверки порядка (если нужно)
        List<Integer> sortedResults = new ArrayList<>(results);
        Collections.sort(sortedResults);

        // Проверяем, что все числа от 1 до 10 присутствуют
        for (int i = 1; i <= 10; i++) {
            assertTrue(sortedResults.contains(i), "Should contain " + i);
        }

        logger.info("Received results: {}", results);

        computation.shutdown();
    }
}