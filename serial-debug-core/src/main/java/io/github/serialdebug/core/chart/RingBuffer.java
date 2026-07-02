package io.github.serialdebug.core.chart;

import java.util.Arrays;

public class RingBuffer<T> {

    private final T[] buffer;
    private final int capacity;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    @SuppressWarnings("unchecked")
    public RingBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.buffer = (T[]) new Object[capacity];
    }

    public synchronized void offer(T item) {
        if (item == null) throw new NullPointerException("null not allowed");
        buffer[head] = item;
        head = (head + 1) % capacity;
        if (size < capacity) {
            size++;
        } else {
            tail = (tail + 1) % capacity;
        }
    }

    public synchronized T poll() {
        if (size == 0) return null;
        T item = buffer[tail];
        buffer[tail] = null;
        tail = (tail + 1) % capacity;
        size--;
        return item;
    }

    public synchronized int size() { return size; }
    public int capacity() { return capacity; }
    public synchronized boolean isEmpty() { return size == 0; }

    public synchronized void clear() {
        Arrays.fill(buffer, null);
        head = 0;
        tail = 0;
        size = 0;
    }
}
