package com.workflow.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.sleeper.ThreadSleepingSleeper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.local.LocalBucket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Bucket4jRateLimiterTest {

  @Test
  void testConstructorInvalidArgs() {
    Duration validDuration = Duration.ofSeconds(1);

    // Test invalid capacity
    assertThrows(
        IllegalArgumentException.class,
        () -> new Bucket4jRateLimiter(0, validDuration),
        "Should throw for capacity < 1");

    // Test invalid refillPeriod
    assertThrows(
        IllegalArgumentException.class,
        () -> new Bucket4jRateLimiter(10, Duration.ZERO),
        "Should throw for zero duration");

    Duration negativeDuration = Duration.ofSeconds(-1);
    assertThrows(
        IllegalArgumentException.class,
        () -> new Bucket4jRateLimiter(10, negativeDuration),
        "Should throw for negative duration");

    // Test null refillPeriod
    assertThrows(
        IllegalArgumentException.class,
        () -> new Bucket4jRateLimiter(10, null),
        "Should throw for null duration");

    // Test invalid refillTokens
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Bucket4jRateLimiter(
                10, 0, validDuration, Bucket4jRateLimiter.RefillStrategy.GREEDY),
        "Should throw for refillTokens < 1");

    // Test null refillStrategy
    assertThrows(
        IllegalArgumentException.class,
        () -> new Bucket4jRateLimiter(10, 10, validDuration, null),
        "Should throw for null refillStrategy");

    // Test null bandwidth
    assertThrows(
        IllegalArgumentException.class,
        () -> new Bucket4jRateLimiter((Bandwidth) null),
        "Should throw for null bandwidth");

    // Test null configuration
    assertThrows(
        IllegalArgumentException.class,
        () -> new Bucket4jRateLimiter((BucketConfiguration) null),
        "Should throw for null configuration");

    // Test null bucket
    assertThrows(
        IllegalArgumentException.class,
        () -> new Bucket4jRateLimiter((LocalBucket) null),
        "Should throw for null bucket");
  }

  @Test
  void testInitialState() {
    long capacity = 10;
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(capacity, Duration.ofSeconds(1));

    int available = limiter.availablePermits();
    assertEquals(capacity, available, "Should start with full capacity");
    assertEquals(capacity, limiter.getCapacity(), "Capacity should match constructor argument");
  }

  @Test
  @DisplayName("tryAcquire should consume tokens and return false when exhausted")
  void testTryAcquire() {
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(2, Duration.ofHours(1));

    assertTrue(limiter.tryAcquire(), "First acquisition should succeed");
    assertEquals(1, limiter.availablePermits(), "Should have 1 token left");

    assertTrue(limiter.tryAcquire(), "Second acquisition should succeed");
    assertEquals(0, limiter.availablePermits(), "Should have no tokens left");

    assertFalse(limiter.tryAcquire(), "Third acquisition should fail (no tokens)");
    assertEquals(0, limiter.availablePermits(), "Should still have no tokens left");
  }

  @Test
  void testRefillLogic() throws InterruptedException {
    // 10 tokens per 500ms
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(10, Duration.ofMillis(500));

    // Exhaust all tokens
    for (int i = 0; i < 10; i++) {
      assertTrue(limiter.tryAcquire(), "Should acquire token " + i);
    }
    assertEquals(0, limiter.availablePermits(), "Should have no tokens left");

    // Wait for refill
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(600));

    // Should have tokens again
    int available = limiter.availablePermits();
    assertTrue(available > 0, "Should have refilled tokens after refresh period");
    assertTrue(limiter.tryAcquire(), "Should be able to acquire after refresh");
  }

  @Test
  void testTryAcquireWithTimeout() throws InterruptedException {
    // 10 tokens per 200ms - faster refill for more reliable test
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(10, Duration.ofMillis(200));

    // Exhaust tokens
    for (int i = 0; i < 10; i++) {
      limiter.tryAcquire();
    }

    long start = System.currentTimeMillis();
    // Try to acquire with a 1-second timeout.
    // With greedy refill in bucket4j, tokens refill gradually over time
    // After exhausting all tokens, we should get a token within the timeout
    boolean success = limiter.tryAcquire(1000);
    long duration = System.currentTimeMillis() - start;

    assertTrue(success, "Should have acquired token within timeout");
    // With greedy refill, tokens become available gradually, so we expect some wait
    // but not necessarily the full refill period
    assertTrue(duration >= 10, "Should have waited at least a bit (duration: " + duration + "ms)");
    assertTrue(duration < 1000, "Should not wait for full timeout");
  }

  @Test
  void testTryAcquireTimeoutFailure() throws InterruptedException {
    // 1 token per 10 seconds
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(1, Duration.ofSeconds(10));

    // Exhaust token
    limiter.tryAcquire();

    // Try with short timeout
    boolean success = limiter.tryAcquire(100);
    assertFalse(success, "Should fail as refresh takes longer than timeout");
  }

  @Test
  void testTryAcquireTimeoutInvalidArgs() {
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(10, Duration.ofSeconds(1));

    assertThrows(
        IllegalArgumentException.class,
        () -> limiter.tryAcquire(-1),
        "Should throw for negative timeout");
  }

  @Test
  void testAcquireBlocking() throws InterruptedException, ExecutionException, TimeoutException {
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(1, Duration.ofMillis(300));

    // Exhaust token
    limiter.tryAcquire();

    // Start acquire in background thread
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      long start = System.currentTimeMillis();
      Future<Void> future =
          executor.submit(
              () -> {
                limiter.acquire();
                return null;
              });

      // Wait for completion
      future.get(1, TimeUnit.SECONDS);
      long duration = System.currentTimeMillis() - start;

      // Should have waited for refill
      assertTrue(duration >= 250, "Should have blocked until refill");
      assertTrue(duration < 800, "Should not block too long");

      executor.shutdown();
    }
  }

  @Test
  void testConcurrentAccess() throws InterruptedException {
    int limit = 100;
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(limit, Duration.ofMinutes(1));

    int threadCount = 10;
    int attemptsPerThread = 15;
    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
      CountDownLatch latch = new CountDownLatch(threadCount);
      List<Integer> successCounts = new ArrayList<>();

      for (int i = 0; i < threadCount; i++) {
        executor.submit(
            () -> {
              int successes = 0;
              for (int j = 0; j < attemptsPerThread; j++) {
                if (limiter.tryAcquire()) {
                  successes++;
                }
              }
              synchronized (successCounts) {
                successCounts.add(successes);
              }
              latch.countDown();
            });
      }

      boolean result = latch.await(10, TimeUnit.SECONDS);
      assertTrue(result, "All threads should complete in time");
      executor.shutdown();

      int totalSuccesses = successCounts.stream().mapToInt(Integer::intValue).sum();
      assertEquals(limit, totalSuccesses, "Total successes should equal limit");
    }
  }

  @Test
  void testGreedyRefillStrategy() throws InterruptedException {
    // 10 tokens per second with GREEDY refill
    Bucket4jRateLimiter limiter =
        new Bucket4jRateLimiter(
            10, 10, Duration.ofSeconds(1), Bucket4jRateLimiter.RefillStrategy.GREEDY);

    // Exhaust all tokens
    for (int i = 0; i < 10; i++) {
      limiter.tryAcquire();
    }

    // Wait for refill
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(1100));

    // With greedy refill, all tokens should be available at once
    int available = limiter.availablePermits();
    assertTrue(available >= 9, "Should have most/all tokens refilled at once with greedy strategy");
  }

  @Test
  void testIntervallyRefillStrategy() throws InterruptedException {
    // 10 tokens per second with INTERVALLY refill
    Bucket4jRateLimiter limiter =
        new Bucket4jRateLimiter(
            10, 10, Duration.ofSeconds(1), Bucket4jRateLimiter.RefillStrategy.INTERVALLY);

    // Exhaust all tokens
    for (int i = 0; i < 10; i++) {
      limiter.tryAcquire();
    }

    // Wait for partial refill
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(1100));

    // Should have tokens available
    int available = limiter.availablePermits();
    assertTrue(available > 0, "Should have some tokens refilled");
  }

  @Test
  void testBandwidthConstructor() {
    Bandwidth bandwidth =
        Bandwidth.builder().capacity(50).refillGreedy(50, Duration.ofSeconds(1)).build();

    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(bandwidth);

    assertEquals(50, limiter.getCapacity(), "Capacity should match bandwidth");
    assertEquals(50, limiter.availablePermits(), "Should start with full capacity");
  }

  @Test
  void testBucketConfigurationConstructor() {
    BucketConfiguration config =
        BucketConfiguration.builder()
            .addLimit(
                Bandwidth.builder().capacity(75).refillGreedy(75, Duration.ofSeconds(1)).build())
            .build();

    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(config);

    assertEquals(75, limiter.getCapacity(), "Capacity should match configuration");
    assertEquals(75, limiter.availablePermits(), "Should start with full capacity");
  }

  @Test
  void testLocalBucketConstructor() {
    LocalBucket bucket =
        Bucket.builder()
            .addLimit(
                Bandwidth.builder().capacity(25).refillGreedy(25, Duration.ofSeconds(1)).build())
            .build();

    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(bucket);

    assertEquals(25, limiter.getCapacity(), "Capacity should match bucket");
    assertEquals(25, limiter.availablePermits(), "Should start with full capacity");
  }

  @Test
  void testGetBucket() {
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(10, Duration.ofSeconds(1));

    assertNotNull(limiter.getBucket(), "Bucket should not be null");
    assertNotNull(limiter.getBandwidth(), "Bandwidth should not be null");
  }

  @Test
  void testReset() {
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(10, Duration.ofSeconds(1));

    // Consume some tokens
    limiter.tryAcquire();
    limiter.tryAcquire();

    // Reset should not throw (even though it's a no-op for bucket4j)
    assertDoesNotThrow(limiter::reset);

    // Bucket4j doesn't support reset, so state should remain unchanged
    assertTrue(limiter.availablePermits() < 10, "Tokens should still be consumed after reset");
  }

  @Test
  void testHighVolumeAcquisition() {
    int limit = 1000;
    Bucket4jRateLimiter limiter = new Bucket4jRateLimiter(limit, Duration.ofMinutes(1));

    int successCount = 0;
    for (int i = 0; i < limit * 2; i++) {
      if (limiter.tryAcquire()) {
        successCount++;
      }
    }

    assertEquals(limit, successCount, "Should successfully acquire exactly the limit");
    assertEquals(0, limiter.availablePermits(), "Should have no tokens remaining");
  }

  @Test
  void testBurstCapacity() {
    // Create limiter with burst capacity higher than refill rate
    long burstCapacity = 100;
    long refillRate = 50;
    Bucket4jRateLimiter limiter =
        new Bucket4jRateLimiter(
            burstCapacity,
            refillRate,
            Duration.ofSeconds(1),
            Bucket4jRateLimiter.RefillStrategy.GREEDY);

    // Should be able to consume burst capacity immediately
    int burstCount = 0;
    for (int i = 0; i < burstCapacity; i++) {
      if (limiter.tryAcquire()) {
        burstCount++;
      }
    }

    assertEquals(burstCapacity, burstCount, "Should handle full burst capacity");
  }
}
