package com.workflow.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.sleeper.ThreadSleepingSleeper;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

  @Test
  void testSlidingWindowBurstPrevention() throws InterruptedException {
    // Allow 5 requests per 500ms
    int maxRequests = 5;
    Duration window = Duration.ofMillis(500);
    SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(maxRequests, window);

    // 1. Fill the window
    for (int i = 0; i < maxRequests; i++) {
      assertTrue(limiter.tryAcquire(), "Permit " + i + " should be acquired");
    }

    // 2. Immediate next request should fail
    assertFalse(limiter.tryAcquire(), "Should fail as window is full");

    // 3. Wait 300ms. Still shouldn't have any permits because the
    // original 5 were all issued at T=0 and expire at T=500.
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(300));
    assertFalse(limiter.tryAcquire(), "Should still fail at 300ms");

    // 4. Wait another 250ms (Total 550ms). Should be able to acquire.
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(250));
    assertTrue(limiter.tryAcquire(), "Should succeed after window slides past first batch");
  }

  @Test
  void testAcquireBlocking() throws InterruptedException {
    // Rate: 1 per 200ms
    SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofMillis(200));

    limiter.tryAcquire(); // Take the only permit

    long start = System.nanoTime();
    limiter.acquire(); // This should block for ~200ms
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertTrue(
        elapsedMillis >= 200, "Should have blocked for at least 200ms, took: " + elapsedMillis);
  }

  @Test
  void testTryAcquireWithTimeout() throws InterruptedException {
    // Rate: 1 per 500ms
    SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofMillis(500));
    limiter.tryAcquire(); // Fill it

    // Try to acquire with a 100ms timeout. Should fail.
    long start = System.currentTimeMillis();
    limiter.tryAcquire(100);
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed >= 100, "Should have waited for the timeout period");

    // Try with 600ms timeout. Should succeed.
    assertTrue(limiter.tryAcquire(600), "Should succeed as 600ms > 500ms window");
  }

  @Test
  void testAvailablePermits() {
    SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(10, Duration.ofMinutes(1));

    assertEquals(10, limiter.availablePermits());
    limiter.tryAcquire();
    limiter.tryAcquire();
    assertEquals(8, limiter.availablePermits());
  }

  @Test
  void testConcurrentAccess() throws InterruptedException {
    int threads = 20;
    int maxRequests = 50;
    // Large window so no sliding happens during the test
    SlidingWindowRateLimiter limiter =
        new SlidingWindowRateLimiter(maxRequests, Duration.ofHours(1));

    AtomicInteger successfulAcquires = new AtomicInteger(0);
    try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
      CountDownLatch latch = new CountDownLatch(threads);

      for (int i = 0; i < threads; i++) {
        executor.submit(
            () -> {
              try {
                for (int j = 0; j < 10; j++) {
                  if (limiter.tryAcquire()) {
                    successfulAcquires.incrementAndGet();
                  }
                }
              } finally {
                latch.countDown();
              }
            });
      }

      boolean await = latch.await(5, TimeUnit.SECONDS);
      assertTrue(await);
      executor.shutdown();
    }

    assertEquals(
        maxRequests,
        successfulAcquires.get(),
        "Should not exceed maxRequests under high concurrency");
  }

  @Test
  void testReset() {
    SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, Duration.ofMinutes(1));
    for (int i = 0; i < 5; i++) limiter.tryAcquire();

    assertFalse(limiter.tryAcquire());
    limiter.reset();
    assertTrue(limiter.tryAcquire(), "Should succeed immediately after reset");
  }
}
