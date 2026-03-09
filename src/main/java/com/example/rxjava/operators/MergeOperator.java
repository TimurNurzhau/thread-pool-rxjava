package com.example.rxjava.operators;

import com.example.rxjava.core.Observable;
import com.example.rxjava.core.Observer;
import com.example.rxjava.disposable.CompositeDisposable;
import com.example.rxjava.disposable.Disposable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Оператор для объединения нескольких Observable в один.
 */
public class MergeOperator {

    /**
     * Объединяет несколько Observable в один, который emits все элементы из всех источников.
     *
     * @param sources массив Observable для объединения
     * @param <T> тип элементов
     * @return новый Observable, который emits элементы из всех источников
     */
    @SafeVarargs
    public static <T> Observable<T> merge(Observable<T>... sources) {
        return Observable.create(emitter -> {
            CompositeDisposable disposables = new CompositeDisposable();
            AtomicInteger completedCount = new AtomicInteger(0);
            int totalSources = sources.length;

            for (Observable<T> source : sources) {
                Disposable disposable = source.subscribe(
                        value -> emitter.onNext(value),
                        error -> emitter.onError(error),
                        () -> {
                            if (completedCount.incrementAndGet() == totalSources) {
                                emitter.onComplete();
                            }
                        }
                );
                disposables.add(disposable);
            }
        });
    }

    /**
     * Объединяет два Observable в один.
     */
    public static <T> Observable<T> merge(Observable<T> source1, Observable<T> source2) {
        return merge(new Observable[]{source1, source2});
    }
}