package com.workflow.ratelimit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

  @Test
  void testBasicRateLimiting() {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(5, Duration.ofSeconds(1));

    // Should allow 5 requests
    for (int i = 0; i < 5; i++) {
      assertTrue(limiter.tryAcquire(), "Request " + i + " should be allowed");
    }

    // 6th request should be denied
    assertFalse(limiter.tryAcquire(), "6th request should be denied");
  }

  @Test
  void testWindowReset() {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(5, Duration.ofMillis(100));

    // Use up all permits
    for (int i = 0; i < 5; i++) {
      assertTrue(limiter.tryAcquire());
    }
    assertFalse(limiter.tryAcquire());

    // Wait for window to reset
    await().atMost(150, TimeUnit.MILLISECONDS).until(() -> true);

    // Should allow requests again
    assertTrue(limiter.tryAcquire(), "Request should be allowed after window reset");
  }

  @Test
  void testAvailablePermits() {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(10, Duration.ofSeconds(1));

    assertEquals(10, limiter.availablePermits());

    limiter.tryAcquire();
    assertEquals(9, limiter.availablePermits());

    limiter.tryAcquire();
    limiter.tryAcquire();
    assertEquals(7, limiter.availablePermits());
  }

  @Test
  void testReset() {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(5, Duration.ofSeconds(1));

    // Use up permits
    for (int i = 0; i < 5; i++) {
      limiter.tryAcquire();
    }
    assertFalse(limiter.tryAcquire());

    // Reset
    limiter.reset();

    // Should allow requests again
    assertTrue(limiter.tryAcquire());
  }

  @Test
  void testAcquireBlocking() throws InterruptedException {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(2, Duration.ofMillis(200));

    // Use up permits
    assertTrue(limiter.tryAcquire());
    assertTrue(limiter.tryAcquire());

    long start = System.currentTimeMillis();

    // This should block until window resets
    limiter.acquire();

    long elapsed = System.currentTimeMillis() - start;

    // Should have waited approximately 200ms
    assertTrue(elapsed >= 150, "Should have waited for window reset, waited: " + elapsed + "ms");
  }

  @Test
  void testTryAcquireWithTimeout() throws InterruptedException {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(2, Duration.ofMillis(200));

    // Use up permits
    assertTrue(limiter.tryAcquire());
    assertTrue(limiter.tryAcquire());

    // Try with short timeout - should fail
    assertFalse(limiter.tryAcquire(50));

    // Try with longer timeout - should succeed
    assertTrue(limiter.tryAcquire(300));
  }

  @Test
  void testConcurrentAccess() throws InterruptedException {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(100, Duration.ofSeconds(1));
    int threadCount = 10;
    int requestsPerThread = 20;

    AtomicInteger successCount = new AtomicInteger(0);
    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
      CountDownLatch latch = new CountDownLatch(threadCount);

      for (int i = 0; i < threadCount; i++) {
        executor.submit(
            () -> {
              for (int j = 0; j < requestsPerThread; j++) {
                if (limiter.tryAcquire()) {
                  successCount.incrementAndGet();
                }
              }
              latch.countDown();
            });
      }

      assertTrue(latch.await(5, TimeUnit.SECONDS));
      executor.shutdown();
    }

    // Should have allowed exactly 100 requests
    assertEquals(100, successCount.get(), "Should allow exactly maxRequests");
  }

  @Test
  void testInvalidParameters() {
    Duration oneSecond = Duration.ofSeconds(1);

    assertThrows(
        IllegalArgumentException.class,
        () -> new FixedWindowRateLimiter(0, oneSecond),
        "Should reject maxRequests < 1");

    assertThrows(
        IllegalArgumentException.class,
        () -> new FixedWindowRateLimiter(10, Duration.ZERO),
        "Should reject zero duration");

    Duration negativeDuration = Duration.ofSeconds(-1);
    assertThrows(
        IllegalArgumentException.class,
        () -> new FixedWindowRateLimiter(10, negativeDuration),
        "Should reject negative duration");
  }

  @Test
  void testHighThroughput() throws InterruptedException {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(1000, Duration.ofMillis(100));

    long start = System.currentTimeMillis();

    // Try to get 2000 permits (should take ~200ms)
    for (int i = 0; i < 2000; i++) {
      limiter.acquire();
    }

    long elapsed = System.currentTimeMillis() - start;

    // Should take at least 100ms (2 windows)
    assertTrue(elapsed >= 100, "Should respect rate limit, took: " + elapsed + "ms");
  }
}
