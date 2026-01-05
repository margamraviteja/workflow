package com.workflow.execution.strategy;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Pluggable execution strategy abstraction. Implementations decide how submitted callables are
 * scheduled and executed (platform threads, virtual threads, reactor, etc.).
 *
 * <p>Implementations must also handle proper resource cleanup when {@link #close()} is called.
 *
 * <p>Example implementations include {@link ThreadPoolExecutionStrategy} and {@link
 * ReactorExecutionStrategy}.
 *
 * <p>Users can provide custom implementations to suit specific execution requirements.
 *
 * @see ThreadPoolExecutionStrategy
 * @see ReactorExecutionStrategy
 */
public interface ExecutionStrategy extends AutoCloseable {
  /**
   * Submit a callable for execution and return a CompletableFuture representing it.
   *
   * @param <T> return type
   * @param task callable
   * @return completable future
   */
  <T> CompletableFuture<T> submit(Callable<T> task);
}
