package com.example.threadpool;

import com.example.threadpool.policy.AbortPolicy;
import com.example.threadpool.policy.CallerRunsPolicy;
import com.example.threadpool.policy.DiscardPolicy;
import com.example.threadpool.queue.RoundRobinBalancer;
import com.example.threadpool.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ThreadPoolTest {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolTest.class);
    private ThreadPoolConfig config;

    @BeforeEach
    void setUp() {
        config = new ThreadPoolConfig(2, 4, 1, TimeUnit.SECONDS, 5, 1);
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
        CountDownLatch completionLatch = new CountDownLatch(4);
        String mainThreadName = Thread.currentThread().getName();
        AtomicInteger callerRunCount = new AtomicInteger(0);

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

        pool.execute(() -> {
            logger.info("Task 2 executed in {}", Thread.currentThread().getName());
            completionLatch.countDown();
        });

        Thread.sleep(500);
        assertEquals(1, pool.getTotalQueueSize(), "Очередь должна содержать 1 задачу");

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

        blockLatch.countDown();

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
        CountDownLatch completionLatch = new CountDownLatch(2);
        AtomicInteger executedCount = new AtomicInteger(0);

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

        pool.execute(() -> {
            executedCount.incrementAndGet();
            logger.info("Вторая задача выполнена");
            secondTaskLatch.countDown();
            completionLatch.countDown();
        });

        Thread.sleep(200);

        for (int i = 0; i < 2; i++) {
            pool.execute(() -> {
                executedCount.incrementAndGet();
                logger.info("Задача которая НЕ должна выполняться");
            });
        }

        Thread.sleep(500);

        blockLatch.countDown();

        assertTrue(secondTaskLatch.await(2, TimeUnit.SECONDS));
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
        // Конфиг с 3 core потоками (3 очереди)
        ThreadPoolConfig rrConfig = new ThreadPoolConfig(3, 3, 1, TimeUnit.SECONDS, 5, 1);
        CustomThreadPool pool = new CustomThreadPool(rrConfig, new AbortPolicy(), new RoundRobinBalancer());

        // Массив для подсчёта задач по очередям
        int[] queueCounts = new int[3];
        CountDownLatch latch = new CountDownLatch(9);

        for (int i = 0; i < 9; i++) {
            final int taskId = i;
            pool.execute(() -> {
                // Получаем текущий поток и его queueId
                Thread currentThread = Thread.currentThread();
                if (currentThread instanceof Worker) {
                    Worker worker = (Worker) currentThread;
                    int queueId = worker.getQueueId();
                    synchronized (queueCounts) {
                        queueCounts[queueId]++;
                    }
                    logger.info("Task {} executed by {} (queue {})",
                            taskId, currentThread.getName(), queueId);
                } else {
                    logger.warn("Current thread is not a Worker: {}", currentThread.getName());
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Tasks did not complete within timeout");

        // Проверяем распределение
        int totalTasks = 0;
        for (int i = 0; i < queueCounts.length; i++) {
            logger.info("Queue {} received {} tasks", i, queueCounts[i]);
            totalTasks += queueCounts[i];
            assertTrue(queueCounts[i] > 0, "Queue " + i + " was never used");
        }

        assertEquals(9, totalTasks, "Total tasks executed should be 9");

        // При 9 задачах на 3 очереди, ожидаем примерно по 3 задачи в каждой
        // Допускаем неравномерность из-за особенностей планировщика, но не более 2 отклонения
        for (int i = 0; i < queueCounts.length; i++) {
            assertTrue(queueCounts[i] >= 2 && queueCounts[i] <= 4,
                    "Queue " + i + " has " + queueCounts[i] + " tasks, expected ~3");
        }

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

        pool.execute(() -> {
            logger.info("Task started in {}", Thread.currentThread().getName());
            startLatch.countDown();
            workerActiveLatch.countDown();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(startLatch.await(2, TimeUnit.SECONDS), "Task did not start");
        assertTrue(workerActiveLatch.await(2, TimeUnit.SECONDS), "Worker did not become active");

        Thread.sleep(500);

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