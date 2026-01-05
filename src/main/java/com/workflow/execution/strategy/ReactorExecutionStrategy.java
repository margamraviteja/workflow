package com.workflow.execution.strategy;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Execution strategy backed by Project Reactor's scheduling system.
 *
 * <p><b>Purpose:</b> Provides non-blocking concurrent execution using reactive streams. Leverages
 * Reactor's efficient scheduler to manage thread pools and handle backpressure. Suitable for
 * high-concurrency scenarios with many I/O operations.
 *
 * <p><b>Scheduler Options:</b>
 *
 * <ul>
 *   <li><b>boundedElastic (default):</b> Optimized for blocking I/O tasks, manages thread pool
 *       dynamically
 *   <li><b>parallel:</b> Fixed-size pool for CPU-intensive work (typically # of CPU cores)
 *   <li><b>immediate:</b> Executes immediately on the calling thread (no async)
 *   <li><b>single:</b> Dedicated single-threaded scheduler
 *   <li><b>Custom Scheduler:</b> Use any Reactor scheduler
 * </ul>
 *
 * <p><b>Execution Flow:</b>
 *
 * <ol>
 *   <li>Callable wrapped in Reactor Mono
 *   <li>Mono scheduled on the configured Scheduler
 *   <li>Result wrapped in CompletableFuture for compatibility
 *   <li>Future returned immediately
 * </ol>
 *
 * <p><b>Thread Safety:</b> This strategy is thread-safe. Can submit tasks concurrently from
 * multiple threads.
 *
 * <p><b>Resource Management:</b> The strategy must be {@link #close()} when done. This disposes the
 * reactor scheduler and cleans up resources.
 *
 * <p><b>Backpressure Handling:</b> Unlike thread pools, Reactor handles backpressure gracefully,
 * making it ideal for scenarios where more tasks arrive than can be processed.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>High-concurrency I/O-heavy workflows
 *   <li>Microservices with many parallel calls
 *   <li>Reactive systems integration
 *   <li>Handling thousands of concurrent connections
 *   <li>Systems requiring backpressure handling
 * </ul>
 *
 * <p><b>Example Usage - Default boundedElastic Scheduler:</b>
 *
 * <pre>{@code
 * ExecutionStrategy strategy = new ReactorExecutionStrategy();
 *
 * CompletableFuture<String> result = strategy.submit(() -> {
 *     // Blocking I/O operation
 *     makeHttpCall();
 *     queryDatabase();
 *     return "Data fetched";
 * });
 *
 * result.thenAccept(data -> System.out.println("Result: " + data));
 * strategy.close();  // Dispose reactor scheduler
 * }</pre>
 *
 * <p><b>Example Usage - Parallel Scheduler (CPU-Bound):</b>
 *
 * <pre>{@code
 * // Use parallel scheduler for CPU-intensive work
 * Scheduler parallelScheduler = Schedulers.parallel();
 * ExecutionStrategy strategy = new ReactorExecutionStrategy(parallelScheduler);
 *
 * CompletableFuture<Integer> result = strategy.submit(() -> {
 *     // CPU-intensive calculation
 *     return computeExpensiveResult();
 * });
 *
 * strategy.close();
 * }</pre>
 *
 * <p><b>Example Usage - With Reactor Mono Chaining:</b>
 *
 * <pre>{@code
 * ExecutionStrategy strategy = new ReactorExecutionStrategy();
 *
 * // Can be used in reactive pipelines
 * CompletableFuture<String> f1 = strategy.submit(() -> fetchData1());
 * CompletableFuture<String> f2 = strategy.submit(() -> fetchData2());
 * CompletableFuture<String> f3 = strategy.submit(() -> combineResults());
 *
 * // Combine results reactively
 * CompletableFuture.allOf(f1, f2, f3).thenAccept(v -> {
 *     System.out.println("All tasks completed");
 * });
 * strategy.close();
 * }</pre>
 *
 * <p><b>Performance Characteristics:</b>
 *
 * <ul>
 *   <li><b>boundedElastic:</b> ~12-32 threads, high throughput for I/O
 *   <li><b>parallel:</b> Fixed pool size = CPU cores, low latency for CPU work
 *   <li><b>Memory efficient:</b> Better memory usage than thread pools for many concurrent tasks
 * </ul>
 *
 * @see ThreadPoolExecutionStrategy
 * @see ExecutionStrategy
 * @see reactor.core.scheduler.Scheduler
 */
public final class ReactorExecutionStrategy implements ExecutionStrategy {
  private final Scheduler scheduler;

  /** Create strategy using boundedElastic scheduler. */
  public ReactorExecutionStrategy() {
    this(Schedulers.boundedElastic());
  }

  /**
   * Create strategy using custom Reactor scheduler.
   *
   * @param scheduler the Reactor {@link Scheduler} used to schedule task execution
   */
  public ReactorExecutionStrategy(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  @Override
  public <T> CompletableFuture<T> submit(Callable<T> task) {
    return Mono.fromCallable(task)
        .subscribeOn(scheduler)
        .publishOn(scheduler) // ensures completion runs on scheduler too
        .toFuture();
  }

  @Override
  public void close() {
    scheduler.dispose();
  }
}
