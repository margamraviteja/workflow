package com.workflow.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fixed window rate limiter that allows a maximum number of requests within a fixed time window.
 *
 * <p>This implementation divides time into fixed windows and allows up to {@code maxRequests}
 * executions per window. When a window expires, the counter resets.
 *
 * <p><b>Characteristics:</b>
 *
 * <ul>
 *   <li><b>Simple:</b> Easy to understand and implement
 *   <li><b>Memory Efficient:</b> Only tracks current count and window start
 *   <li><b>Burst Friendly:</b> Allows bursts at window boundaries
 *   <li><b>Edge Case:</b> Can allow 2x limit at window boundaries (e.g., all requests at end of
 *       window 1, all at start of window 2)
 * </ul>
 *
 * <p><b>Thread Safety:</b> This implementation is thread-safe using atomic operations.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Simple API rate limiting (e.g., 1000 requests per hour)
 *   <li>When burst behavior is acceptable
 *   <li>When memory efficiency is important
 * </ul>
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Allow 100 requests per minute
 * RateLimitStrategy limiter = new FixedWindowRateLimiter(100, Duration.ofMinutes(1));
 *
 * // Wrap workflow
 * Workflow rateLimited = RateLimitedWorkflow.builder()
 *     .workflow(apiWorkflow)
 *     .rateLimitStrategy(limiter)
 *     .build();
 * }</pre>
 *
 * <p><b>Window Boundary Example:</b>
 *
 * <pre>
 * Time:     0s ----------- 59s | 60s ----------- 119s
 * Window:   [    Window 1     ] [     Window 2      ]
 * Requests: 99 requests at 59s | 100 requests at 60s
 * Total:    199 requests in 1 second (at boundary)
 * </pre>
 *
 * @see RateLimitStrategy
 * @see SlidingWindowRateLimiter
 */
public class FixedWindowRateLimiter implements RateLimitStrategy {
  private final int maxRequests;
  private final long windowSizeNanos;
  private final AtomicReference<WindowState> state;

  private static class WindowState {
    final long windowStartNanos;
    final int count;

    WindowState(long windowStartNanos, int count) {
      this.windowStartNanos = windowStartNanos;
      this.count = count;
    }
  }

  /**
   * Creates a fixed window rate limiter.
   *
   * @param maxRequests maximum number of requests allowed per window
   * @param windowSize duration of the time window
   * @throws IllegalArgumentException if maxRequests is less than 1 or windowSize is not positive
   */
  public FixedWindowRateLimiter(int maxRequests, Duration windowSize) {
    if (maxRequests < 1) {
      throw new IllegalArgumentException("maxRequests must be at least 1");
    }
    if (windowSize.isNegative() || windowSize.isZero()) {
      throw new IllegalArgumentException("windowSize must be positive");
    }

    this.maxRequests = maxRequests;
    this.windowSizeNanos = windowSize.toNanos();
    this.state = new AtomicReference<>(new WindowState(System.nanoTime(), 0));
  }

  @Override
  public void acquire() throws InterruptedException {
    while (!tryAcquire()) {
      long now = System.nanoTime();
      WindowState windowState = state.get();
      long nextWindow = windowState.windowStartNanos + windowSizeNanos;
      long sleepTimeNanos = nextWindow - now;

      if (sleepTimeNanos > 0) {
        sleepNanos(Math.min(sleepTimeNanos, TimeUnit.MILLISECONDS.toNanos(10)));
      }
    }
  }

  private static void sleepNanos(long sleepTimeNanos) throws InterruptedException {
    TimeUnit.NANOSECONDS.sleep(sleepTimeNanos);
  }

  @Override
  public boolean tryAcquire() {
    while (true) {
      long now = System.nanoTime();
      WindowState windowState = state.get();
      long currentWindowStart = windowState.windowStartNanos;
      int currentCount = windowState.count;

      // Check if we need to start a new window
      if (now - currentWindowStart >= windowSizeNanos) {
        // Try to update the window
        if (state.compareAndSet(windowState, new WindowState(now, 1))) {
          return true;
        }
        // Another thread updated the window, retry
      }
      // Check if we can increment within current window
      else if (currentCount < maxRequests) {
        if (state.compareAndSet(
            windowState, new WindowState(currentWindowStart, currentCount + 1))) {
          return true;
        }
        // Another thread incremented, retry
      }
      // Rate limit exceeded
      else {
        return false;
      }
    }
  }

  @Override
  public boolean tryAcquire(long timeoutMillis) throws InterruptedException {
    if (timeoutMillis < 0) {
      throw new IllegalArgumentException("timeoutMillis must be non-negative");
    }
    final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

    while (System.nanoTime() < deadline) {
      if (tryAcquire()) {
        return true;
      }

      WindowState windowState = state.get();
      long now = System.nanoTime();
      long nextWindow = windowState.windowStartNanos + windowSizeNanos;
      long nanosUntilNextWindow = nextWindow - now;

      long nanosLeft = deadline - now;
      long sleepTimeNanos = Math.min(nanosUntilNextWindow, nanosLeft);
      if (sleepTimeNanos > 0) {
        sleepNanos(Math.min(sleepTimeNanos, TimeUnit.MILLISECONDS.toNanos(10)));
      }
    }

    return false;
  }

  @Override
  public int availablePermits() {
    long now = System.nanoTime();
    WindowState windowState = state.get();

    // If window has expired, all permits are available
    if (now - windowState.windowStartNanos >= windowSizeNanos) {
      return maxRequests;
    }

    return Math.max(0, maxRequests - windowState.count);
  }

  @Override
  public void reset() {
    state.set(new WindowState(System.nanoTime(), 0));
  }
}
