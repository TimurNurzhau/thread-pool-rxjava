package com.example.rxjava;

import com.example.rxjava.core.Observable;
import com.example.rxjava.core.Observer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class FlatMapTest {
    private static final Logger logger = LoggerFactory.getLogger(FlatMapTest.class);

    @Test
    void testFlatMapSimple() throws InterruptedException {
        // Создаем Observable, который эмитит числа 1, 2, 3
        Observable<Integer> source = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onComplete();
        });

        List<String> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        // flatMap: каждое число преобразуем в Observable,
        // который эмитит "Number: X" и "Squared: X*X"
        Observable<String> flatMapped = source.flatMap(num ->
                Observable.create(innerEmitter -> {
                    innerEmitter.onNext("Number: " + num);
                    innerEmitter.onNext("Squared: " + (num * num));
                    innerEmitter.onComplete();
                })
        );

        flatMapped.subscribe(new Observer<String>() {
            @Override
            public void onNext(String item) {
                results.add(item);
                logger.info("Received: {}", item);
            }

            @Override
            public void onError(Throwable error) {
                fail("Unexpected error: " + error.getMessage());
            }

            @Override
            public void onComplete() {
                logger.info("Complete called, results size: {}", results.size());
                latch.countDown();
            }
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Timed out waiting for completion");

        // Должно быть 6 элементов (2 для каждого из 3 чисел)
        assertEquals(6, results.size(), "Should receive 6 elements");

        // Проверяем наличие всех ожидаемых элементов
        assertTrue(results.contains("Number: 1"), "Should contain 'Number: 1'");
        assertTrue(results.contains("Squared: 1"), "Should contain 'Squared: 1'");
        assertTrue(results.contains("Number: 2"), "Should contain 'Number: 2'");
        assertTrue(results.contains("Squared: 4"), "Should contain 'Squared: 4'");
        assertTrue(results.contains("Number: 3"), "Should contain 'Number: 3'");
        assertTrue(results.contains("Squared: 9"), "Should contain 'Squared: 9'");

        logger.info("All results: {}", results);
    }

    @Test
    void testFlatMapWithError() throws InterruptedException {
        Observable<Integer> source = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onError(new RuntimeException("Test error in source"));
        });

        List<String> results = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> caughtError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Observable<String> flatMapped = source.flatMap(num ->
                Observable.create(innerEmitter -> {
                    innerEmitter.onNext("Processed: " + num);
                    innerEmitter.onComplete();
                })
        );

        flatMapped.subscribe(new Observer<String>() {
            @Override
            public void onNext(String item) {
                results.add(item);
                logger.info("Received: {}", item);
            }

            @Override
            public void onError(Throwable error) {
                caughtError.set(error);
                logger.info("Caught error: {}", error.getMessage());
                latch.countDown();
            }

            @Override
            public void onComplete() {
                logger.info("Complete should not be called");
                // Не вызываем latch.countDown() здесь
            }
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Timed out waiting for error");

        // Проверяем, что ошибка была получена
        assertNotNull(caughtError.get(), "Error should be caught");
        assertEquals("Test error in source", caughtError.get().getMessage());

        // Может получить 0, 1 или 2 элемента в зависимости от времени возникновения ошибки
        logger.info("Received {} items before error", results.size());
        assertTrue(results.size() <= 2, "Should receive at most 2 items");
    }

    @Test
    void testFlatMapWithErrorInInner() throws InterruptedException {
        Observable<Integer> source = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onComplete();
        });

        List<String> results = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> caughtError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Observable<String> flatMapped = source.flatMap(num ->
                Observable.create(innerEmitter -> {
                    if (num == 2) {
                        innerEmitter.onError(new RuntimeException("Error processing number 2"));
                    } else {
                        innerEmitter.onNext("Processed: " + num);
                        innerEmitter.onComplete();
                    }
                })
        );

        flatMapped.subscribe(new Observer<String>() {
            @Override
            public void onNext(String item) {
                results.add(item);
                logger.info("Received: {}", item);
            }

            @Override
            public void onError(Throwable error) {
                caughtError.set(error);
                logger.info("Caught error: {}", error.getMessage());
                latch.countDown();
            }

            @Override
            public void onComplete() {
                logger.info("Complete called");
                // На случай, если onComplete вызовется (хотя не должен)
                latch.countDown();
            }
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Timed out");

        // Проверяем, что ошибка была получена
        assertNotNull(caughtError.get(), "Error should be caught");
        assertEquals("Error processing number 2", caughtError.get().getMessage());

        // Должен получить только элемент для числа 1
        logger.info("Received {} items: {}", results.size(), results);
        assertEquals(1, results.size(), "Should receive exactly 1 item");
        assertEquals("Processed: 1", results.get(0));
    }

    @Test
    void testFlatMapWithMultipleInnerAndCompletion() throws InterruptedException {
        Observable<Integer> source = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onComplete();
        });

        List<String> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        Observable<String> flatMapped = source.flatMap(num ->
                Observable.create(innerEmitter -> {
                    // Первый внутренний Observable эмитит 2 элемента
                    innerEmitter.onNext("Start of " + num);
                    innerEmitter.onNext("End of " + num);
                    innerEmitter.onComplete();
                })
        );

        flatMapped.subscribe(new Observer<String>() {
            @Override
            public void onNext(String item) {
                results.add(item);
                logger.info("Received: {}", item);
            }

            @Override
            public void onError(Throwable error) {
                fail("Unexpected error: " + error.getMessage());
            }

            @Override
            public void onComplete() {
                logger.info("Complete called, results size: {}", results.size());
                latch.countDown();
            }
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Timed out");

        // Должно быть 4 элемента (2 для каждого из 2 чисел)
        assertEquals(4, results.size(), "Should receive 4 elements");

        // Проверяем наличие всех элементов
        assertTrue(results.contains("Start of 1"));
        assertTrue(results.contains("End of 1"));
        assertTrue(results.contains("Start of 2"));
        assertTrue(results.contains("End of 2"));
    }
}