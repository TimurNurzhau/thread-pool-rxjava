package com.example.threadpool;

import com.example.threadpool.executor.CustomExecutor;
import com.example.threadpool.factory.CustomThreadFactory;
import com.example.threadpool.policy.RejectPolicy;
import com.example.threadpool.policy.AbortPolicy;
import com.example.threadpool.queue.LoadBalancer;
import com.example.threadpool.queue.RoundRobinBalancer;
import com.example.threadpool.worker.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Реализация пула потоков с настраиваемыми параметрами и балансировкой нагрузки.
 * Поддерживает политики отказа, мониторинг свободных потоков и Round Robin балансировку.
 */
public class CustomThreadPool implements CustomExecutor {
    private static final Logger logger = LoggerFactory.getLogger(CustomThreadPool.class);

    private final ThreadPoolConfig config;
    private RejectPolicy rejectPolicy;
    private final CustomThreadFactory threadFactory;
    private final List<Worker> workers;
    private final LoadBalancer loadBalancer;
    private final List<BlockingQueue<Runnable>> queues;
    private final ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger rejectedTaskCount = new AtomicInteger(0);
    private final ReentrantLock workersLock = new ReentrantLock();

    private volatile boolean isShutdown = false;
    private volatile boolean isShutdownNow = false;

    /**
     * Создает пул потоков с конфигурацией по умолчанию.
     * Использует AbortPolicy и RoundRobinBalancer.
     *
     * @param config конфигурация пула
     */
    public CustomThreadPool(ThreadPoolConfig config) {
        this(config, new AbortPolicy(), new RoundRobinBalancer());
    }

    /**
     * Создает пул потоков с указанными политикой отказа и балансировщиком.
     *
     * @param config конфигурация пула
     * @param rejectPolicy политика отказа при переполнении
     * @param loadBalancer балансировщик нагрузки
     */
    public CustomThreadPool(ThreadPoolConfig config, RejectPolicy rejectPolicy, LoadBalancer loadBalancer) {
        this.config = config;
        this.rejectPolicy = rejectPolicy;
        this.threadFactory = new CustomThreadFactory("CustomPool");
        this.workers = new CopyOnWriteArrayList<>();
        this.loadBalancer = loadBalancer;
        this.queues = new CopyOnWriteArrayList<>();

        // Создаем очереди (по одной на каждый core поток)
        for (int i = 0; i < config.getCorePoolSize(); i++) {
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(config.getQueueSize());
            queues.add(queue);
            loadBalancer.addQueue(queue);
        }

        // Создаем corePoolSize потоков при старте
        for (int i = 0; i < config.getCorePoolSize(); i++) {
            addWorker(queues.get(i), i);
        }

        logger.info("[Pool] CustomThreadPool initialized with config: {}, policy: {}, queues: {}",
                config, rejectPolicy.getClass().getSimpleName(), queues.size());

        // Запускаем монитор для поддержания minSpareThreads
        startSpareThreadsMonitor();
    }

    private void addWorker(BlockingQueue<Runnable> queue, int queueId) {
        workersLock.lock();
        try {
            // Проверяем, не завершен ли уже пул
            if (isShutdown || isShutdownNow) {
                logger.warn("[Pool] Cannot add worker after shutdown");
                return;
            }

            Worker worker = new Worker(
                    queue,
                    "CustomPool",
                    config.getKeepAliveTime(),
                    config.getTimeUnit(),
                    config.getMaxIdleChecks(),
                    queueId
            );
            workers.add(worker);
            threadFactory.newThread(worker).start();
            logger.info("[Pool] Added worker {} for queue {}", worker.getName(), queue.hashCode());
        } finally {
            workersLock.unlock();
        }
    }

    /**
     * Пытается создать новый рабочий поток, если текущее количество потоков меньше максимального.
     * Возвращает очередь, в которую следует поместить задачу (новую или текущую).
     *
     * @param currentQueue текущая выбранная очередь
     * @return очередь для размещения задачи
     */
    private BlockingQueue<Runnable> tryCreateNewWorker(BlockingQueue<Runnable> currentQueue) {
        BlockingQueue<Runnable> newQueue = null;
        int newQueueId = -1;

        // Часть 1: проверяем и создаем очередь под блокировкой
        workersLock.lock();
        try {
            if (workers.size() < config.getMaxPoolSize()) {
                newQueue = new LinkedBlockingQueue<>(config.getQueueSize());
                queues.add(newQueue);
                loadBalancer.addQueue(newQueue);
                newQueueId = queues.size() - 1;
                logger.debug("[Pool] Created new queue, total queues: {}", queues.size());
            }
        } finally {
            workersLock.unlock();
        }

        // Часть 2: если создали очередь, запускаем воркер без блокировки
        if (newQueue != null) {
            addWorker(newQueue, newQueueId);
            logger.info("[Pool] Created new worker and queue due to load");
            return newQueue;
        }

        return currentQueue;
    }

    private void startSpareThreadsMonitor() {
        monitorExecutor.scheduleAtFixedRate(() -> {
            if (isShutdown || isShutdownNow) return;

            int activeCount = getActiveCount();
            int totalWorkers = workers.size();
            int freeWorkers = totalWorkers - activeCount;

            logger.debug("[Monitor] Active: {}, Total: {}, Free: {}, Min spare: {}",
                    activeCount, totalWorkers, freeWorkers, config.getMinSpareThreads());

            // Если свободных потоков меньше minSpareThreads и можно создать новые
            while (freeWorkers < config.getMinSpareThreads() && totalWorkers < config.getMaxPoolSize()) {
                BlockingQueue<Runnable> newQueue = new LinkedBlockingQueue<>(config.getQueueSize());
                queues.add(newQueue);
                loadBalancer.addQueue(newQueue);
                int newQueueId = queues.size() - 1;
                addWorker(newQueue, newQueueId);
                totalWorkers++;
                freeWorkers++;
                logger.info("[Monitor] Created spare thread. Free workers now: {}", freeWorkers);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("Task cannot be null");
        }

        if (isShutdown || isShutdownNow) {
            logger.warn("[Pool] Rejected task - pool is shutting down");
            rejectedTaskCount.incrementAndGet();
            rejectPolicy.reject(command, this);
            return;
        }

        BlockingQueue<Runnable> targetQueue = loadBalancer.getNextQueue();

        // Пытаемся создать новый воркер, если нужно
        targetQueue = tryCreateNewWorker(targetQueue);

        boolean offered = targetQueue.offer(command);
        if (offered) {
            logger.info("[Pool] Task accepted into queue {}. Queue size: {}",
                    targetQueue.hashCode(), targetQueue.size());
        } else {
            rejectedTaskCount.incrementAndGet();
            logger.warn("[Pool] Queue {} is full! Current size: {}, max: {}",
                    targetQueue.hashCode(), targetQueue.size(), config.getQueueSize());
            rejectPolicy.reject(command, this);
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (callable == null) {
            throw new NullPointerException("Callable cannot be null");
        }

        if (isShutdown || isShutdownNow) {
            logger.warn("[Pool] Rejected submission - pool is shutting down");
            rejectedTaskCount.incrementAndGet();
            CompletableFuture<T> rejectedFuture = new CompletableFuture<>();
            rejectedFuture.completeExceptionally(new RejectedExecutionException("Pool is shutting down"));
            return rejectedFuture;
        }

        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public void shutdown() {
        logger.info("[Pool] Shutdown initiated");
        isShutdown = true;

        monitorExecutor.shutdown();
        try {
            if (!monitorExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            monitorExecutor.shutdownNow();
        }

        workersLock.lock();
        try {
            for (Worker worker : workers) {
                worker.shutdown();
            }
        } finally {
            workersLock.unlock();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        logger.info("[Pool] ShutdownNow initiated");
        isShutdownNow = true;

        monitorExecutor.shutdown();
        try {
            if (!monitorExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            monitorExecutor.shutdownNow();
        }

        workersLock.lock();
        try {
            for (Worker worker : workers) {
                worker.shutdown();
                worker.interrupt();
            }
        } finally {
            workersLock.unlock();
        }

        List<Runnable> unfinishedTasks = new ArrayList<>();
        for (BlockingQueue<Runnable> queue : queues) {
            queue.drainTo(unfinishedTasks);
        }

        logger.info("[Pool] ShutdownNow completed. Returning {} unfinished tasks", unfinishedTasks.size());
        return unfinishedTasks;
    }

    /**
     * Возвращает количество активных потоков (выполняющих задачи).
     */
    public int getActiveCount() {
        int count = 0;
        for (Worker worker : workers) {
            if (worker.isActive()) {
                count++;
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Total active count: {}", count);
        }
        return count;
    }

    /**
     * Возвращает общее количество живых потоков в пуле.
     */
    public int getTotalWorkers() {
        workersLock.lock();
        try {
            return workers.size();
        } finally {
            workersLock.unlock();
        }
    }

    /**
     * Возвращает общий размер всех очередей.
     */
    public int getTotalQueueSize() {
        int total = 0;
        for (BlockingQueue<Runnable> queue : queues) {
            total += queue.size();
        }
        return total;
    }

    /**
     * Возвращает количество отклоненных задач.
     */
    public int getRejectedTaskCount() {
        return rejectedTaskCount.get();
    }

    /**
     * Ожидает завершения всех потоков.
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        workersLock.lock();
        try {
            for (Worker worker : workers) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }
                worker.join(remaining);
            }
            return workers.stream().noneMatch(Thread::isAlive);
        } finally {
            workersLock.unlock();
        }
    }

    /**
     * Проверяет, завершены ли все потоки.
     */
    public boolean isTerminated() {
        workersLock.lock();
        try {
            return workers.stream().noneMatch(Thread::isAlive);
        } finally {
            workersLock.unlock();
        }
    }

    /**
     * Возвращает количество живых потоков.
     */
    public int getLiveThreadCount() {
        workersLock.lock();
        try {
            return (int) workers.stream()
                    .filter(Thread::isAlive)
                    .count();
        } finally {
            workersLock.unlock();
        }
    }

    /**
     * Возвращает размеры всех очередей.
     */
    public List<Integer> getQueueSizes() {
        List<Integer> sizes = new ArrayList<>();
        workersLock.lock();
        try {
            for (BlockingQueue<Runnable> queue : queues) {
                sizes.add(queue.size());
            }
        } finally {
            workersLock.unlock();
        }
        return sizes;
    }

    /**
     * Устанавливает политику отказа.
     */
    public void setRejectPolicy(RejectPolicy newPolicy) {
        this.rejectPolicy = newPolicy;
        logger.info("[Pool] Reject policy changed to: {}", newPolicy.getClass().getSimpleName());
    }

    /**
     * Возвращает текущую политику отказа.
     */
    public RejectPolicy getRejectPolicy() {
        return rejectPolicy;
    }

    /**
     * Сбрасывает счетчик отклоненных задач.
     */
    public void resetRejectedTaskCount() {
        rejectedTaskCount.set(0);
    }

    /**
     * Возвращает ID очереди для указанного воркера.
     */
    public int getWorkerQueueId(Worker worker) {
        return worker.getQueueId();
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }
}