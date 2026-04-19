package com.agentlibrary.storage;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Single-writer queue serialising all write operations to prevent JGit concurrency issues.
 * Uses a single-threaded executor internally.
 */
public class WriteQueue {

    private final ExecutorService executor;
    private final long timeoutSeconds;

    public WriteQueue() {
        this(30);
    }

    public WriteQueue(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "write-queue");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Submits a task that produces a result. Blocks the calling thread until
     * the task completes or times out.
     *
     * @param task the task to execute
     * @param <T>  the result type
     * @return the result of the task
     * @throws StorageException if the task fails, times out, or is rejected
     */
    public <T> T submit(Supplier<T> task) {
        try {
            Future<T> future = executor.submit(task::get);
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            throw new StorageException("Write queue is shut down", e);
        } catch (TimeoutException e) {
            throw new StorageException("Write operation timed out after " + timeoutSeconds + " seconds", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StorageException se) {
                throw se;
            }
            throw new StorageException("Write operation failed", cause != null ? cause : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("Write operation interrupted", e);
        }
    }

    /**
     * Submits a task that produces no result.
     *
     * @param task the task to execute
     * @throws StorageException if the task fails, times out, or is rejected
     */
    public void submit(Runnable task) {
        submit(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Shuts down the write queue. No new tasks will be accepted after this call.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
