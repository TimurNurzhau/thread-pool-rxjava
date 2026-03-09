package com.example.rxjava.disposable;

import java.util.ArrayList;
import java.util.List;

public class CompositeDisposable implements Disposable {
    private final List<Disposable> disposables = new ArrayList<>();
    private volatile boolean disposed = false;

    public void add(Disposable disposable) {
        if (disposed) {
            disposable.dispose();
        } else {
            synchronized (disposables) {
                disposables.add(disposable);
            }
        }
    }

    public void addAll(Disposable... disposables) {
        for (Disposable d : disposables) {
            add(d);
        }
    }

    public void remove(Disposable disposable) {
        synchronized (disposables) {
            disposables.remove(disposable);
        }
    }

    public int getDisposablesCount() {
        synchronized (disposables) {
            return disposables.size();
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        synchronized (disposables) {
            for (Disposable d : disposables) {
                d.dispose();
            }
            disposables.clear();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}