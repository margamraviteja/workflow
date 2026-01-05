package com.workflow.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.sleeper.ThreadSleepingSleeper;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

  @Test
  @DisplayName("Should throw exception for invalid constructor arguments")
  void testConstructorInvalidArgs() {
    Duration duration = Duration.ofSeconds(1);
    assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0, 10, duration));
    assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(10, 0, duration));
    assertThrows(
        IllegalArgumentException.class, () -> new TokenBucketRateLimiter(10, 10, Duration.ZERO));
  }

  @Test
  @DisplayName("Should start with full capacity")
  void testInitialState() {
    int capacity = 5;
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, capacity, Duration.ofSeconds(1));
    assertEquals(capacity, limiter.availablePermits());
  }

  @Test
  @DisplayName("tryAcquire should consume tokens and return false when empty")
  void testTryAcquire() {
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 2, Duration.ofHours(1));

    assertTrue(limiter.tryAcquire(), "First acquisition should succeed");
    assertTrue(limiter.tryAcquire(), "Second acquisition should succeed");
    assertFalse(limiter.tryAcquire(), "Third acquisition should fail (empty bucket)");
    assertEquals(0, limiter.availablePermits());
  }

  @Test
  @DisplayName("Refill should add tokens over time")
  void testRefillLogic() throws InterruptedException {
    // 10 tokens per second, capacity 10
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10, Duration.ofSeconds(1));

    // Empty the bucket
    for (int i = 0; i < 10; i++) limiter.tryAcquire();
    assertEquals(0, limiter.availablePermits());

    // Wait 500ms -> should refill ~5 tokens
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(550));

    int available = limiter.availablePermits();
    assertTrue(available >= 5, "Should have refilled at least 5 tokens, found: " + available);
  }

  @Test
  @DisplayName("reset() should restore bucket to full capacity")
  void testReset() {
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10, Duration.ofHours(1));

    for (int i = 0; i < 5; i++) limiter.tryAcquire();
    assertEquals(5, limiter.availablePermits());

    limiter.reset();
    assertEquals(10, limiter.availablePermits());
  }

  @Test
  @DisplayName("tryAcquire with timeout should wait for tokens")
  void testTryAcquireWithTimeout() throws InterruptedException {
    // Very slow refill: 1 token per 500ms
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, Duration.ofMillis(500));

    // Empty bucket
    limiter.tryAcquire();

    long start = System.currentTimeMillis();
    // Try to acquire with a 1-second timeout. Should succeed after ~500ms
    boolean success = limiter.tryAcquire(1000);
    long duration = System.currentTimeMillis() - start;

    assertTrue(success, "Should have acquired token within timeout");
    assertTrue(duration >= 450, "Should have waited for refill");
  }

  @Test
  @DisplayName("tryAcquire with timeout should return false if timeout expires")
  void testTryAcquireTimeoutFailure() throws InterruptedException {
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, Duration.ofSeconds(10));
    limiter.tryAcquire(); // Empty bucket

    boolean success = limiter.tryAcquire(100); // Only wait 100ms for a 10s refill
    assertFalse(success, "Should fail as refill takes longer than timeout");
  }

  @Test
  @DisplayName("acquire() should block until token is available")
  void testAcquireBlocking() throws InterruptedException {
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 1, Duration.ofMillis(200));
    limiter.tryAcquire(); // Empty

    Thread thread =
        new Thread(
            () -> {
              try {
                limiter.acquire();
              } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
              }
            });

    thread.start();
    thread.join(1000); // Wait for thread to finish

    assertFalse(thread.isAlive(), "Thread should have completed after acquiring token");
  }
}
