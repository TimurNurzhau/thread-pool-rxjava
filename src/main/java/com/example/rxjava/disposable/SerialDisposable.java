package com.example.rxjava.disposable;

public class SerialDisposable implements Disposable {
    private volatile Disposable current;
    private volatile boolean disposed = false;

    public void set(Disposable disposable) {
        if (disposed) {
            disposable.dispose();
        } else {
            Disposable old = this.current;
            this.current = disposable;
            if (old != null) {
                old.dispose();
            }
        }
    }

    public Disposable get() {
        return current;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        Disposable d = current;
        if (d != null) {
            d.dispose();
            current = null;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}