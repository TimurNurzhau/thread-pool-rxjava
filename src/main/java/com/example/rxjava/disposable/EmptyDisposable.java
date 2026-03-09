package com.example.rxjava.disposable;

public class EmptyDisposable implements Disposable {
    private volatile boolean disposed = false;

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public static EmptyDisposable disposed() {
        EmptyDisposable d = new EmptyDisposable();
        d.dispose();
        return d;
    }
}