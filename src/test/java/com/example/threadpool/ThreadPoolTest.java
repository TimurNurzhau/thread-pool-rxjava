package com.example.threadpool;

import com.example.threadpool.policy.AbortPolicy;
import com.example.threadpool.policy.CallerRunsPolicy;
import com.example.threadpool.policy.DiscardPolicy;
import com.example.threadpool.queue.RoundRobinBalancer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreadPoolTest {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolTest.class);
    private ThreadPoolConfig config;

    @BeforeEach
    void setUp() {
        config = new ThreadPoolConfig(2, 4, 1, TimeUnit.SECONDS, 2, 1);
    }

    @Test
    void testPoolCreationAndExecution() throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(config, new AbortPolicy(), new RoundRobinBalancer());
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        for (int i = 0; i < 5; i++) {
            pool.execute(() -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(5, counter.get());
        pool.shutdown();
        assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    void testAbortPolicy() throws InterruptedException {
        ThreadPoolConfig smallConfig = new ThreadPoolConfig(1, 1, 1, TimeUnit.SECONDS, 1, 1);
        CustomThreadPool pool = new CustomThreadPool(smallConfig, new AbortPolicy(), new RoundRobinBalancer());

        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch secondTaskLatch = new CountDownLatch(1);
        AtomicInteger executedCount = new AtomicInteger(0);

        pool.execute(() -> {
            executedCount.incrementAndGet();
            logger.info("Первая задача (блокирующая) начала выполнение");
            startLatch.countDown();
            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            logger.info("Первая задача завершена");
        });

        assertTrue(startLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(200);

        pool.execute(() -> {
            executedCount.incrementAndGet();
            logger.info("Вторая задача (из очереди) начала выполнение");
            secondTaskLatch.countDown();
        });

        Thread.sleep(200);

        for (int i = 0; i < 2; i++) {
            final int taskNum = i + 3;
            pool.execute(() -> {
                logger.info("Задача {} (не должна выполняться)", taskNum);
                executedCount.incrementAndGet();
            });
        }

        Thread.sleep(500);

        assertEquals(2, pool.getRejectedTaskCount(), "Должны быть отклонены 2 задачи");

        blockLatch.countDown();
        assertTrue(secondTaskLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(500);

        assertEquals(2, executedCount.get(), "Должны выполниться 2 задачи");
        pool.shutdown();
    }

    @Test
    void testCallerRunsPolicy() throws InterruptedException {
        ThreadPoolConfig smallConfig = new ThreadPoolConfig(1, 1, 1, TimeUnit.SECONDS, 1, 1);
        CustomThreadPool pool = new CustomThreadPool(smallConfig, new CallerRunsPolicy(), new RoundRobinBalancer());

        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch taskStartedLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(4); // Ждем все 4 задачи
        String mainThreadName = Thread.currentThread().getName();
        AtomicInteger callerRunCount = new AtomicInteger(0);

        // Первая задача - блокирующая
        pool.execute(() -> {
            logger.info("Task 1 started in {}", Thread.currentThread().getName());
            startLatch.countDown();
            taskStartedLatch.countDown();
            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            completionLatch.countDown();
            logger.info("Task 1 completed");
        });

        assertTrue(startLatch.await(2, TimeUnit.SECONDS));
        assertTrue(taskStartedLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(200);

        // Вторая задача - пойдет в очередь
        pool.execute(() -> {
            logger.info("Task 2 executed in {}", Thread.currentThread().getName());
            completionLatch.countDown();
        });

        Thread.sleep(500);
        assertEquals(1, pool.getTotalQueueSize(), "Очередь должна содержать 1 задачу");

        // Третья и четвертая задачи - должны выполниться в caller thread
        for (int i = 3; i <= 4; i++) {
            final int taskId = i;
            pool.execute(() -> {
                logger.info("Task {} executed in {}", taskId, Thread.currentThread().getName());
                completionLatch.countDown();
                if (Thread.currentThread().getName().equals(mainThreadName)) {
                    callerRunCount.incrementAndGet();
                    logger.info("Caller run count incremented to {}", callerRunCount.get());
                }
            });
        }

        // Разблокируем первую задачу
        blockLatch.countDown();

        // Ждем завершения всех задач с таймаутом (без busy-waiting)
        assertTrue(completionLatch.await(5, TimeUnit.SECONDS),
                "Tasks did not complete within timeout");

        logger.info("Final callerRunCount: {}", callerRunCount.get());
        assertTrue(callerRunCount.get() > 0,
                "Хотя бы одна задача должна выполниться в caller thread");

        pool.shutdown();
    }

    @Test
    void testDiscardPolicy() throws InterruptedException {
        ThreadPoolConfig smallConfig = new ThreadPoolConfig(1, 1, 1, TimeUnit.SECONDS, 1, 1);
        CustomThreadPool pool = new CustomThreadPool(smallConfig, new DiscardPolicy(), new RoundRobinBalancer());

        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch secondTaskLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(2); // Ждем только 2 задачи (первую и вторую)
        AtomicInteger executedCount = new AtomicInteger(0);

        // Первая задача - блокирующая
        pool.execute(() -> {
            executedCount.incrementAndGet();
            startLatch.countDown();
            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            completionLatch.countDown();
        });

        assertTrue(startLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(200);

        // Вторая задача - пойдет в очередь
        pool.execute(() -> {
            executedCount.incrementAndGet();
            logger.info("Вторая задача выполнена");
            secondTaskLatch.countDown();
            completionLatch.countDown();
        });

        Thread.sleep(200);

        // Третья и четвертая задачи - должны быть отброшены (DiscardPolicy)
        for (int i = 0; i < 2; i++) {
            pool.execute(() -> {
                executedCount.incrementAndGet();
                logger.info("Задача которая НЕ должна выполниться");
            });
        }

        Thread.sleep(500);

        // Разблокируем первую задачу
        blockLatch.countDown();

        // Ждем выполнения второй задачи
        assertTrue(secondTaskLatch.await(2, TimeUnit.SECONDS));

        // Ждем завершения первых двух задач
        assertTrue(completionLatch.await(3, TimeUnit.SECONDS));

        Thread.sleep(500);

        assertEquals(2, executedCount.get(), "Должны выполниться только 2 задачи");
        pool.shutdown();
    }

    @Test
    void testSubmit() throws Exception {
        CustomThreadPool pool = new CustomThreadPool(config, new AbortPolicy(), new RoundRobinBalancer());
        var future = pool.submit(() -> {
            Thread.sleep(100);
            return 42;
        });
        assertEquals(42, future.get(2, TimeUnit.SECONDS));
        pool.shutdown();
    }

    @Test
    void testShutdownNow() throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(config, new AbortPolicy(), new RoundRobinBalancer());
        CountDownLatch blockLatch = new CountDownLatch(1);

        pool.execute(() -> {
            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(200);

        for (int i = 0; i < 5; i++) {
            pool.execute(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread.sleep(200);
        List<Runnable> unfinished = pool.shutdownNow();
        assertNotNull(unfinished);
        logger.info("Unfinished tasks: {}", unfinished.size());

        blockLatch.countDown();
        Thread.sleep(500);
    }

    @Test
    void testRoundRobinBalancing() throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(config, new AbortPolicy(), new RoundRobinBalancer());
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            pool.execute(latch::countDown);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        pool.shutdown();
    }

    @Test
    void testMinSpareThreads() throws InterruptedException {
        ThreadPoolConfig spareConfig = new ThreadPoolConfig(1, 3, 2, TimeUnit.SECONDS, 5, 2);
        CustomThreadPool pool = new CustomThreadPool(spareConfig, new AbortPolicy(), new RoundRobinBalancer());

        // Даем время монитору создать spare потоки
        Thread.sleep(3000);

        int totalWorkers = pool.getTotalWorkers();
        logger.info("Total workers after monitor: {}", totalWorkers);
        assertTrue(totalWorkers >= 2, "Должно быть минимум 2 потока");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch workerActiveLatch = new CountDownLatch(1);

        // Запускаем задачу
        pool.execute(() -> {
            logger.info("Task started in {}", Thread.currentThread().getName());
            startLatch.countDown();
            workerActiveLatch.countDown();

            try {
                Thread.sleep(2000); // Держим поток активным
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Ждем старта задачи
        assertTrue(startLatch.await(2, TimeUnit.SECONDS), "Task did not start");

        // Ждем подтверждения активности
        assertTrue(workerActiveLatch.await(2, TimeUnit.SECONDS), "Worker did not become active");

        // Принудительно ждем, чтобы флаг успел установиться
        Thread.sleep(500);

        // Проверяем активность несколько раз
        int activeCount = 0;
        for (int i = 0; i < 5; i++) {
            activeCount = pool.getActiveCount();
            logger.info("Attempt {}: Active workers: {}", i+1, activeCount);
            if (activeCount > 0) {
                break;
            }
            Thread.sleep(200);
        }

        logger.info("Final active count: {}", activeCount);
        assertTrue(activeCount > 0, "Должен быть минимум 1 активный поток");

        blockLatch.countDown();
        pool.shutdown();
    }
}