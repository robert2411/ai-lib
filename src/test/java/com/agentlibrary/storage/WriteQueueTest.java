package com.agentlibrary.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WriteQueueTest {

    private WriteQueue queue;

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.shutdown();
        }
    }

    @Test
    void singleTaskExecutesAndReturnsResult() {
        queue = new WriteQueue();
        String result = queue.submit(() -> "hello");
        assertEquals("hello", result);
    }

    @Test
    void tasksExecuteSequentially() {
        queue = new WriteQueue();
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < 5; i++) {
            final int index = i;
            queue.submit(() -> {
                order.add(index);
                return null;
            });
        }

        assertEquals(List.of(0, 1, 2, 3, 4), order);
    }

    @Test
    void concurrentSubmittersAreSerialised() throws InterruptedException {
        queue = new WriteQueue();
        int numThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    queue.submit(() -> {
                        // Record thread name to verify single-writer
                        executionOrder.add(Thread.currentThread().getName());
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads at once
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        // All tasks ran on the same thread (write-queue)
        assertEquals(numThreads, executionOrder.size());
        for (String threadName : executionOrder) {
            assertEquals("write-queue", threadName);
        }
    }

    @Test
    void exceptionInTaskPropagatesAsStorageException() {
        queue = new WriteQueue();
        StorageException ex = assertThrows(StorageException.class, () ->
                queue.submit(() -> {
                    throw new RuntimeException("boom");
                })
        );
        assertNotNull(ex.getCause());
        assertEquals("boom", ex.getCause().getMessage());
    }

    @Test
    void timeoutProducesStorageException() {
        queue = new WriteQueue(1); // 1-second timeout
        StorageException ex = assertThrows(StorageException.class, () ->
                queue.submit(() -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "should not return";
                })
        );
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    void shutdownPreventsNewSubmissions() {
        queue = new WriteQueue();
        queue.shutdown();
        assertThrows(StorageException.class, () ->
                queue.submit(() -> "should fail")
        );
    }

    @Test
    void runnableTaskExecutes() {
        queue = new WriteQueue();
        List<String> results = new ArrayList<>();
        queue.submit(() -> results.add("executed"));
        assertEquals(List.of("executed"), results);
    }
}
