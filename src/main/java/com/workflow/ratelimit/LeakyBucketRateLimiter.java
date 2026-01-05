package com.workflow.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Leaky bucket rate limiter that ensures a constant output rate regardless of input bursts.
 *
 * <p>This implementation uses the leaky bucket algorithm where:
 *
 * <ul>
 *   <li>Requests are added to a bucket
 *   <li>Requests "leak" out at a constant rate
 *   <li>If bucket is full, requests are rejected
 *   <li>Output rate is always constant
 * </ul>
 *
 * <p><b>Characteristics:</b>
 *
 * <ul>
 *   <li><b>Constant Rate:</b> Output rate is perfectly constant
 *   <li><b>Smoothing:</b> Smooths out bursts effectively
 *   <li><b>Queue Behavior:</b> Acts like a queue with limited capacity
 *   <li><b>Strict:</b> No tolerance for bursts beyond bucket capacity
 * </ul>
 *
 * <p><b>Thread Safety:</b> This implementation is thread-safe using locks and atomic operations.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Network traffic shaping with constant output
 *   <li>Systems requiring predictable, steady load
 *   <li>When burst smoothing is more important than burst tolerance
 *   <li>Rate limiting to external systems with strict rate requirements
 * </ul>
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Process 10 requests per second with bucket capacity of 20
 * RateLimitStrategy limiter = new LeakyBucketRateLimiter(
 *     10,                         // Requests per second
 *     20,                         // Bucket capacity
 *     Duration.ofSeconds(1)       // Rate period
 * );
 *
 * // Wrap workflow
 * Workflow rateLimited = RateLimitedWorkflow.builder()
 *     .workflow(apiWorkflow)
 *     .rateLimitStrategy(limiter)
 *     .build();
 * }</pre>
 *
 * <p><b>Leaky Bucket Visualization:</b>
 *
 * <pre>
 * Input (bursty):    10  0  0  0  50  0  0  0  30
 *                     ↓  ↓  ↓  ↓   ↓  ↓  ↓  ↓   ↓
 *                  ┌──────────────────────────┐
 *                  │       Bucket (cap=20)    │
 *                  │  [=================]     │
 *                  └──────────┬───────────────┘
 *                             ↓ (leak rate: constant)
 * Output (smooth):   10 10 10 10  10 10 10 10  10
 *
 * Excess requests rejected when bucket full
 * </pre>
 *
 * <p><b>Comparison with Token Bucket:</b>
 *
 * <ul>
 *   <li><b>Token Bucket:</b> Allows bursts up to capacity, then enforces rate
 *   <li><b>Leaky Bucket:</b> Smooths bursts into constant output rate
 * </ul>
 *
 * @see RateLimitStrategy
 * @see TokenBucketRateLimiter
 */
public class LeakyBucketRateLimiter implements RateLimitStrategy {
  private final double requestsPerPeriod;
  private final long periodNanos;
  private final int capacity;
  private final Lock lock;

  // These are both protected by 'lock' to ensure a consistent state snapshot
  private double water; // Current water level in bucket
  private long lastLeakTimeNanos;

  /**
   * Creates a leaky bucket rate limiter.
   *
   * @param requestsPerPeriod number of requests that leak out per period
   * @param capacity maximum number of requests the bucket can hold
   * @param period time period for the leak rate
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public LeakyBucketRateLimiter(double requestsPerPeriod, int capacity, Duration period) {
    if (requestsPerPeriod <= 0) {
      throw new IllegalArgumentException("requestsPerPeriod must be positive");
    }
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be at least 1");
    }
    if (period.isNegative() || period.isZero()) {
      throw new IllegalArgumentException("period must be positive");
    }

    this.requestsPerPeriod = requestsPerPeriod;
    this.capacity = capacity;
    this.periodNanos = period.toNanos();
    this.water = 0.0;
    this.lastLeakTimeNanos = System.nanoTime();
    this.lock = new ReentrantLock();
  }

  /**
   * Creates a leaky bucket rate limiter with capacity equal to requests per period.
   *
   * @param requestsPerPeriod leak rate (also used as capacity)
   * @param period time period for the leak rate
   */
  public LeakyBucketRateLimiter(int requestsPerPeriod, Duration period) {
    this(requestsPerPeriod, requestsPerPeriod, period);
  }

  @Override
  public void acquire() throws InterruptedException {
    while (!tryAcquire()) {
      long sleepTimeNanos;
      lock.lock();
      try {
        leak();

        if (water < capacity) {
          continue; // Space became available
        }

        // Calculate time for water to leak enough
        double waterToLeak = getWaterToLeak();
        sleepTimeNanos = getSleepTimeNanos(waterToLeak);
      } finally {
        lock.unlock();
      }

      if (sleepTimeNanos > 0) {
        sleepNanos(sleepTimeNanos);
      }
    }
  }

  private double nanosPerRequest() {
    return periodNanos / requestsPerPeriod;
  }

  private long getSleepTimeNanos(double waterToLeak) {
    if (waterToLeak <= 0) return 0L;
    return Math.max(1L, (long) Math.ceil(waterToLeak * nanosPerRequest()));
  }

  private double getWaterToLeak() {
    return water - capacity + 1.0;
  }

  @Override
  public boolean tryAcquire() {
    lock.lock();
    try {
      leak();

      double newWater = water + 1.0;
      if (newWater <= capacity) {
        water = newWater;
        return true;
      }

      return false;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean tryAcquire(long timeoutMillis) throws InterruptedException {
    if (timeoutMillis < 0) {
      throw new IllegalArgumentException("timeoutMillis must be non-negative");
    }
    final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

    while (System.nanoTime() < deadlineNanos) {
      if (tryAcquire()) {
        return true;
      }

      long remainingTime = deadlineNanos - System.nanoTime();
      if (remainingTime <= 0) {
        break;
      }

      long sleepTimeNanos = TimeUnit.MILLISECONDS.toNanos(1);
      lock.lock();
      try {
        leak();

        if (water >= capacity) {
          // Calculate time for enough water to leak
          double waterToLeak = getWaterToLeak();
          sleepTimeNanos = getSleepTimeNanos(waterToLeak);
          sleepTimeNanos = Math.min(sleepTimeNanos, remainingTime);
        }
      } finally {
        lock.unlock();
      }

      if (sleepTimeNanos > 0) {
        sleepNanos(Math.min(sleepTimeNanos, TimeUnit.MILLISECONDS.toNanos(10)));
      }
    }

    return tryAcquire();
  }

  private static void sleepNanos(long sleepTimeNanos) throws InterruptedException {
    TimeUnit.NANOSECONDS.sleep(sleepTimeNanos);
  }

  @Override
  public int availablePermits() {
    lock.lock();
    try {
      leak();
      int permits = (int) Math.floor(capacity - water);
      return Math.max(0, permits);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void reset() {
    lock.lock();
    try {
      water = 0;
      lastLeakTimeNanos = System.nanoTime();
    } finally {
      lock.unlock();
    }
  }

  /** Leaks water from the bucket based on elapsed time. */
  private void leak() {
    long now = System.nanoTime();
    long elapsedNanos = now - lastLeakTimeNanos;
    double nanosPerRequest = nanosPerRequest();

    if (elapsedNanos > 0) {
      double leaked = (elapsedNanos / (double) periodNanos) * requestsPerPeriod;
      if (leaked > 0) {
        water = Math.max(0, water - leaked);
        long nanosConsumed = (long) (leaked * nanosPerRequest);
        lastLeakTimeNanos += nanosConsumed;
      }
    }
  }

  /**
   * Returns the current water level in the bucket.
   *
   * @return current water level
   */
  public double getCurrentWaterLevel() {
    lock.lock();
    try {
      leak();
      return water;
    } finally {
      lock.unlock();
    }
  }
}
