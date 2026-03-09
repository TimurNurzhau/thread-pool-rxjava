package com.example.rxjava.core;

@FunctionalInterface
public interface Function<T, R> {
    R apply(T t);
}