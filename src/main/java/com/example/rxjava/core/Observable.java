package com.example.rxjava.core;

import com.example.rxjava.disposable.CompositeDisposable;
import com.example.rxjava.disposable.Disposable;
import com.example.rxjava.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class Observable<T> {
    private static final Logger logger = LoggerFactory.getLogger(Observable.class);

    // Пул для переиспользования списков в observeOn
    private static final int MAX_LIST_POOL_SIZE = 16;
    private static final Queue<List> LIST_POOL = new ConcurrentLinkedQueue<>();

    private final Emitter<T> emitter;

    private Observable(Emitter<T> emitter) {
        this.emitter = emitter;
    }

    public static <T> Observable<T> create(Emitter<T> emitter) {
        if (emitter == null) {
            throw new NullPointerException("emitter must not be null");
        }
        return new Observable<>(new Emitter<T>() {
            @Override
            public void emit(Observer<T> observer) {
                try {
                    SafeObserver<T> safeObserver = new SafeObserver<>(observer);
                    emitter.emit(safeObserver);
                } catch (Exception e) {
                    observer.onError(e);
                }
            }
        });
    }

    public Disposable subscribe(Observer<T> observer) {
        if (observer == null) {
            throw new NullPointerException("observer must not be null");
        }

        SafeObserver<T> safeObserver;
        if (observer instanceof SafeObserver) {
            safeObserver = (SafeObserver<T>) observer;
        } else {
            safeObserver = new SafeObserver<>(observer);
        }

        try {
            emitter.emit(safeObserver);
        } catch (Exception e) {
            if (!safeObserver.isDisposed()) {
                safeObserver.onError(e);
            }
        }

        return new Disposable() {
            @Override
            public void dispose() {
                safeObserver.dispose();
            }

            @Override
            public boolean isDisposed() {
                return safeObserver.isDisposed();
            }
        };
    }

    public Disposable subscribe(Consumer<T> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        if (onNext == null || onError == null || onComplete == null) {
            throw new NullPointerException("Subscriber callbacks must not be null");
        }

        return subscribe(new Observer<T>() {
            @Override
            public void onNext(T item) {
                try {
                    onNext.accept(item);
                } catch (Exception e) {
                    // Ошибка в подписчике не должна влиять на поток
                    logger.error("Subscriber threw exception in onNext lambda: {}", e.getMessage(), e);
                    // НЕ вызываем onError!
                }
            }

            @Override
            public void onError(Throwable t) {
                try {
                    onError.accept(t);
                } catch (Exception e) {
                    logger.error("Exception in subscriber onError handler: {}", e.getMessage(), e);
                }
            }

            @Override
            public void onComplete() {
                try {
                    onComplete.run();
                } catch (Exception e) {
                    logger.error("Exception in subscriber onComplete handler: {}", e.getMessage(), e);
                }
            }
        });
    }

    public <R> Observable<R> map(Function<T, R> function) {
        if (function == null) {
            throw new NullPointerException("function must not be null");
        }

        return Observable.create(emitter -> {
            Disposable d = subscribe(
                    item -> {
                        try {
                            emitter.onNext(function.apply(item));
                        } catch (Exception e) {
                            emitter.onError(e);
                        }
                    },
                    emitter::onError,
                    emitter::onComplete
            );
        });
    }

    public Observable<T> filter(Predicate<T> predicate) {
        if (predicate == null) {
            throw new NullPointerException("predicate must not be null");
        }

        return Observable.create(emitter -> {
            Disposable d = subscribe(
                    item -> {
                        try {
                            if (predicate.test(item)) {
                                emitter.onNext(item);
                            }
                        } catch (Exception e) {
                            emitter.onError(e);
                        }
                    },
                    emitter::onError,
                    emitter::onComplete
            );
        });
    }

    public <R> Observable<R> flatMap(Function<T, Observable<R>> function) {
        if (function == null) {
            throw new NullPointerException("function must not be null");
        }

        return Observable.create(emitter -> {
            CompositeDisposable disposables = new CompositeDisposable();
            AtomicBoolean mainCompleted = new AtomicBoolean(false);
            AtomicBoolean errorOccurred = new AtomicBoolean(false);
            AtomicInteger activeInnerCount = new AtomicInteger(0);
            Object lock = new Object();

            Disposable mainDisposable = subscribe(
                    item -> {
                        if (errorOccurred.get()) return;

                        try {
                            Observable<R> result = function.apply(item);

                            activeInnerCount.incrementAndGet();

                            Disposable inner = result.subscribe(
                                    value -> {
                                        // SafeObserver внутри уже потокобезопасен
                                        if (!errorOccurred.get()) {
                                            emitter.onNext(value);
                                        }
                                    },
                                    error -> {
                                        synchronized (lock) {
                                            if (errorOccurred.compareAndSet(false, true)) {
                                                emitter.onError(error);
                                                disposables.dispose();
                                            }
                                        }
                                    },
                                    () -> {
                                        int remaining = activeInnerCount.decrementAndGet();
                                        if (!errorOccurred.get() && mainCompleted.get() && remaining == 0) {
                                            emitter.onComplete();
                                        }
                                    }
                            );

                            disposables.add(inner);

                        } catch (Exception e) {
                            synchronized (lock) {
                                if (errorOccurred.compareAndSet(false, true)) {
                                    emitter.onError(e);
                                    disposables.dispose();
                                }
                            }
                        }
                    },
                    error -> {
                        synchronized (lock) {
                            if (errorOccurred.compareAndSet(false, true)) {
                                emitter.onError(error);
                                disposables.dispose();
                            }
                        }
                    },
                    () -> {
                        mainCompleted.set(true);
                        if (!errorOccurred.get() && activeInnerCount.get() == 0) {
                            emitter.onComplete();
                        }
                    }
            );

            disposables.add(mainDisposable);
        });
    }

    public Observable<T> subscribeOn(Scheduler scheduler) {
        if (scheduler == null) {
            throw new NullPointerException("scheduler must not be null");
        }

        return Observable.create(emitter -> {
            scheduler.execute(() -> {
                Disposable d = subscribe(
                        emitter::onNext,
                        emitter::onError,
                        emitter::onComplete
                );
            });
        });
    }

    public Observable<T> observeOn(Scheduler scheduler) {
        if (scheduler == null) {
            throw new NullPointerException("scheduler must not be null");
        }

        return Observable.create(emitter -> {
            List<T> buffer = new ArrayList<>();
            Object lock = new Object();
            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<Throwable> error = new AtomicReference<>(null);

            Disposable d = subscribe(
                    item -> {
                        boolean shouldSchedule = false;
                        synchronized (lock) {
                            if (!completed.get() && error.get() == null) {
                                buffer.add(item);
                                shouldSchedule = true;
                            }
                        }
                        if (shouldSchedule) {
                            scheduler.execute(() -> {
                                // Получаем список из пула или создаем новый
                                List<T> toEmit = LIST_POOL.poll();
                                if (toEmit == null) {
                                    toEmit = new ArrayList<>();
                                }

                                synchronized (lock) {
                                    if (!buffer.isEmpty()) {
                                        toEmit.addAll(buffer);
                                        buffer.clear();
                                    }
                                }

                                try {
                                    for (T value : toEmit) {
                                        // Проверяем ошибку перед каждой эмиссией
                                        if (error.get() != null) {
                                            break;
                                        }
                                        emitter.onNext(value);
                                    }

                                    synchronized (lock) {
                                        if (completed.get() && buffer.isEmpty() && error.get() == null) {
                                            emitter.onComplete();
                                        }
                                    }
                                } finally {
                                    // Возвращаем список в пул, если там не слишком много
                                    if (LIST_POOL.size() < MAX_LIST_POOL_SIZE) {
                                        toEmit.clear();
                                        LIST_POOL.offer(toEmit);
                                    }
                                }
                            });
                        }
                    },
                    err -> {
                        synchronized (lock) {
                            if (error.compareAndSet(null, err)) {
                                buffer.clear();
                                scheduler.execute(() -> emitter.onError(err));
                            }
                        }
                    },
                    () -> {
                        synchronized (lock) {
                            completed.set(true);
                            if (buffer.isEmpty()) {
                                scheduler.execute(emitter::onComplete);
                            }
                        }
                    }
            );
        });
    }

    @FunctionalInterface
    public interface Emitter<T> {
        void emit(Observer<T> observer);
    }

    private static class SafeObserver<T> implements Observer<T> {
        private final Observer<T> actual;
        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private final AtomicBoolean terminated = new AtomicBoolean(false);

        SafeObserver(Observer<T> actual) {
            this.actual = actual;
        }

        public boolean isDisposed() {
            return disposed.get();
        }

        public void dispose() {
            disposed.set(true);
        }

        @Override
        public void onNext(T item) {
            if (terminated.get() || disposed.get()) {
                return;
            }
            try {
                actual.onNext(item);
            } catch (Exception e) {
                logger.error("Subscriber threw exception in onNext: {}", e.getMessage(), e);
                // НЕ вызываем onError!
            }
        }

        @Override
        public void onError(Throwable t) {
            if (!terminated.compareAndSet(false, true) || disposed.get()) {
                return;
            }
            try {
                actual.onError(t);
            } catch (Exception e) {
                logger.error("Exception in subscriber onError handler: {}", e.getMessage(), e);
            }
        }

        @Override
        public void onComplete() {
            if (!terminated.compareAndSet(false, true) || disposed.get()) {
                return;
            }
            try {
                actual.onComplete();
            } catch (Exception e) {
                logger.error("Exception in subscriber onComplete handler: {}", e.getMessage(), e);
            }
        }
    }
}