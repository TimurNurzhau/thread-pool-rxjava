package com.example.rxjava.operators;

import com.example.rxjava.core.Observable;
import com.example.rxjava.core.Observer;
import com.example.rxjava.disposable.CompositeDisposable;
import com.example.rxjava.disposable.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Оператор для комбинирования элементов нескольких Observable попарно.
 */
public class ZipOperator {

    /**
     * Функция для комбинирования двух элементов.
     */
    @FunctionalInterface
    public interface ZipFunction2<T1, T2, R> {
        R apply(T1 t1, T2 t2);
    }

    /**
     * Объединяет два Observable, попарно комбинируя их элементы.
     *
     * @param source1 первый Observable
     * @param source2 второй Observable
     * @param zipper функция комбинирования
     * @param <T1> тип элементов первого Observable
     * @param <T2> тип элементов второго Observable
     * @param <R> тип результата
     * @return новый Observable с комбинированными элементами
     */
    public static <T1, T2, R> Observable<R> zip(
            Observable<T1> source1,
            Observable<T2> source2,
            ZipFunction2<T1, T2, R> zipper) {

        return Observable.create(emitter -> {
            CompositeDisposable disposables = new CompositeDisposable();
            List<T1> list1 = new ArrayList<>();
            List<T2> list2 = new ArrayList<>();
            Object lock = new Object();
            AtomicInteger completedCount = new AtomicInteger(0);

            Disposable d1 = source1.subscribe(
                    value -> {
                        synchronized (lock) {
                            list1.add(value);
                            if (!list2.isEmpty()) {
                                T2 val2 = list2.remove(0);
                                emitter.onNext(zipper.apply(value, val2));
                            }
                        }
                    },
                    emitter::onError,
                    () -> {
                        if (completedCount.incrementAndGet() == 2) {
                            emitter.onComplete();
                        }
                    }
            );

            Disposable d2 = source2.subscribe(
                    value -> {
                        synchronized (lock) {
                            list2.add(value);
                            if (!list1.isEmpty()) {
                                T1 val1 = list1.remove(0);
                                emitter.onNext(zipper.apply(val1, value));
                            }
                        }
                    },
                    emitter::onError,
                    () -> {
                        if (completedCount.incrementAndGet() == 2) {
                            emitter.onComplete();
                        }
                    }
            );

            disposables.addAll(d1, d2);
        });
    }
}