package com.example.rxjava;

import com.example.rxjava.core.Observable;
import com.example.rxjava.core.Observer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class OperatorsTest {
    private static final Logger logger = LoggerFactory.getLogger(OperatorsTest.class);

    @Test
    void testMapOperator() {
        List<Integer> results = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Integer> observable = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onComplete();
        });

        observable
                .map(x -> x * 2)
                .subscribe(new Observer<Integer>() {
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
        assertEquals(2, results.get(0));
        assertEquals(4, results.get(1));
        assertEquals(6, results.get(2));
        assertTrue(completed.get());
    }

    @Test
    void testFilterOperator() {
        List<Integer> results = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Integer> observable = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onNext(4);
            emitter.onNext(5);
            emitter.onComplete();
        });

        observable
                .filter(x -> x % 2 == 0)
                .subscribe(new Observer<Integer>() {
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

        assertEquals(2, results.size());
        assertEquals(2, results.get(0));
        assertEquals(4, results.get(1));
        assertTrue(completed.get());
    }

    @Test
    void testMapAndFilterTogether() {
        List<String> results = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<Integer> observable = Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onNext(4);
            emitter.onNext(5);
            emitter.onComplete();
        });

        observable
                .map(x -> x * 3)        // 3, 6, 9, 12, 15
                .filter(x -> x > 5)      // 6, 9, 12, 15
                .map(x -> "Number: " + x)
                .subscribe(new Observer<String>() {
                    @Override
                    public void onNext(String item) {
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

        // Должно быть 4 элемента: 6, 9, 12, 15
        assertEquals(4, results.size());
        assertEquals("Number: 6", results.get(0));
        assertEquals("Number: 9", results.get(1));
        assertEquals("Number: 12", results.get(2));
        assertEquals("Number: 15", results.get(3));
        assertTrue(completed.get());
    }
}