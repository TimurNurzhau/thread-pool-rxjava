package com.example.rxjava.operators;

import com.example.rxjava.core.Observable;
import com.example.rxjava.core.Observer;
import com.example.rxjava.disposable.CompositeDisposable;
import com.example.rxjava.disposable.Disposable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Оператор для последовательного объединения Observable.
 */
public class ConcatOperator {

    /**
     * Последовательно объединяет несколько Observable.
     *
     * @param sources массив Observable для объединения
     * @param <T> тип элементов
     * @return новый Observable, который последовательно emits элементы из всех источников
     */
    @SafeVarargs
    public static <T> Observable<T> concat(Observable<T>... sources) {
        return Observable.create(emitter -> {
            CompositeDisposable disposables = new CompositeDisposable();
            AtomicInteger index = new AtomicInteger(0);

            processNext(sources, index, emitter, disposables);
        });
    }

    private static <T> void processNext(
            Observable<T>[] sources,
            AtomicInteger index,
            Observer<T> emitter,
            CompositeDisposable disposables) {

        int current = index.getAndIncrement();
        if (current >= sources.length) {
            emitter.onComplete();
            return;
        }

        Disposable disposable = sources[current].subscribe(
                value -> emitter.onNext(value),
                error -> emitter.onError(error),
                () -> processNext(sources, index, emitter, disposables)
        );

        disposables.add(disposable);
    }
}