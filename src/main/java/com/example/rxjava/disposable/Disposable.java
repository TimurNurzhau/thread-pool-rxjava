package com.example.rxjava.disposable;

public interface Disposable {
    void dispose();
    boolean isDisposed();
}