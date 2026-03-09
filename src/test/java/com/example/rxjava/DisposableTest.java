package com.example.rxjava;

import com.example.rxjava.core.Observable;
import com.example.rxjava.core.Observer;
import com.example.rxjava.disposable.CompositeDisposable;
import com.example.rxjava.disposable.Disposable;
import com.example.rxjava.disposable.SerialDisposable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class DisposableTest {
    private static final Logger logger = LoggerFactory.getLogger(DisposableTest.class);

    @Test
    void testDisposable() {
        AtomicBoolean disposed = new AtomicBoolean(false);

        Disposable disposable = new Disposable() {
            @Override
            public void dispose() {
                disposed.set(true);
            }

            @Override
            public boolean isDisposed() {
                return disposed.get();
            }
        };

        assertFalse(disposable.isDisposed());
        disposable.dispose();
        assertTrue(disposable.isDisposed());
    }

    @Test
    void testCompositeDisposable() {
        CompositeDisposable composite = new CompositeDisposable();

        AtomicBoolean disposed1 = new AtomicBoolean(false);
        AtomicBoolean disposed2 = new AtomicBoolean(false);

        Disposable d1 = new Disposable() {
            @Override
            public void dispose() { disposed1.set(true); }
            @Override
            public boolean isDisposed() { return disposed1.get(); }
        };

        Disposable d2 = new Disposable() {
            @Override
            public void dispose() { disposed2.set(true); }
            @Override
            public boolean isDisposed() { return disposed2.get(); }
        };

        composite.add(d1);
        composite.add(d2);

        assertFalse(composite.isDisposed());
        assertFalse(disposed1.get());
        assertFalse(disposed2.get());

        composite.dispose();

        assertTrue(composite.isDisposed());
        assertTrue(disposed1.get());
        assertTrue(disposed2.get());
    }

    @Test
    void testSerialDisposable() {
        SerialDisposable serial = new SerialDisposable();

        AtomicBoolean disposed1 = new AtomicBoolean(false);
        AtomicBoolean disposed2 = new AtomicBoolean(false);

        Disposable d1 = new Disposable() {
            @Override
            public void dispose() { disposed1.set(true); }
            @Override
            public boolean isDisposed() { return disposed1.get(); }
        };

        Disposable d2 = new Disposable() {
            @Override
            public void dispose() { disposed2.set(true); }
            @Override
            public boolean isDisposed() { return disposed2.get(); }
        };

        serial.set(d1);
        assertFalse(serial.isDisposed());
        assertFalse(disposed1.get());

        serial.set(d2); // Это должно вызвать dispose для d1
        assertTrue(disposed1.get());
        assertFalse(disposed2.get());

        serial.dispose();
        assertTrue(serial.isDisposed());
        assertTrue(disposed2.get());
    }
}