package com.workflow.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class PolicyTest {

  @Test
  void timeoutPolicy_ofMillis_createsPolicy() {
    TimeoutPolicy policy = TimeoutPolicy.ofMillis(5000);

    assertNotNull(policy);
    assertEquals(5000, policy.timeoutMs());
  }

  @Test
  void timeoutPolicy_none_returnsZero() {
    assertEquals(0, TimeoutPolicy.NONE.timeoutMs());
  }

  @Test
  void timeoutPolicy_withZero_isValid() {
    TimeoutPolicy policy = TimeoutPolicy.ofMillis(0);
    assertEquals(0, policy.timeoutMs());
  }

  @Test
  void timeoutPolicy_withNegative_isValid() {
    TimeoutPolicy policy = TimeoutPolicy.ofMillis(-1);
    assertEquals(-1, policy.timeoutMs());
  }

  @Test
  void retryPolicy_limitedRetries_createsPolicy() {
    RetryPolicy policy = RetryPolicy.limitedRetries(3);

    assertNotNull(policy);
    assertTrue(policy.shouldRetry(1, new RuntimeException()));
    assertTrue(policy.shouldRetry(2, new RuntimeException()));
    assertTrue(policy.shouldRetry(3, new RuntimeException()));
    assertFalse(policy.shouldRetry(4, new RuntimeException()));
  }

  @Test
  void retryPolicy_none_neverRetries() {
    assertFalse(RetryPolicy.NONE.shouldRetry(1, new RuntimeException()));
    assertFalse(RetryPolicy.NONE.shouldRetry(2, new RuntimeException()));
  }

  @Test
  void retryPolicy_exponentialBackoff_createsPolicy() {
    RetryPolicy policy = RetryPolicy.exponentialBackoff(5, 100);

    assertNotNull(policy);
    assertTrue(policy.shouldRetry(1, new RuntimeException()));
    assertTrue(policy.shouldRetry(4, new RuntimeException()));
    assertFalse(policy.shouldRetry(5, new RuntimeException()));
  }

  @Test
  void retryPolicy_fixedBackoff_createsPolicy() {
    RetryPolicy policy = RetryPolicy.fixedBackoff(3, 500);

    assertNotNull(policy);
    assertTrue(policy.shouldRetry(1, new RuntimeException()));
    assertTrue(policy.shouldRetry(2, new RuntimeException()));
    assertFalse(policy.shouldRetry(3, new RuntimeException()));
  }

  @Test
  void retryPolicy_limitedRetriesWithExceptions_createsPolicy() {
    RetryPolicy policy = RetryPolicy.limitedRetries(3, IOException.class, TimeoutException.class);

    assertNotNull(policy);
  }

  @Test
  void retryPolicy_limitedRetriesWithExceptions_retriesOnMatch() {
    RetryPolicy policy = RetryPolicy.limitedRetries(3, IOException.class);

    assertTrue(policy.shouldRetry(1, new IOException("Test")));
    assertTrue(policy.shouldRetry(2, new IOException("Test")));
  }

  @Test
  void retryPolicy_limitedRetriesWithExceptions_doesNotRetryOnMismatch() {
    RetryPolicy policy = RetryPolicy.limitedRetries(3, IOException.class);

    assertFalse(policy.shouldRetry(1, new IllegalArgumentException("Test")));
  }

  @Test
  void retryPolicy_limitedRetriesWithExceptions_respectsMaxAttempts() {
    RetryPolicy policy = RetryPolicy.limitedRetries(2, IOException.class);

    assertTrue(policy.shouldRetry(1, new IOException()));
    assertTrue(policy.shouldRetry(2, new IOException()));
    assertFalse(policy.shouldRetry(3, new IOException()));
  }

  @Test
  void backoffStrategy_constant_returnsSameDelay() {
    RetryPolicy.BackoffStrategy strategy = RetryPolicy.BackoffStrategy.constant(500);

    assertEquals(500, strategy.computeDelayMs(1));
    assertEquals(500, strategy.computeDelayMs(2));
    assertEquals(500, strategy.computeDelayMs(10));
  }

  @Test
  void backoffStrategy_linear_increasesLinearly() {
    RetryPolicy.BackoffStrategy strategy = RetryPolicy.BackoffStrategy.linear(100);

    assertEquals(100, strategy.computeDelayMs(1));
    assertEquals(200, strategy.computeDelayMs(2));
    assertEquals(300, strategy.computeDelayMs(3));
    assertEquals(1000, strategy.computeDelayMs(10));
  }

  @Test
  void backoffStrategy_exponential_growsExponentially() {
    RetryPolicy.BackoffStrategy strategy = RetryPolicy.BackoffStrategy.exponential(100);

    assertEquals(100, strategy.computeDelayMs(1)); // 100 * 2^0
    assertEquals(200, strategy.computeDelayMs(2)); // 100 * 2^1
    assertEquals(400, strategy.computeDelayMs(3)); // 100 * 2^2
    assertEquals(800, strategy.computeDelayMs(4)); // 100 * 2^3
  }

  @Test
  void backoffStrategy_exponentialWithJitter_addsRandomness() {
    RetryPolicy.BackoffStrategy strategy =
        RetryPolicy.BackoffStrategy.exponentialWithJitter(100, 10000);

    long delay1 = strategy.computeDelayMs(1);
    long delay2 = strategy.computeDelayMs(2);
    long delay3 = strategy.computeDelayMs(3);

    // Base exponential values: 100, 200, 400
    // With jitter, should be within reasonable range
    assertTrue(delay1 >= 80 && delay1 <= 120, "Delay1 out of range: " + delay1);
    assertTrue(delay2 >= 180 && delay2 <= 220, "Delay2 out of range: " + delay2);
    assertTrue(delay3 >= 380 && delay3 <= 420, "Delay3 out of range: " + delay3);

    // Should respect max delay
    long largeDelay = strategy.computeDelayMs(20);
    assertTrue(largeDelay <= 10000);
  }

  @Test
  void backoffStrategy_exponentialWithJitter_customFactor() {
    RetryPolicy.BackoffStrategy strategy =
        RetryPolicy.BackoffStrategy.exponentialWithJitter(100, 10000, 0.5);

    long delay = strategy.computeDelayMs(1);

    // With 0.5 factor, jitter can be +/- 50ms from base 100ms
    assertTrue(delay >= 50 && delay <= 150, "Delay out of range: " + delay);
  }

  @Test
  void backoffStrategy_noBackoff_returnsZero() {
    assertEquals(0, RetryPolicy.BackoffStrategy.NO_BACKOFF.computeDelayMs(1));
    assertEquals(0, RetryPolicy.BackoffStrategy.NO_BACKOFF.computeDelayMs(10));
  }

  @Test
  void retryPolicy_limitedRetriesWithBackoff_combinesConfiguration() {
    RetryPolicy.BackoffStrategy backoff = RetryPolicy.BackoffStrategy.constant(200);
    RetryPolicy policy = RetryPolicy.limitedRetriesWithBackoff(3, backoff);

    assertTrue(policy.shouldRetry(1, new RuntimeException()));
    assertTrue(policy.shouldRetry(3, new RuntimeException()));
    assertFalse(policy.shouldRetry(4, new RuntimeException()));

    assertEquals(200, policy.backoff().computeDelayMs(1));
  }

  @Test
  void retryPolicy_limitedRetriesWithBackoff_andExceptions() {
    RetryPolicy.BackoffStrategy backoff = RetryPolicy.BackoffStrategy.exponential(100);
    RetryPolicy policy = RetryPolicy.limitedRetriesWithBackoff(3, backoff, IOException.class);

    assertTrue(policy.shouldRetry(1, new IOException()));
    assertFalse(policy.shouldRetry(1, new RuntimeException()));
    assertFalse(policy.shouldRetry(4, new IOException()));
  }

  @Test
  void retryPolicy_backoff_returnsConfiguredStrategy() {
    RetryPolicy.BackoffStrategy expectedStrategy = RetryPolicy.BackoffStrategy.linear(150);
    RetryPolicy policy = RetryPolicy.limitedRetriesWithBackoff(5, expectedStrategy);

    assertEquals(150, policy.backoff().computeDelayMs(1));
    assertEquals(300, policy.backoff().computeDelayMs(2));
  }

  // Additional TimeoutPolicy tests for complete coverage

  @Test
  void timeoutPolicy_ofSeconds_createsPolicy() {
    TimeoutPolicy policy = TimeoutPolicy.ofSeconds(5);

    assertNotNull(policy);
    assertEquals(5000, policy.timeoutMs());
  }

  @Test
  void timeoutPolicy_ofSeconds_withZero_throwsException() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> TimeoutPolicy.ofSeconds(0));

    assertTrue(exception.getMessage().contains("must be positive"));
    assertTrue(exception.getMessage().contains("0"));
  }

  @Test
  void timeoutPolicy_ofSeconds_withNegative_throwsException() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> TimeoutPolicy.ofSeconds(-5));

    assertTrue(exception.getMessage().contains("must be positive"));
    assertTrue(exception.getMessage().contains("-5"));
  }

  @Test
  void timeoutPolicy_ofMinutes_createsPolicy() {
    TimeoutPolicy policy = TimeoutPolicy.ofMinutes(2);

    assertNotNull(policy);
    assertEquals(120000, policy.timeoutMs()); // 2 minutes = 120,000 ms
  }

  @Test
  void timeoutPolicy_ofMinutes_withZero_throwsException() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> TimeoutPolicy.ofMinutes(0));

    assertTrue(exception.getMessage().contains("must be positive"));
    assertTrue(exception.getMessage().contains("0"));
  }

  @Test
  void timeoutPolicy_ofMinutes_withNegative_throwsException() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> TimeoutPolicy.ofMinutes(-1));

    assertTrue(exception.getMessage().contains("must be positive"));
    assertTrue(exception.getMessage().contains("-1"));
  }

  @Test
  void timeoutPolicy_getDuration_returnsCorrectDuration() {
    TimeoutPolicy policy = TimeoutPolicy.ofMillis(5000);

    assertEquals(5000, policy.getDuration().toMillis());
  }

  @Test
  void timeoutPolicy_getDuration_withZero_returnsZeroDuration() {
    TimeoutPolicy policy = TimeoutPolicy.NONE;

    assertEquals(0, policy.getDuration().toMillis());
    assertTrue(policy.getDuration().isZero());
  }

  @Test
  void timeoutPolicy_getDuration_withNegative_returnsNegativeDuration() {
    TimeoutPolicy policy = TimeoutPolicy.ofMillis(-1000);

    assertEquals(-1000, policy.getDuration().toMillis());
    assertTrue(policy.getDuration().isNegative());
  }

  @Test
  void timeoutPolicy_getDuration_withSeconds_returnsCorrectDuration() {
    TimeoutPolicy policy = TimeoutPolicy.ofSeconds(10);

    assertEquals(10000, policy.getDuration().toMillis());
    assertEquals(10, policy.getDuration().getSeconds());
  }

  @Test
  void timeoutPolicy_getDuration_withMinutes_returnsCorrectDuration() {
    TimeoutPolicy policy = TimeoutPolicy.ofMinutes(3);

    assertEquals(180000, policy.getDuration().toMillis());
    assertEquals(180, policy.getDuration().getSeconds());
  }

  @Test
  void timeoutPolicy_ofMillis_withLargeValue_createsPolicy() {
    TimeoutPolicy policy = TimeoutPolicy.ofMillis(Long.MAX_VALUE);

    assertEquals(Long.MAX_VALUE, policy.timeoutMs());
  }

  @Test
  void timeoutPolicy_ofSeconds_convertsCorrectly() {
    TimeoutPolicy policy1 = TimeoutPolicy.ofSeconds(1);
    TimeoutPolicy policy60 = TimeoutPolicy.ofSeconds(60);

    assertEquals(1000, policy1.timeoutMs());
    assertEquals(60000, policy60.timeoutMs());
  }

  @Test
  void timeoutPolicy_ofMinutes_convertsCorrectly() {
    TimeoutPolicy policy1 = TimeoutPolicy.ofMinutes(1);
    TimeoutPolicy policy10 = TimeoutPolicy.ofMinutes(10);

    assertEquals(60000, policy1.timeoutMs());
    assertEquals(600000, policy10.timeoutMs());
  }
}
