package com.example.rxjava;

import com.example.rxjava.core.Observable;
import com.example.rxjava.core.Observer;
import com.example.rxjava.disposable.Disposable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ObservableTest {
    private static final Logger logger = LoggerFactory.getLogger(ObservableTest.class);

    @Test
    void testObservableCreateAndSubscribe() {
        List<Integer> results = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Integer> observable = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onComplete();
        });

        observable.subscribe(new Observer<Integer>() {
            @Override
            public void onNext(Integer item) {
                results.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not throw error");
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertEquals(3, results.size());
        assertEquals(1, results.get(0));
        assertEquals(2, results.get(1));
        assertEquals(3, results.get(2));
        assertTrue(completed.get());
    }

    @Test
    void testErrorPropagation() {
        AtomicReference<Throwable> caughtError = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        List<Integer> receivedItems = new ArrayList<>();

        Observable<Integer> observable = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onError(new RuntimeException("Test error"));
            emitter.onNext(3); // Этот элемент не должен быть обработан
        });

        observable.subscribe(
                item -> receivedItems.add(item),
                error -> {
                    caughtError.set(error);
                    logger.info("Caught error: {}", error.getMessage());
                },
                () -> completed.set(true)
        );

        // Проверяем, что ошибка была получена
        assertNotNull(caughtError.get());
        assertEquals("Test error", caughtError.get().getMessage());

        // Проверяем, что onComplete не был вызван
        assertFalse(completed.get());

        // Проверяем, что элементы до ошибки были получены, а после - нет
        assertEquals(2, receivedItems.size());
        assertEquals(1, receivedItems.get(0));
        assertEquals(2, receivedItems.get(1));
    }

    @Test
    void testErrorInEmitter() {
        AtomicReference<Throwable> caughtError = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        List<Integer> receivedItems = new ArrayList<>();

        Observable<Integer> observable = Observable.create(emitter -> {
            emitter.onNext(1);
            // Эмитируем исключение в процессе эмиссии
            throw new RuntimeException("Crash in emitter");
        });

        observable.subscribe(
                item -> receivedItems.add(item),
                error -> {
                    caughtError.set(error);
                    logger.info("Caught error: {}", error.getMessage());
                },
                () -> completed.set(true)
        );

        // Проверяем, что ошибка была перехвачена
        assertNotNull(caughtError.get());
        assertEquals("Crash in emitter", caughtError.get().getMessage());
        assertEquals(1, receivedItems.size()); // onNext(1) успел выполниться
        assertFalse(completed.get());
    }

    @Test
    void testSubscribeWithLambdaError() {
        AtomicReference<Throwable> caughtError = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        List<Integer> receivedItems = new ArrayList<>();

        Observable<Integer> observable = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onError(new RuntimeException("Error in stream"));
        });

        observable.subscribe(
                item -> receivedItems.add(item),
                error -> {
                    caughtError.set(error);
                    logger.info("Lambda caught error: {}", error.getMessage());
                },
                () -> completed.set(true)
        );

        assertNotNull(caughtError.get());
        assertEquals("Error in stream", caughtError.get().getMessage());
        assertEquals(1, receivedItems.size());
        assertFalse(completed.get());
    }

    @Test
    void testErrorInOnNextHandler() {
        AtomicReference<Throwable> caughtError = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        List<Integer> receivedItems = new ArrayList<>();

        Observable<Integer> observable = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onComplete();
        });

        // Создаем первый подписчик, который падает на элементе 2
        observable.subscribe(
                item -> {
                    receivedItems.add(item);
                    if (item == 2) {
                        throw new RuntimeException("Error processing item 2");
                    }
                },
                error -> {
                    caughtError.set(error);
                    logger.info("This should NOT be called - error in subscriber doesn't affect stream");
                },
                () -> {
                    logger.info("First subscriber completed");
                }
        );

        // Проверяем, что ошибка НЕ была передана в onError потока
        assertNull(caughtError.get(), "Error in subscriber should not propagate to stream");

        // Проверяем, что все элементы были доставлены (исключение в подписчике не останавливает поток)
        assertEquals(3, receivedItems.size(), "All items should be received despite exception in subscriber");

        // Теперь создаем второго подписчика, который должен получить все элементы
        List<Integer> secondResults = new ArrayList<>();
        AtomicBoolean secondCompleted = new AtomicBoolean(false);

        observable.subscribe(
                item -> secondResults.add(item),
                error -> fail("Second subscriber should not get error"),
                () -> secondCompleted.set(true)
        );

        assertEquals(3, secondResults.size(), "Second subscriber should receive all items");
        assertTrue(secondCompleted.get(), "Second subscriber should complete normally");
    }

    @Test
    void testDisposableFromSubscribe() {
        AtomicInteger nextCount = new AtomicInteger(0);
        AtomicReference<Throwable> caughtError = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Integer> observable = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onComplete();
        });

        Disposable disposable = observable.subscribe(
                item -> {
                    nextCount.incrementAndGet();
                    logger.info("Received: {}", item);
                },
                error -> caughtError.set(error),
                () -> completed.set(true)
        );

        assertNotNull(disposable);
        assertFalse(disposable.isDisposed());
        assertEquals(3, nextCount.get());
        assertTrue(completed.get());

        disposable.dispose();
        assertTrue(disposable.isDisposed());
    }
}