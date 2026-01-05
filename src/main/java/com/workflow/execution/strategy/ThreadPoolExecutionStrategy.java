package com.workflow.execution.strategy;

import java.util.concurrent.*;
import java.util.concurrent.Callable;

/**
 * Execution strategy that submits callables to a thread pool for concurrent execution.
 *
 * <p><b>Purpose:</b> Provides parallel execution of workflows using a configurable thread pool.
 * Suitable for CPU-intensive and I/O-bound tasks. Supports different pool configurations from
 * cached pools to fixed pools to virtual threads.
 *
 * <p><b>Thread Pool Options:</b>
 *
 * <ul>
 *   <li><b>Cached Pool (default):</b> Creates threads as needed, reuses idle threads
 *       (CachedThreadPool)
 *   <li><b>Fixed Pool:</b> Fixed number of worker threads (FixedThreadPool)
 *   <li><b>Virtual Threads (Java 21+):</b> Lightweight threads for massive concurrency
 *   <li><b>Custom ExecutorService:</b> Use your own custom executor
 * </ul>
 *
 * <p><b>Execution Flow:</b>
 *
 * <ol>
 *   <li>Callable is submitted to the executor
 *   <li>A thread (or virtual thread) is allocated to execute the callable
 *   <li>Result or exception is captured in a CompletableFuture
 *   <li>CompletableFuture is returned immediately
 * </ol>
 *
 * <p><b>Thread Safety:</b> This strategy is thread-safe. Can submit tasks from multiple threads
 * concurrently.
 *
 * <p><b>Resource Management:</b> The strategy must be {@link #close()} when done to shut down the
 * executor. Failure to close may leak thread pool resources.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Parallel workflows with thread pool management
 *   <li>CPU-intensive parallel tasks
 *   <li>I/O-bound parallel operations
 *   <li>Bounded concurrency control (fixed thread pool)
 *   <li>High-concurrency scenarios (virtual threads)
 * </ul>
 *
 * <p><b>Example Usage - Cached Thread Pool (Default):</b>
 *
 * <pre>{@code
 * ExecutionStrategy strategy = new ThreadPoolExecutionStrategy();
 *
 * CompletableFuture<Integer> result = strategy.submit(() -> {
 *     int sum = 0;
 *     for (int i = 1; i <= 1000000; i++) {
 *         sum += i;
 *     }
 *     return sum;
 * });
 *
 * result.thenAccept(sum -> System.out.println("Sum: " + sum));
 * strategy.close();
 * }</pre>
 *
 * <p><b>Example Usage - Fixed Thread Pool:</b>
 *
 * <pre>{@code
 * // Use exactly 4 threads
 * ExecutionStrategy strategy = new ThreadPoolExecutionStrategy(4);
 *
 * // All tasks will be queued if more than 4 are submitted concurrently
 * for (int i = 0; i < 10; i++) {
 *     int taskNum = i;
 *     strategy.submit(() -> {
 *         System.out.println("Task " + taskNum + " on " + Thread.currentThread().getName());
 *         return "Done " + taskNum;
 *     });
 * }
 * strategy.close();
 * }</pre>
 *
 * <p><b>Example Usage - Virtual Threads (Java 21+):</b>
 *
 * <pre>{@code
 * // Create thousands of lightweight virtual threads
 * ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
 * ExecutionStrategy strategy = new ThreadPoolExecutionStrategy(virtualExecutor);
 *
 * // Can handle thousands of concurrent tasks efficiently
 * for (int i = 0; i < 10000; i++) {
 *     strategy.submit(() -> {
 *         Thread.sleep(100);  // I/O-like operation
 *         return "Done";
 *     });
 * }
 * strategy.close();
 * }</pre>
 *
 * <p><b>Example Usage - Custom Executor with Monitoring:</b>
 *
 * <pre>{@code
 * ExecutorService customExecutor = new ThreadPoolExecutor(
 *     2, 5,  // Core and max threads
 *     60, TimeUnit.SECONDS,
 *     new LinkedBlockingQueue<>(100)
 * );
 * ExecutionStrategy strategy = new ThreadPoolExecutionStrategy(customExecutor);
 * }</pre>
 *
 * <p><b>Performance Tuning:</b>
 *
 * <ul>
 *   <li><b>CPU-bound tasks:</b> threads ≈ number of CPU cores
 *   <li><b>I/O-bound tasks:</b> larger thread pool (e.g., 2-3x cores)
 *   <li><b>Virtual threads:</b> can use thousands without performance penalty
 * </ul>
 *
 * @see ReactorExecutionStrategy
 * @see ExecutionStrategy
 * @see java.util.concurrent.ExecutorService
 */
public final class ThreadPoolExecutionStrategy implements ExecutionStrategy {
  private final ExecutorService executor;

  /** Create a ThreadPoolExecutionStrategy using a cached thread pool. */
  public ThreadPoolExecutionStrategy() {
    this(Executors.newCachedThreadPool());
  }

  /**
   * Create a ThreadPoolExecutionStrategy with a fixed-size thread pool.
   *
   * @param threads number of threads in the pool
   */
  public ThreadPoolExecutionStrategy(int threads) {
    this(Executors.newFixedThreadPool(threads));
  }

  /**
   * Create a ThreadPoolExecutionStrategy that uses the provided ExecutorService.
   *
   * @param executor the ExecutorService to use
   */
  public ThreadPoolExecutionStrategy(ExecutorService executor) {
    this.executor = executor;
  }

  @Override
  public <T> CompletableFuture<T> submit(Callable<T> task) {
    CompletableFuture<T> cf = new CompletableFuture<>();
    executor.submit(
        () -> {
          try {
            T r = task.call();
            cf.complete(r);
          } catch (Exception t) {
            cf.completeExceptionally(t);
          }
        });
    return cf;
  }

  @Override
  public void close() {
    executor.close();
  }
}
