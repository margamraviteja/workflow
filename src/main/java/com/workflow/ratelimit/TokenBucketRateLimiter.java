package com.workflow.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token bucket rate limiter that allows controlled bursts while maintaining average rate.
 *
 * <p>This implementation uses the token bucket algorithm where:
 *
 * <ul>
 *   <li>Tokens are added to a bucket at a fixed rate
 *   <li>Each request consumes one token
 *   <li>Requests are allowed if tokens are available
 *   <li>Bucket has a maximum capacity allowing bursts
 * </ul>
 *
 * <p><b>Characteristics:</b>
 *
 * <ul>
 *   <li><b>Burst Support:</b> Allows bursts up to bucket capacity
 *   <li><b>Average Rate:</b> Maintains average rate over time
 *   <li><b>Smooth:</b> No boundary effects
 *   <li><b>Memory Efficient:</b> Only stores token count and last refill time
 * </ul>
 *
 * <p><b>Thread Safety:</b> This implementation is thread-safe using locks and atomic operations.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>APIs that allow bursts (e.g., 100 req/sec with burst of 200)
 *   <li>Network traffic shaping
 *   <li>When smooth average rate is important but occasional bursts are acceptable
 * </ul>
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Allow 100 requests per second with burst capacity of 200
 * RateLimitStrategy limiter = new TokenBucketRateLimiter(
 *     100,                        // Tokens per second
 *     200,                        // Bucket capacity
 *     Duration.ofSeconds(1)       // Refill period
 * );
 *
 * // Wrap workflow
 * Workflow rateLimited = RateLimitedWorkflow.builder()
 *     .workflow(apiWorkflow)
 *     .rateLimitStrategy(limiter)
 *     .build();
 * }</pre>
 *
 * <p><b>Token Bucket Visualization:</b>
 *
 * <pre>
 * Bucket Capacity: 200 tokens
 * Refill Rate: 100 tokens/second
 *
 * [========] 200 tokens available → Can handle burst of 200 requests
 * [====    ] 100 tokens → Normal operation
 * [        ] 0 tokens → Must wait for refill
 *
 * Time progression:
 * T=0: [========] 200 tokens → Burst of 150 requests
 * T=0: [==      ] 50 tokens remaining
 * T=1: [======  ] 150 tokens (refilled 100)
 * </pre>
 *
 * @see RateLimitStrategy
 * @see LeakyBucketRateLimiter
 */
public class TokenBucketRateLimiter implements RateLimitStrategy {
  private final int capacity;
  private final Lock lock;
  private double tokens;
  private long lastRefillTimeNanos;
  private final double tokensPerNano;

  /**
   * Creates a token bucket rate limiter.
   *
   * @param tokensPerRefill number of tokens added per refill period
   * @param capacity maximum number of tokens the bucket can hold
   * @param refillPeriod how often tokens are added
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public TokenBucketRateLimiter(double tokensPerRefill, int capacity, Duration refillPeriod) {
    if (tokensPerRefill <= 0) {
      throw new IllegalArgumentException("tokensPerRefill must be positive");
    }
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be at least 1");
    }
    if (refillPeriod.isNegative() || refillPeriod.isZero()) {
      throw new IllegalArgumentException("refillPeriod must be positive");
    }
    this.capacity = capacity;
    this.tokensPerNano = tokensPerRefill / refillPeriod.toNanos();
    this.tokens = capacity; // Start with full bucket
    this.lastRefillTimeNanos = System.nanoTime();
    this.lock = new ReentrantLock();
  }

  /**
   * Creates a token bucket rate limiter with capacity equal to tokens per refill.
   *
   * @param tokensPerRefill number of tokens added per refill period (also used as capacity)
   * @param refillPeriod how often tokens are added
   */
  public TokenBucketRateLimiter(int tokensPerRefill, Duration refillPeriod) {
    this(tokensPerRefill, tokensPerRefill, refillPeriod);
  }

  @Override
  public void acquire() throws InterruptedException {
    while (!tryAcquire()) {
      long sleepNanos;
      lock.lock();
      try {
        refill();

        if (tokens >= 1.0) {
          // token became available between tryAcquire and now; loop to consume it
          continue;
        }

        // Calculate time for next token
        double tokensNeeded = 1.0 - tokens;
        sleepNanos = (long) Math.ceil(tokensNeeded / tokensPerNano);
      } finally {
        lock.unlock();
      }

      if (sleepNanos > 0) {
        sleepNanos(Math.min(sleepNanos, TimeUnit.MILLISECONDS.toNanos(10)));
      }
    }
  }

  private static void sleepNanos(long sleepTimeNanos) throws InterruptedException {
    TimeUnit.NANOSECONDS.sleep(sleepTimeNanos);
  }

  @Override
  public boolean tryAcquire() {
    lock.lock();
    try {
      refill();

      if (tokens >= 1.0) {
        tokens -= 1.0;
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
    long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

    while (System.nanoTime() < deadlineNanos) {
      if (tryAcquire()) {
        return true;
      }

      long now = System.nanoTime();
      long remainingNanos = deadlineNanos - now;
      if (remainingNanos <= 0) {
        return false;
        // deadline passed, do not attempt again
      }

      long sleepNanos = TimeUnit.MILLISECONDS.toNanos(1);
      lock.lock();
      try {
        refill();

        // Calculate time for next token
        if (tokens < 1.0) {
          double tokensNeeded = 1.0 - tokens;
          sleepNanos = (long) Math.ceil(tokensNeeded / tokensPerNano);
          sleepNanos = Math.min(sleepNanos, remainingNanos);
        }
      } finally {
        lock.unlock();
      }

      if (sleepNanos > 0) {
        sleepNanos(Math.min(sleepNanos, TimeUnit.MILLISECONDS.toNanos(10)));
      }
    }

    return tryAcquire();
  }

  @Override
  public int availablePermits() {
    lock.lock();
    try {
      refill();
      return (int) Math.floor(tokens);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void reset() {
    lock.lock();
    try {
      tokens = capacity;
      lastRefillTimeNanos = System.nanoTime();
    } finally {
      lock.unlock();
    }
  }

  /** Refills tokens based on elapsed time since last refill. */
  private void refill() {
    long now = System.nanoTime();
    long elapsedNanos = now - lastRefillTimeNanos;

    if (elapsedNanos > 0) {
      double tokensToAdd = elapsedNanos * tokensPerNano;
      tokens = Math.min(capacity, tokens + tokensToAdd);
      lastRefillTimeNanos = now;
    }
  }
}
