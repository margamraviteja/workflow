package com.workflow.ratelimit;

/**
 * Strategy interface for rate limiting workflow executions.
 *
 * <p>Rate limiting controls how frequently a workflow can be executed within a time window. This is
 * useful for:
 *
 * <ul>
 *   <li>Throttling API calls to respect rate limits
 *   <li>Controlling resource consumption
 *   <li>Preventing system overload
 *   <li>Implementing fair usage policies
 * </ul>
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe as they may be accessed concurrently
 * by multiple workflows.
 *
 * <p><b>Common Implementations:</b>
 *
 * <ul>
 *   <li>{@link FixedWindowRateLimiter} - Fixed time window with request count limit
 *   <li>{@link SlidingWindowRateLimiter} - Sliding time window for smoother rate limiting
 *   <li>{@link TokenBucketRateLimiter} - Token bucket algorithm with burst support
 *   <li>{@link LeakyBucketRateLimiter} - Leaky bucket algorithm for steady rate
 *   <li>{@link Resilience4jRateLimiter} - Production-ready implementation using Resilience4j
 *       library
 * </ul>
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Create rate limiter: 100 requests per minute
 * RateLimitStrategy rateLimiter = new FixedWindowRateLimiter(100, Duration.ofMinutes(1));
 *
 * // Wrap workflow with rate limiting
 * Workflow rateLimited = RateLimitedWorkflow.builder()
 *     .workflow(apiCallWorkflow)
 *     .rateLimitStrategy(rateLimiter)
 *     .build();
 *
 * // Executions are automatically rate limited
 * for (int i = 0; i < 1000; i++) {
 *     rateLimited.execute(context); // Blocks when limit reached
 * }
 * }</pre>
 *
 * @see com.workflow.RateLimitedWorkflow
 * @see FixedWindowRateLimiter
 * @see SlidingWindowRateLimiter
 * @see TokenBucketRateLimiter
 * @see LeakyBucketRateLimiter
 */
public interface RateLimitStrategy {
  /**
   * Attempts to acquire permission to proceed. Blocks until permission is granted.
   *
   * <p>This method should block the calling thread until the rate limit allows execution to
   * proceed. Implementations should be thread-safe and handle concurrent access correctly.
   *
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  void acquire() throws InterruptedException;

  /**
   * Attempts to acquire permission to proceed without blocking.
   *
   * @return true if permission was immediately granted, false if rate limit would be exceeded
   */
  boolean tryAcquire();

  /**
   * Attempts to acquire permission to proceed, waiting up to the specified timeout.
   *
   * @param timeoutMillis maximum time to wait in milliseconds
   * @return true if permission was granted within the timeout, false otherwise
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  boolean tryAcquire(long timeoutMillis) throws InterruptedException;

  /**
   * Returns the current number of available permits. This is a snapshot and may change immediately
   * after the call.
   *
   * @return the number of permits currently available, or -1 if not supported by implementation
   */
  default int availablePermits() {
    return -1; // Not all implementations can provide this
  }

  /**
   * Resets the rate limiter state. This is useful for testing or when starting a new rate limit
   * period.
   */
  void reset();
}
