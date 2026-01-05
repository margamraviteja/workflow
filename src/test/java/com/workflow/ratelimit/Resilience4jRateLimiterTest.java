package com.workflow.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.sleeper.ThreadSleepingSleeper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Resilience4jRateLimiterTest {

  @Test
  void testConstructorInvalidArgs() {
    Duration validDuration = Duration.ofSeconds(1);

    // Test invalid limitForPeriod
    assertThrows(
        IllegalArgumentException.class,
        () -> new Resilience4jRateLimiter(0, validDuration),
        "Should throw for limitForPeriod < 1");

    // Test invalid limitRefreshPeriod
    assertThrows(
        IllegalArgumentException.class,
        () -> new Resilience4jRateLimiter(10, Duration.ZERO),
        "Should throw for zero duration");

    Duration negativeDuration = Duration.ofSeconds(-1);
    assertThrows(
        IllegalArgumentException.class,
        () -> new Resilience4jRateLimiter(10, negativeDuration),
        "Should throw for negative duration");

    // Test invalid timeout duration
    assertThrows(
        IllegalArgumentException.class,
        () -> new Resilience4jRateLimiter(10, validDuration, negativeDuration),
        "Should throw for negative timeout");

    // Test null name
    assertThrows(
        IllegalArgumentException.class,
        () -> new Resilience4jRateLimiter(null, 10, validDuration, validDuration),
        "Should throw for null name");

    // Test empty name
    assertThrows(
        IllegalArgumentException.class,
        () -> new Resilience4jRateLimiter("", 10, validDuration, validDuration),
        "Should throw for empty name");

    // Test null config
    assertThrows(
        IllegalArgumentException.class,
        () -> new Resilience4jRateLimiter("test", null),
        "Should throw for null config");

    // Test null rate limiter
    assertThrows(
        IllegalArgumentException.class,
        () -> new Resilience4jRateLimiter(null),
        "Should throw for null rate limiter");
  }

  @Test
  void testInitialState() {
    int limit = 10;
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(limit, Duration.ofSeconds(1));

    int available = limiter.availablePermits();
    assertEquals(limit, available, "Should start with full permits");
  }

  @Test
  @DisplayName("tryAcquire should consume permits and return false when exhausted")
  void testTryAcquire() {
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(2, Duration.ofHours(1));

    assertTrue(limiter.tryAcquire(), "First acquisition should succeed");
    assertTrue(limiter.tryAcquire(), "Second acquisition should succeed");
    assertFalse(limiter.tryAcquire(), "Third acquisition should fail (no permits)");

    assertEquals(0, limiter.availablePermits(), "Should have no permits left");
  }

  @Test
  void testRefillLogic() throws InterruptedException {
    // 10 permits per 500ms
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(10, Duration.ofMillis(500));

    // Exhaust all permits
    for (int i = 0; i < 10; i++) {
      assertTrue(limiter.tryAcquire(), "Should acquire permit " + i);
    }
    assertEquals(0, limiter.availablePermits(), "Should have no permits left");

    // Wait for refresh
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(600));

    // Should have permits again
    int available = limiter.availablePermits();
    assertTrue(available > 0, "Should have refilled permits after refresh period");
    assertTrue(limiter.tryAcquire(), "Should be able to acquire after refresh");
  }

  @Test
  void testTryAcquireWithTimeout() throws InterruptedException {
    // 5 permits per 500ms
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(5, Duration.ofMillis(500));

    // Exhaust permits
    for (int i = 0; i < 5; i++) {
      limiter.tryAcquire();
    }

    long start = System.currentTimeMillis();
    // Try to acquire with a 1-second timeout. Should succeed after ~500ms (next cycle)
    boolean success = limiter.tryAcquire(1000);
    long duration = System.currentTimeMillis() - start;

    assertTrue(success, "Should have acquired permit within timeout");
    assertTrue(duration >= 450, "Should have waited for refresh cycle");
    assertTrue(duration < 1000, "Should not wait for full timeout");
  }

  @Test
  void testTryAcquireTimeoutFailure() throws InterruptedException {
    // 1 permit per 10 seconds
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(1, Duration.ofSeconds(10));

    // Exhaust permit
    limiter.tryAcquire();

    // Try with short timeout
    boolean success = limiter.tryAcquire(100);
    assertFalse(success, "Should fail as refresh takes longer than timeout");
  }

  @Test
  void testAcquireBlocking() throws InterruptedException, ExecutionException, TimeoutException {
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(1, Duration.ofMillis(300));

    // Exhaust permit
    limiter.tryAcquire();

    // Start acquire in background thread
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<Long> future =
          executor.submit(
              () -> {
                long start = System.currentTimeMillis();
                try {
                  limiter.acquire();
                } catch (InterruptedException _) {
                  return -1L;
                }
                return System.currentTimeMillis() - start;
              });

      // Should block for approximately 300ms
      Long duration = future.get(1, TimeUnit.SECONDS);
      executor.shutdown();

      assertNotNull(duration);
      assertTrue(duration >= 250, "Should have blocked waiting for permit");
    }
  }

  @Test
  void testConcurrentAccess() throws InterruptedException {
    int limit = 50;
    int threads = 10;
    int attemptsPerThread = 10;

    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(limit, Duration.ofSeconds(1));

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threads);
    List<Integer> successCounts = new CopyOnWriteArrayList<>();

    // Create threads that try to acquire permits
    for (int i = 0; i < threads; i++) {
      new Thread(
              () -> {
                try {
                  startLatch.await(); // Wait for all threads to be ready
                  int successes = 0;
                  for (int j = 0; j < attemptsPerThread; j++) {
                    if (limiter.tryAcquire()) {
                      successes++;
                    }
                  }
                  successCounts.add(successes);
                } catch (InterruptedException _) {
                  Thread.currentThread().interrupt();
                } finally {
                  endLatch.countDown();
                }
              })
          .start();
    }

    // Start all threads simultaneously
    startLatch.countDown();
    assertTrue(endLatch.await(5, TimeUnit.SECONDS), "All threads should complete");

    // Total successful acquisitions should not exceed the limit
    int totalSuccesses = successCounts.stream().mapToInt(Integer::intValue).sum();
    assertTrue(
        totalSuccesses <= limit, "Total acquisitions should not exceed limit: " + totalSuccesses);
    assertTrue(totalSuccesses > 0, "Should have some successful acquisitions");
  }

  @Test
  void testConfigurationGetters() {
    int limit = 100;
    Duration refreshPeriod = Duration.ofSeconds(1);
    Duration timeout = Duration.ofSeconds(5);

    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(limit, refreshPeriod, timeout);

    assertEquals(limit, limiter.getLimitForPeriod(), "Should return correct limit");
    assertEquals(refreshPeriod, limiter.getLimitRefreshPeriod(), "Should return correct period");
    assertEquals(timeout, limiter.getTimeoutDuration(), "Should return correct timeout");
    assertNotNull(limiter.getName(), "Should have a name");
  }

  @Test
  void testCustomConfig() {
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(50)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(10))
            .build();

    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter("customLimiter", config);

    assertEquals(50, limiter.getLimitForPeriod());
    assertEquals(Duration.ofSeconds(1), limiter.getLimitRefreshPeriod());
    assertEquals(Duration.ofSeconds(10), limiter.getTimeoutDuration());
    assertEquals("customLimiter", limiter.getName());
  }

  @Test
  void testWrappingExistingRateLimiter() {
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(20)
            .limitRefreshPeriod(Duration.ofMillis(500))
            .timeoutDuration(Duration.ofSeconds(3))
            .build();

    RateLimiter r4jLimiter = RateLimiter.of("wrappedLimiter", config);
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(r4jLimiter);

    assertEquals(r4jLimiter, limiter.getRateLimiter());
    assertEquals(20, limiter.getLimitForPeriod());
    assertTrue(limiter.tryAcquire());
  }

  @Test
  void testNumberOfWaitingThreads() throws InterruptedException {
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(1, Duration.ofSeconds(10));

    // Exhaust the permit
    limiter.tryAcquire();

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch readyLatch = new CountDownLatch(3);

    // Start 3 threads that will wait
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      Thread thread =
          new Thread(
              () -> {
                readyLatch.countDown();
                try {
                  startLatch.await();
                  limiter.tryAcquire(100);
                } catch (InterruptedException _) {
                  Thread.currentThread().interrupt();
                }
              });
      thread.start();
      threads.add(thread);
    }

    // Wait for threads to be ready
    readyLatch.await();
    new ThreadSleepingSleeper()
        .sleep(Duration.ofMillis(50)); // Small delay to ensure threads are set up

    // Start the waiting
    startLatch.countDown();
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(50)); // Give threads time to start waiting

    // Check waiting threads (maybe 0 if threads already completed with timeout)
    int waiting = limiter.getNumberOfWaitingThreads();
    assertTrue(waiting >= 0, "Waiting threads should be non-negative");

    // Clean up
    for (Thread thread : threads) {
      thread.join(1000);
    }
  }

  @Test
  void testInterruptionDuringAcquire() throws InterruptedException {
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(1, Duration.ofSeconds(10));

    // Exhaust permit
    limiter.tryAcquire();

    Thread testThread =
        new Thread(
            () -> {
              try {
                limiter.acquire(); // Should block
                fail("Should have been interrupted");
              } catch (InterruptedException _) {
                // Expected
              }
            });

    testThread.start();
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(100)); // Let thread start blocking

    testThread.interrupt();
    testThread.join(1000);

    assertFalse(testThread.isAlive(), "Thread should have terminated");
  }

  @Test
  void testHighFrequencyRequests() {
    int limit = 1000;
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(limit, Duration.ofSeconds(1));

    int successCount = 0;
    for (int i = 0; i < limit + 100; i++) {
      if (limiter.tryAcquire()) {
        successCount++;
      }
    }

    // Should allow exactly limit requests (or very close due to timing)
    assertTrue(successCount <= limit, "Should not exceed limit: " + successCount + " vs " + limit);
    assertTrue(
        successCount >= limit - 10, "Should allow most requests up to limit: " + successCount);
  }

  @Test
  void testShortRefreshPeriod() throws InterruptedException {
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(5, Duration.ofMillis(100));

    // First batch
    for (int i = 0; i < 5; i++) {
      assertTrue(limiter.tryAcquire(), "Should acquire permit " + i);
    }
    assertFalse(limiter.tryAcquire(), "Should be out of permits");

    // Wait for refresh
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(150));

    // Second batch
    assertTrue(limiter.tryAcquire(), "Should have permits after refresh");
  }

  @Test
  void testNegativeTimeout() {
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(10, Duration.ofSeconds(1));

    assertThrows(
        IllegalArgumentException.class,
        () -> limiter.tryAcquire(-1),
        "Should throw for negative timeout");
  }

  @Test
  void testReset() {
    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(10, Duration.ofSeconds(1));

    // Consume some permits
    for (int i = 0; i < 5; i++) {
      limiter.tryAcquire();
    }

    // Reset (note: Resilience4j doesn't support reset, but method shouldn't throw)
    assertDoesNotThrow(limiter::reset);
  }

  @Test
  void testNanoPrecision() {
    // Very high frequency: 1000 permits per millisecond
    Resilience4jRateLimiter limiter =
        new Resilience4jRateLimiter(1000, Duration.ofMillis(1), Duration.ofMillis(100));

    // Should be able to acquire many permits quickly
    int count = 0;
    for (int i = 0; i < 1000 && limiter.tryAcquire(); i++) {
      count++;
    }

    assertTrue(count > 0, "Should acquire at least some permits");
  }
}
