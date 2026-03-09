package com.example.rxjava.core;

@FunctionalInterface
public interface Predicate<T> {
    boolean test(T t);
}