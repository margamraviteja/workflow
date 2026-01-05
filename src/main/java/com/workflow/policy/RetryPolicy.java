package com.workflow.policy;

import java.security.SecureRandom;

/**
 * Strategy interface that defines retry behavior for tasks executed by the workflow engine.
 * Implementations decide if a failed attempt should be retried and expose a {@link BackoffStrategy}
 * to control the delay between retries.
 *
 * <p>This interface provides factory methods for common retry patterns including:
 *
 * <ul>
 *   <li>Limited retries with no backoff
 *   <li>Limited retries with custom backoff strategies
 *   <li>Selective retries based on exception types
 *   <li>Fixed backoff (constant delay)
 *   <li>Exponential backoff (increasing delay)
 * </ul>
 *
 * <p>Typical implementations include fixed retry counts, retryable exception types, and various
 * backoff strategies (constant, linear, exponential, jittered).
 *
 * <p><b>Usage Examples:</b>
 *
 * <pre>{@code
 * // Basic retry - up to 3 attempts with no delay
 * RetryPolicy simple = RetryPolicy.limitedRetries(3);
 *
 * // Exponential backoff - delays grow: 100ms, 200ms, 400ms, 800ms...
 * RetryPolicy exponential = RetryPolicy.exponentialBackoff(5, 100);
 *
 * // Fixed backoff - constant 500ms delay between attempts
 * RetryPolicy fixed = RetryPolicy.fixedBackoff(3, 500);
 *
 * // Retry only specific exceptions
 * RetryPolicy selective = RetryPolicy.limitedRetries(
 *     3,
 *     IOException.class,
 *     TimeoutException.class
 * );
 *
 * // Custom backoff with jitter to prevent thundering herd
 * RetryPolicy jittered = RetryPolicy.limitedRetriesWithBackoff(
 *     5,
 *     BackoffStrategy.exponentialWithJitter(100, 10000)
 * );
 * }</pre>
 *
 * @see RetryPolicy.BackoffStrategy
 * @see com.workflow.task.RetryingTask
 * @see com.workflow.task.TaskDescriptor
 */
public interface RetryPolicy {
  /**
   * Determine whether a retry should be attempted for the given failure.
   *
   * @param attempt 1-based attempt number (the first attempt is 1)
   * @param error the exception thrown during the attempt
   * @return true if the orchestrator should attempt a retry, false otherwise
   */
  boolean shouldRetry(int attempt, Exception error);

  /**
   * Returns the configured backoff strategy used to compute delay between retries.
   *
   * @return a non-null {@link BackoffStrategy}
   */
  BackoffStrategy backoff();

  /** A convenience no-retry policy constant. */
  RetryPolicy NONE =
      new RetryPolicy() {
        @Override
        public boolean shouldRetry(int attempt, Exception error) {
          return false;
        }

        @Override
        public BackoffStrategy backoff() {
          return BackoffStrategy.NO_BACKOFF;
        }
      };

  /**
   * Retry a fixed number of times regardless of the error type and with no backoff strategy.
   *
   * @param maxAttempts maximum number of attempts (inclusive)
   * @return a {@link RetryPolicy} that retries up to {@code maxAttempts}
   */
  static RetryPolicy limitedRetries(final int maxAttempts) {
    return limitedRetriesWithBackoff(maxAttempts, BackoffStrategy.NO_BACKOFF);
  }

  /**
   * Retry a fixed number of times regardless of the error type and with backoff strategy.
   *
   * @param maxAttempts maximum number of attempts (inclusive)
   * @param backoffStrategy the backoff strategy to use between retries
   * @return a {@link RetryPolicy} that retries up to {@code maxAttempts}
   */
  static RetryPolicy limitedRetriesWithBackoff(
      final int maxAttempts, final BackoffStrategy backoffStrategy) {
    return new RetryPolicy() {
      @Override
      public boolean shouldRetry(int attempt, Exception error) {
        return attempt <= maxAttempts;
      }

      @Override
      public BackoffStrategy backoff() {
        return backoffStrategy;
      }
    };
  }

  /**
   * Retry until a maximum attempt count, but only for specified retryable exception types and with
   * no backoff strategy.
   *
   * @param maxAttempts maximum number of attempts (inclusive)
   * @param retryableExceptions exception classes that are considered retryable
   * @return a {@link RetryPolicy} that retries only when the thrown exception is an instance of one
   *     of {@code retryableExceptions}
   */
  @SafeVarargs
  static RetryPolicy limitedRetries(
      final int maxAttempts, final Class<? extends Exception>... retryableExceptions) {
    return limitedRetriesWithBackoff(maxAttempts, BackoffStrategy.NO_BACKOFF, retryableExceptions);
  }

  /**
   * Retry until a maximum attempt count, but only for specified retryable exception types and with
   * backoff strategy.
   *
   * @param maxAttempts maximum number of attempts (inclusive)
   * @param backoffStrategy the backoff strategy to use between retries
   * @param retryableExceptions exception classes that are considered retryable
   * @return a {@link RetryPolicy} that retries only when the thrown exception is an instance of one
   *     of {@code retryableExceptions}
   */
  @SafeVarargs
  static RetryPolicy limitedRetriesWithBackoff(
      final int maxAttempts,
      final BackoffStrategy backoffStrategy,
      final Class<? extends Exception>... retryableExceptions) {
    return new RetryPolicy() {
      @Override
      public boolean shouldRetry(int attempt, Exception error) {
        if (attempt > maxAttempts) {
          return false;
        }
        for (Class<? extends Exception> clazz : retryableExceptions) {
          if (clazz.isInstance(error)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public BackoffStrategy backoff() {
        return backoffStrategy;
      }
    };
  }

  /**
   * Retry with fixed backoff strategy. Retries up to maxAttempts times with a constant delay
   * between attempts.
   *
   * @param maxAttempts maximum number of attempts (inclusive)
   * @param backoffMillis constant delay in milliseconds between retries
   * @return a {@link RetryPolicy} with fixed backoff
   */
  static RetryPolicy fixedBackoff(final int maxAttempts, final long backoffMillis) {
    return new RetryPolicy() {
      @Override
      public boolean shouldRetry(int attempt, Exception error) {
        return attempt < maxAttempts;
      }

      @Override
      public BackoffStrategy backoff() {
        return BackoffStrategy.constant(backoffMillis);
      }
    };
  }

  /**
   * Retry with exponential backoff strategy. Retries up to maxAttempts times with exponentially
   * increasing delays between attempts.
   *
   * @param maxAttempts maximum number of attempts (inclusive)
   * @param baseBackoffMillis base delay in milliseconds (doubled with each attempt)
   * @return a {@link RetryPolicy} with exponential backoff
   */
  static RetryPolicy exponentialBackoff(final int maxAttempts, final long baseBackoffMillis) {
    return new RetryPolicy() {
      @Override
      public boolean shouldRetry(int attempt, Exception error) {
        return attempt < maxAttempts;
      }

      @Override
      public BackoffStrategy backoff() {
        return BackoffStrategy.exponential(baseBackoffMillis);
      }
    };
  }

  /**
   * Computes the delay (in milliseconds) to wait before performing a retry. Typical implementations
   * include constant, linear, exponential and jittered strategies.
   *
   * <p>Used by {@link RetryPolicy} to determine wait time between retry attempts.
   *
   * <p>Example implementations:
   *
   * <pre>{@code
   * BackoffStrategy constantBackoff = BackoffStrategy.constant(500); // 500 ms delay
   * BackoffStrategy linearBackoff = BackoffStrategy.linear(200); // 200 ms * attempt number
   * BackoffStrategy expBackoff = BackoffStrategy.exponential(100); // exponential growth
   * BackoffStrategy jitteredBackoff = BackoffStrategy.exponentialWithJitter(100, 10000); // with jitter
   * }</pre>
   *
   * @see RetryPolicy
   */
  interface BackoffStrategy {
    /**
     * Compute the delay in milliseconds for the given retry attempt.
     *
     * @param attempt 1-based retry attempt number
     * @return delay in milliseconds before the next retry
     */
    long computeDelayMs(int attempt);

    /** A backoff strategy that always returns zero (no delay). */
    BackoffStrategy NO_BACKOFF = _ -> 0;

    /**
     * Create a constant backoff strategy.
     *
     * @param delayMs delay in milliseconds between retries
     * @return a {@link BackoffStrategy} returning a constant delay
     */
    static BackoffStrategy constant(long delayMs) {
      return _ -> delayMs;
    }

    /**
     * Create a linear backoff strategy.
     *
     * @param baseDelayMs base delay in milliseconds (multiplied by attempt number)
     * @return a {@link BackoffStrategy} that grows linearly with attempts
     */
    static BackoffStrategy linear(long baseDelayMs) {
      return attempt -> baseDelayMs * attempt;
    }

    /**
     * Create an exponential backoff strategy.
     *
     * @param baseDelayMs base delay in milliseconds for attempt 1
     * @return a {@link BackoffStrategy} that grows exponentially with attempts
     */
    static BackoffStrategy exponential(long baseDelayMs) {
      return attempt -> (long) (baseDelayMs * Math.pow(2, attempt - 1.0));
    }

    /**
     * Create an exponential-with-jitter backoff strategy that adds random jitter to the base
     * exponential delay.
     *
     * @param baseDelayMs base delay in milliseconds
     * @param maxDelayMs maximum delay allowed in milliseconds
     * @return a {@link BackoffStrategy} with jitter
     */
    static BackoffStrategy exponentialWithJitter(long baseDelayMs, long maxDelayMs) {
      return exponentialWithJitter(baseDelayMs, maxDelayMs, 0.2);
    }

    /**
     * Create an exponential-with-jitter backoff strategy with explicit jitter factor.
     *
     * @param baseDelayMs base delay in milliseconds
     * @param maxDelayMs maximum delay allowed
     * @param jitterFactor fraction (0-1) applied to baseDelayMs for jitter
     * @return a {@link BackoffStrategy} with jitter
     */
    static BackoffStrategy exponentialWithJitter(
        long baseDelayMs, long maxDelayMs, double jitterFactor) {
      return attempt -> {
        SecureRandom random = new SecureRandom();
        long expDelay = (long) (baseDelayMs * Math.pow(2, attempt - 1.0));
        long jitter = (long) (baseDelayMs * jitterFactor * random.nextDouble());
        jitter *= random.nextBoolean() ? 1 : -1;
        return Math.min(expDelay + jitter, maxDelayMs);
      };
    }
  }
}
