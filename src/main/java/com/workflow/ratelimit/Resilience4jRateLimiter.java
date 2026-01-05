package com.workflow.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

/**
 * Rate limiter implementation using Resilience4j library.
 *
 * <p>This implementation wraps Resilience4j's {@link RateLimiter} to provide integration with the
 * workflow engine. Resilience4j is a lightweight fault tolerance library designed for functional
 * programming and provides a robust rate limiting implementation.
 *
 * <p><b>Characteristics:</b>
 *
 * <ul>
 *   <li><b>Battle-tested:</b> Production-ready implementation from Resilience4j
 *   <li><b>Atomic Semaphore:</b> Uses AtomicRateLimiter.SemaphoreBasedRateLimiter internally
 *   <li><b>Thread-safe:</b> Fully thread-safe with minimal locking
 *   <li><b>Configurable:</b> Supports timeout period and limit refresh period
 * </ul>
 *
 * <p><b>How it works:</b>
 *
 * <p>Resilience4j rate limiter divides time into cycles and allows a specified number of
 * permissions per cycle. When permissions are exhausted, threads wait until the next cycle begins.
 *
 * <pre>
 * Cycle 1 (1s): [== 100 permits ==]
 *               Used: 100 → Wait for next cycle
 * Cycle 2 (1s): [== 100 permits ==]
 *               Fresh permits available
 * </pre>
 *
 * <p><b>Thread Safety:</b> This implementation is fully thread-safe using Resilience4j's internal
 * synchronization mechanisms.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Production environments requiring battle-tested rate limiting
 *   <li>Microservices architectures already using Resilience4j
 *   <li>Applications needing advanced rate limiting features
 *   <li>Integration with Resilience4j monitoring and metrics
 * </ul>
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Allow 100 requests per second
 * RateLimitStrategy limiter = new Resilience4jRateLimiter(100, Duration.ofSeconds(1));
 *
 * // Wrap workflow
 * Workflow rateLimited = RateLimitedWorkflow.builder()
 *     .workflow(apiWorkflow)
 *     .rateLimitStrategy(limiter)
 *     .build();
 *
 * // Execute with rate limiting
 * rateLimited.execute(context);
 * }</pre>
 *
 * <p><b>Advanced Configuration:</b>
 *
 * <pre>{@code
 * // Custom configuration with timeout
 * RateLimiterConfig config = RateLimiterConfig.custom()
 *     .limitForPeriod(100)
 *     .limitRefreshPeriod(Duration.ofSeconds(1))
 *     .timeoutDuration(Duration.ofSeconds(5))
 *     .build();
 *
 * RateLimitStrategy limiter = new Resilience4jRateLimiter("myLimiter", config);
 * }</pre>
 *
 * @see RateLimitStrategy
 * @see io.github.resilience4j.ratelimiter.RateLimiter
 * @see <a href="https://resilience4j.readme.io/docs/ratelimiter">Resilience4j Rate Limiter
 *     Documentation</a>
 */
@Getter
public class Resilience4jRateLimiter implements RateLimitStrategy {
  /**
   * The underlying Resilience4j rate limiter instance.
   *
   * <p>This allows access to additional Resilience4j features such as:
   *
   * <ul>
   *   <li>Event publishing and monitoring
   *   <li>Metrics and statistics
   *   <li>Dynamic configuration
   * </ul>
   */
  private final RateLimiter rateLimiter;

  /** The configured timeout duration. */
  private final Duration timeoutDuration;

  /**
   * Creates a Resilience4j rate limiter with the specified limits.
   *
   * @param limitForPeriod the maximum number of permits available during one limit refresh period
   * @param limitRefreshPeriod the period of a limit refresh. After each period the rate limiter
   *     sets its permissions count back to the limitForPeriod value
   * @throws IllegalArgumentException if limitForPeriod is less than 1 or limitRefreshPeriod is
   *     negative or zero
   */
  public Resilience4jRateLimiter(int limitForPeriod, Duration limitRefreshPeriod) {
    this(
        "resilience4j-rate-limiter-" + System.nanoTime(),
        limitForPeriod,
        limitRefreshPeriod,
        Duration.ofSeconds(5));
  }

  /**
   * Creates a Resilience4j rate limiter with the specified limits and timeout.
   *
   * @param limitForPeriod the maximum number of permits available during one limit refresh period
   * @param limitRefreshPeriod the period of a limit refresh
   * @param timeoutDuration the default wait time a thread waits for a permission
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public Resilience4jRateLimiter(
      int limitForPeriod, Duration limitRefreshPeriod, Duration timeoutDuration) {
    this(
        "resilience4j-rate-limiter-" + System.nanoTime(),
        limitForPeriod,
        limitRefreshPeriod,
        timeoutDuration);
  }

  /**
   * Creates a Resilience4j rate limiter with the specified name and limits.
   *
   * @param name the name of the rate limiter
   * @param limitForPeriod the maximum number of permits available during one limit refresh period
   * @param limitRefreshPeriod the period of a limit refresh
   * @param timeoutDuration the default wait time a thread waits for a permission
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public Resilience4jRateLimiter(
      String name, int limitForPeriod, Duration limitRefreshPeriod, Duration timeoutDuration) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name cannot be null or empty");
    }
    if (limitForPeriod < 1) {
      throw new IllegalArgumentException("limitForPeriod must be at least 1");
    }
    if (limitRefreshPeriod == null
        || limitRefreshPeriod.isNegative()
        || limitRefreshPeriod.isZero()) {
      throw new IllegalArgumentException("limitRefreshPeriod must be positive");
    }
    if (timeoutDuration == null || timeoutDuration.isNegative()) {
      throw new IllegalArgumentException("timeoutDuration must be non-negative");
    }

    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(limitForPeriod)
            .limitRefreshPeriod(limitRefreshPeriod)
            .timeoutDuration(timeoutDuration)
            .build();

    this.rateLimiter = RateLimiter.of(name, config);
    this.timeoutDuration = timeoutDuration;
  }

  /**
   * Creates a Resilience4j rate limiter with a custom configuration.
   *
   * @param name the name of the rate limiter
   * @param config the custom rate limiter configuration
   * @throws IllegalArgumentException if name is null or empty, or config is null
   */
  public Resilience4jRateLimiter(String name, RateLimiterConfig config) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name cannot be null or empty");
    }
    if (config == null) {
      throw new IllegalArgumentException("config cannot be null");
    }

    this.rateLimiter = RateLimiter.of(name, config);
    this.timeoutDuration = config.getTimeoutDuration();
  }

  /**
   * Creates a Resilience4j rate limiter wrapping an existing Resilience4j RateLimiter instance.
   *
   * @param rateLimiter the Resilience4j rate limiter to wrap
   * @throws IllegalArgumentException if rateLimiter is null
   */
  public Resilience4jRateLimiter(RateLimiter rateLimiter) {
    if (rateLimiter == null) {
      throw new IllegalArgumentException("rateLimiter cannot be null");
    }
    this.rateLimiter = rateLimiter;
    this.timeoutDuration = rateLimiter.getRateLimiterConfig().getTimeoutDuration();
  }

  @Override
  public void acquire() throws InterruptedException {
    long waitNanos = rateLimiter.reservePermission();
    if (waitNanos < 0) {
      throw new InterruptedException("Failed to acquire permission within timeout");
    }
    if (waitNanos > 0) {
      TimeUnit.NANOSECONDS.sleep(waitNanos);
    }
  }

  @Override
  public boolean tryAcquire() {
    long waitNanos = rateLimiter.reservePermission();
    return waitNanos == 0;
  }

  @Override
  public boolean tryAcquire(long timeoutMillis) throws InterruptedException {
    if (timeoutMillis < 0) {
      throw new IllegalArgumentException("timeoutMillis must be non-negative");
    }
    long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    long waitNanos = rateLimiter.reservePermission();
    if (waitNanos < 0) {
      return false;
    }
    if (waitNanos > timeoutNanos) {
      return false;
    }
    if (waitNanos > 0) {
      TimeUnit.NANOSECONDS.sleep(waitNanos);
    }
    return true;
  }

  @Override
  public int availablePermits() {
    return rateLimiter.getMetrics().getAvailablePermissions();
  }

  @Override
  public void reset() {
    // Resilience4j doesn't provide a direct reset method
    // The rate limiter will automatically refresh at the next cycle
  }

  /**
   * Gets the name of this rate limiter.
   *
   * @return the rate limiter name
   */
  public String getName() {
    return rateLimiter.getName();
  }

  /**
   * Gets the configured limit for the refresh period.
   *
   * @return the maximum number of permits available per refresh period
   */
  public int getLimitForPeriod() {
    return rateLimiter.getRateLimiterConfig().getLimitForPeriod();
  }

  /**
   * Gets the configured limit refresh period.
   *
   * @return the duration of each refresh period
   */
  public Duration getLimitRefreshPeriod() {
    return rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod();
  }

  /**
   * Gets the current number of waiting threads.
   *
   * @return the number of threads waiting for permission
   */
  public int getNumberOfWaitingThreads() {
    return rateLimiter.getMetrics().getNumberOfWaitingThreads();
  }
}
