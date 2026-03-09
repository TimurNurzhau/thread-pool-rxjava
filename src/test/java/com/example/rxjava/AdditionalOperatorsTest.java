package com.example.rxjava;

import com.example.rxjava.core.Observable;
import com.example.rxjava.operators.ConcatOperator;
import com.example.rxjava.operators.MergeOperator;
import com.example.rxjava.operators.ZipOperator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class AdditionalOperatorsTest {
    private static final Logger logger = LoggerFactory.getLogger(AdditionalOperatorsTest.class);

    @Test
    void testMergeOperator() throws InterruptedException {
        Observable<Integer> source1 = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onComplete();
        });

        Observable<Integer> source2 = Observable.create(emitter -> {
            emitter.onNext(3);
            emitter.onNext(4);
            emitter.onComplete();
        });

        List<Integer> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Integer> merged = MergeOperator.merge(source1, source2);
        merged.subscribe(
                results::add,
                error -> fail("Should not throw error"),
                () -> {
                    completed.set(true);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(completed.get());
        assertEquals(4, results.size());
        assertTrue(results.contains(1));
        assertTrue(results.contains(2));
        assertTrue(results.contains(3));
        assertTrue(results.contains(4));
    }

    @Test
    void testZipOperator() throws InterruptedException {
        Observable<Integer> numbers = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onComplete();
        });

        Observable<String> letters = Observable.create(emitter -> {
            emitter.onNext("A");
            emitter.onNext("B");
            emitter.onNext("C");
            emitter.onComplete();
        });

        List<String> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Observable<String> zipped = ZipOperator.zip(
                numbers,
                letters,
                (num, letter) -> num + letter
        );

        zipped.subscribe(
                results::add,
                error -> fail("Should not throw error"),
                latch::countDown
        );

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(3, results.size());
        assertEquals("1A", results.get(0));
        assertEquals("2B", results.get(1));
        assertEquals("3C", results.get(2));
    }

    @Test
    void testConcatOperator() throws InterruptedException {
        Observable<Integer> source1 = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onComplete();
        });

        Observable<Integer> source2 = Observable.create(emitter -> {
            emitter.onNext(3);
            emitter.onNext(4);
            emitter.onComplete();
        });

        List<Integer> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Observable<Integer> concatenated = ConcatOperator.concat(source1, source2);
        concatenated.subscribe(
                results::add,
                error -> fail("Should not throw error"),
                latch::countDown
        );

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(4, results.size());
        assertEquals(1, results.get(0));
        assertEquals(2, results.get(1));
        assertEquals(3, results.get(2));
        assertEquals(4, results.get(3));
    }
}