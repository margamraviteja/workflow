package com.workflow.ratelimit;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sliding window rate limiter that tracks individual request timestamps for accurate rate limiting.
 *
 * <p>This implementation maintains a queue of request timestamps and only allows requests if fewer
 * than {@code maxRequests} have occurred in the sliding window.
 *
 * <p><b>Characteristics:</b>
 *
 * <ul>
 *   <li><b>Accurate:</b> No boundary effects like fixed window
 *   <li><b>Smooth:</b> Distributes load more evenly
 *   <li><b>Memory Cost:</b> Stores timestamp for each request in the window
 *   <li><b>Performance:</b> Cleanup operations on each request
 * </ul>
 *
 * <p><b>Thread Safety:</b> This implementation is thread-safe using locks for critical sections.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Accurate API rate limiting without boundary issues
 *   <li>When smooth request distribution is important
 *   <li>When memory for storing timestamps is acceptable
 * </ul>
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Allow 100 requests per minute with sliding window
 * RateLimitStrategy limiter = new SlidingWindowRateLimiter(100, Duration.ofMinutes(1));
 *
 * // Wrap workflow
 * Workflow rateLimited = RateLimitedWorkflow.builder()
 *     .workflow(apiWorkflow)
 *     .rateLimitStrategy(limiter)
 *     .build();
 * }</pre>
 *
 * <p><b>Comparison with Fixed Window:</b>
 *
 * <pre>
 * Fixed Window:
 * Time:     0s -------- 59s | 60s -------- 119s
 * Window:   [  Window 1    ] [   Window 2    ]
 * Can allow 2x limit at boundary
 *
 * Sliding Window:
 * Time:     0s -------- 59s   60s -------- 119s
 * Window:   [---- 60s -----] [---- 60s -----]
 * Always exactly maxRequests in any 60s period
 * </pre>
 *
 * @see RateLimitStrategy
 * @see FixedWindowRateLimiter
 */
public class SlidingWindowRateLimiter implements RateLimitStrategy {
  private final int maxRequests;
  private final long windowSizeNanos;
  private final Queue<Long> requestTimestamps;
  private final Lock lock;

  /**
   * Creates a sliding window rate limiter.
   *
   * @param maxRequests maximum number of requests allowed in the sliding window
   * @param windowSize duration of the sliding window
   * @throws IllegalArgumentException if maxRequests is less than 1 or windowSize is not positive
   */
  public SlidingWindowRateLimiter(int maxRequests, Duration windowSize) {
    if (maxRequests < 1) {
      throw new IllegalArgumentException("maxRequests must be at least 1");
    }
    if (windowSize.isNegative() || windowSize.isZero()) {
      throw new IllegalArgumentException("windowSize must be positive");
    }

    this.maxRequests = maxRequests;
    this.windowSizeNanos = windowSize.toNanos();
    this.requestTimestamps = new ArrayDeque<>(this.maxRequests);
    this.lock = new ReentrantLock();
  }

  @Override
  public void acquire() throws InterruptedException {
    while (!tryAcquire()) {
      long now = System.nanoTime();
      long oldestAllowedTimestamp = now - windowSizeNanos;
      long sleepTimeNanos;

      lock.lock();
      try {
        // Find the oldest timestamp that would need to expire
        sleepTimeNanos = getSleepTimeNanos(oldestAllowedTimestamp, 0, now);
      } finally {
        lock.unlock();
      }

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
    long now = System.nanoTime();
    long oldestAllowedTimestamp = now - windowSizeNanos;

    lock.lock();
    try {
      // Remove expired timestamps
      removeExpired(oldestAllowedTimestamp);

      // Check if we can add a new request
      if (requestTimestamps.size() < maxRequests) {
        requestTimestamps.offer(now);
        return true;
      }

      return false;
    } finally {
      lock.unlock();
    }
  }

  private void removeExpired(long oldestAllowedTimestamp) {
    while (!requestTimestamps.isEmpty() && requestTimestamps.peek() <= oldestAllowedTimestamp) {
      requestTimestamps.poll();
    }
  }

  @Override
  public boolean tryAcquire(long timeoutMillis) throws InterruptedException {
    if (timeoutMillis < 0) {
      throw new IllegalArgumentException("timeoutMillis must be non-negative");
    }
    long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

    while (System.nanoTime() < deadlineNanos) {
      if (tryAcquire()) {
        return true;
      }

      long now = System.nanoTime();
      long oldestAllowedTimestamp = now - windowSizeNanos;
      long remainingTime = deadlineNanos - now;
      if (remainingTime <= 0) {
        break;
      }

      // Calculate how long to wait for next available slot
      long sleepTimeNanos = 0;
      lock.lock();
      try {
        sleepTimeNanos = getSleepTimeNanos(oldestAllowedTimestamp, sleepTimeNanos, now);
      } finally {
        lock.unlock();
      }

      if (sleepTimeNanos > 0) {
        sleepNanos(Math.min(sleepTimeNanos, TimeUnit.MILLISECONDS.toNanos(10)));
      }
    }

    return tryAcquire();
  }

  private long getSleepTimeNanos(long oldestAllowedTimestamp, long sleepTimeNanos, long now) {
    // Find the oldest timestamp that would need to expire
    Long oldestTimestamp = requestTimestamps.peek();
    if (oldestTimestamp != null && oldestTimestamp > oldestAllowedTimestamp) {
      sleepTimeNanos = (oldestTimestamp + windowSizeNanos) - now;
    }
    return sleepTimeNanos;
  }

  @Override
  public int availablePermits() {
    long now = System.nanoTime();
    long oldestAllowedTimestamp = now - windowSizeNanos;

    lock.lock();
    try {
      // Remove expired timestamps
      removeExpired(oldestAllowedTimestamp);
      return Math.max(0, maxRequests - requestTimestamps.size());
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void reset() {
    lock.lock();
    try {
      requestTimestamps.clear();
    } finally {
      lock.unlock();
    }
  }
}
