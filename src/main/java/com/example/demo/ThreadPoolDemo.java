package com.example.demo;

import com.example.threadpool.CustomThreadPool;
import com.example.threadpool.ThreadPoolConfig;
import com.example.threadpool.policy.AbortPolicy;
import com.example.threadpool.policy.CallerRunsPolicy;
import com.example.threadpool.policy.DiscardPolicy;
import com.example.threadpool.queue.RoundRobinBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ThreadPoolDemo {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolDemo.class);

    public static void main(String[] args) throws InterruptedException {
        logger.info("=== Демонстрация Custom ThreadPool ===");

        // 1. Создаем конфигурацию пула
        ThreadPoolConfig config = new ThreadPoolConfig(
                2,           // corePoolSize - базовое количество потоков
                4,           // maxPoolSize - максимальное количество потоков
                5,           // keepAliveTime - время простоя
                TimeUnit.SECONDS,
                5,           // queueSize - размер очереди
                1            // minSpareThreads - минимальное число резервных потоков
        );

        logger.info("Создаем пул с конфигурацией: {}", config);

        // 2. Создаем пул с политикой по умолчанию (AbortPolicy) и RoundRobin балансировкой
        logger.info("\n=== Тест 1: Обычная работа с AbortPolicy ===");
        CustomThreadPool pool = new CustomThreadPool(config, new AbortPolicy(), new RoundRobinBalancer());

        // Отправляем задачи (немного, чтобы увидеть нормальную работу)
        logger.info("Отправляем 8 задач (должны обработаться)");
        for (int i = 1; i <= 8; i++) {
            int taskId = i;
            pool.execute(() -> {
                logger.info("Выполняется задача #{} в потоке {}",
                        taskId, Thread.currentThread().getName());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                logger.info("Задача #{} завершена", taskId);
            });
            Thread.sleep(100);
        }

        // Даем время на выполнение
        Thread.sleep(5000);

        // Тест на переполнение с AbortPolicy
        logger.info("\n=== Тест 2: Переполнение с AbortPolicy ===");
        for (int i = 9; i <= 18; i++) {
            int taskId = i;
            pool.execute(() -> {
                logger.info("Срочная задача #{} в потоке {}",
                        taskId, Thread.currentThread().getName());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread.sleep(10000);
        pool.shutdown();
        Thread.sleep(2000);

        // 3. Тест с CallerRunsPolicy
        logger.info("\n=== Тест 3: CallerRunsPolicy ===");
        CustomThreadPool callerRunsPool = new CustomThreadPool(
                new ThreadPoolConfig(1, 1, 1, TimeUnit.SECONDS, 2, 1),
                new CallerRunsPolicy(),
                new RoundRobinBalancer()
        );

        logger.info("Отправляем 5 задач в пул с 1 потоком и очередью 2");
        for (int i = 1; i <= 5; i++) {
            int taskId = i;
            logger.info("Отправка задачи #{} из потока {}", taskId, Thread.currentThread().getName());
            callerRunsPool.execute(() -> {
                logger.info("Задача #{} выполняется в {}", taskId, Thread.currentThread().getName());
                try { Thread.sleep(500); } catch (InterruptedException e) {}
                logger.info("Задача #{} завершена", taskId);
            });
        }

        Thread.sleep(3000);
        callerRunsPool.shutdown();
        Thread.sleep(1000);

        // 4. Тест с DiscardPolicy
        logger.info("\n=== Тест 4: DiscardPolicy ===");
        CustomThreadPool discardPool = new CustomThreadPool(
                new ThreadPoolConfig(1, 1, 1, TimeUnit.SECONDS, 2, 1),
                new DiscardPolicy(),
                new RoundRobinBalancer()
        );

        logger.info("Отправляем 5 задач (первые 3 выполнятся, остальные будут молча отброшены)");
        for (int i = 1; i <= 5; i++) {
            int taskId = i;
            discardPool.execute(() -> {
                logger.info("Discard задача #{} выполняется", taskId);
                try { Thread.sleep(500); } catch (InterruptedException e) {}
                logger.info("Discard задача #{} завершена", taskId);
            });
            Thread.sleep(100);
        }

        Thread.sleep(3000);
        discardPool.shutdown();
        Thread.sleep(1000);

        // 5. Тест балансировки Round Robin
        logger.info("\n=== Тест 5: Round Robin балансировка между очередями ===");
        CustomThreadPool balancedPool = new CustomThreadPool(
                new ThreadPoolConfig(3, 5, 2, TimeUnit.SECONDS, 3, 1),
                new AbortPolicy(),
                new RoundRobinBalancer()
        );

        logger.info("Отправляем 10 задач для распределения по очередям");
        for (int i = 1; i <= 10; i++) {
            int taskId = i;
            balancedPool.execute(() -> {
                logger.info("Задача #{} выполняется в {}", taskId, Thread.currentThread().getName());
                try { Thread.sleep(500); } catch (InterruptedException e) {}
            });
            Thread.sleep(100);
        }

        Thread.sleep(5000);
        balancedPool.shutdown();

        logger.info("\n=== Все тесты завершены ===");
    }
}