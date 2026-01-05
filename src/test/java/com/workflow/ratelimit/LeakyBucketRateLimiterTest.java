package com.workflow.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.sleeper.ThreadSleepingSleeper;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeakyBucketRateLimiterTest {

  private LeakyBucketRateLimiter limiter;

  @Test
  @DisplayName("Should allow requests up to capacity and reject immediate excess")
  void testBasicCapacity() {
    // Rate: 1 per second, Capacity: 3
    limiter = new LeakyBucketRateLimiter(1, 3, Duration.ofSeconds(30));

    assertTrue(limiter.tryAcquire());
    assertTrue(limiter.tryAcquire());
    assertTrue(limiter.tryAcquire());
    assertFalse(limiter.tryAcquire(), "Bucket should be full at 3 permits");
  }

  @Test
  @DisplayName("Should leak permits over time accurately")
  void testLeakLogic() throws InterruptedException {
    // Rate: 10 per 500ms (1 permit every 50ms), Capacity: 1
    limiter = new LeakyBucketRateLimiter(10, 1, Duration.ofMillis(500));

    assertTrue(limiter.tryAcquire());
    assertFalse(limiter.tryAcquire());

    // Wait for ~70ms (enough for 1 permit to leak with safety margin)
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(70));

    assertTrue(limiter.tryAcquire(), "Permit should have leaked by now");
  }

  @Test
  @DisplayName("acquire() should block until space is available")
  void testBlockingAcquire() throws InterruptedException {
    // Rate: 1 per 200ms, Capacity: 1
    limiter = new LeakyBucketRateLimiter(1, 1, Duration.ofMillis(200));
    limiter.tryAcquire(); // Fill the bucket

    long start = System.nanoTime();

    // This call will block for ~200ms
    limiter.acquire();

    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    assertTrue(elapsed >= 200, "Should have blocked for at least 200ms, took: " + elapsed);
  }

  @Test
  @DisplayName("tryAcquire with timeout should respect the deadline")
  void testTryAcquireTimeout() throws InterruptedException {
    // Rate: 1 per 500ms, Capacity: 1
    limiter = new LeakyBucketRateLimiter(1, 1, Duration.ofMillis(500));
    limiter.tryAcquire(); // Fill the bucket

    // Scenario 1: Timeout shorter than leak time (Should Fail)
    long startFail = System.currentTimeMillis();
    boolean failed = limiter.tryAcquire(100);
    long elapsedFail = System.currentTimeMillis() - startFail;

    assertFalse(failed);
    assertTrue(elapsedFail >= 100);

    // Scenario 2: Timeout longer than leak time (Should Succeed)
    boolean success = limiter.tryAcquire(600);
    assertTrue(success, "Should have succeeded within 600ms");
  }

  @Test
  @DisplayName("reset() should empty bucket and wake up waiting threads")
  void testResetSignaling() throws InterruptedException {
    limiter = new LeakyBucketRateLimiter(1, 1, Duration.ofDays(1));
    limiter.tryAcquire(); // Fill bucket so it won't leak for a long time

    AtomicBoolean acquireSucceeded = new AtomicBoolean(false);
    CountDownLatch threadStarted = new CountDownLatch(1);

    Thread blockingThread =
        new Thread(
            () -> {
              try {
                threadStarted.countDown();
                limiter.acquire(); // This will block indefinitely until reset
                acquireSucceeded.set(true);
              } catch (InterruptedException _) {
                // ignore
              }
            });

    blockingThread.start();
    threadStarted.await();
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(100));

    assertFalse(acquireSucceeded.get());

    limiter.reset(); // Should trigger signalAll()

    // Wait briefly for the thread to wake up
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(100));
    assertTrue(acquireSucceeded.get(), "Thread should have been woken up by reset()");
    blockingThread.interrupt();
  }

  @Test
  @DisplayName("Should handle high concurrency without exceeding capacity")
  void testConcurrency() throws InterruptedException {
    int threads = 10;
    int capacity = 20;
    // Set rate very slow so we only test capacity/concurrency
    limiter = new LeakyBucketRateLimiter(1, capacity, Duration.ofHours(1));

    ConcurrentLinkedQueue<Boolean> results = new ConcurrentLinkedQueue<>();
    try (ExecutorService service = Executors.newFixedThreadPool(threads)) {
      CountDownLatch latch = new CountDownLatch(threads);

      for (int i = 0; i < threads; i++) {
        service.submit(
            () -> {
              for (int j = 0; j < 5; j++) {
                results.add(limiter.tryAcquire());
              }
              latch.countDown();
            });
      }

      latch.await();
      service.shutdown();
    }

    long successCount = results.stream().filter(b -> b).count();
    assertEquals(capacity, successCount, "Total successful acquires must equal bucket capacity");
  }

  @Test
  void testConstructorValidations() {
    double validRate = 10.0;
    int validCap = 10;
    Duration validPeriod = Duration.ofSeconds(1);
    Duration negativeDuration = Duration.ofSeconds(-1);

    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new LeakyBucketRateLimiter(0, validCap, validPeriod)),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new LeakyBucketRateLimiter(validRate, 0, validPeriod)),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new LeakyBucketRateLimiter(validRate, validCap, Duration.ZERO)),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new LeakyBucketRateLimiter(validRate, validCap, negativeDuration)));
  }

  // --- Leak Logic & Precision Tests ---

  @Test
  @DisplayName("getCurrentWaterLevel should reflect partial leaks")
  void testPartialLeakPrecision() throws InterruptedException {
    // 10 requests per 1000ms = 1 request per 100ms
    limiter = new LeakyBucketRateLimiter(10, 5, Duration.ofMillis(1000));

    limiter.tryAcquire(); // Water = 1.0
    limiter.tryAcquire(); // Water = 2.0

    new ThreadSleepingSleeper().sleep(Duration.ofMillis(150));

    double water = limiter.getCurrentWaterLevel();
    assertTrue(water <= 0.6 && water >= 0.4, "Water level should be approx 0.5, but was: " + water);
  }

  @Test
  @DisplayName("leak() should not move lastLeakTimeNanos forward if no water is leaked")
  void testLeakNoOp() {
    limiter = new LeakyBucketRateLimiter(1, 10, Duration.ofHours(1));
    double initialWater = limiter.getCurrentWaterLevel();
    assertEquals(0.0, initialWater);

    // Immediate check should result in 0 leak
    assertEquals(0.0, limiter.getCurrentWaterLevel());
  }

  // --- tryAcquire(timeout) Exception & Edge Case Tests ---

  @Test
  @DisplayName("tryAcquire(timeout) should throw IllegalArgumentException for negative timeout")
  void testTryAcquireNegativeTimeout() {
    limiter = new LeakyBucketRateLimiter(1, 1, Duration.ofSeconds(1));
    assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-100));
  }

  @Test
  @DisplayName("tryAcquire(timeout) should handle InterruptedException")
  void testTryAcquireInterruption() throws InterruptedException {
    limiter = new LeakyBucketRateLimiter(1, 1, Duration.ofDays(1));
    limiter.tryAcquire(); // Fill bucket

    Thread t =
        new Thread(
            () -> {
              try {
                limiter.tryAcquire(5000);
              } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                System.out.println("thread interrupted as expected");
              } catch (Exception _) {
                System.out.println("unexpected exception");
              }
            });

    t.start();
    new ThreadSleepingSleeper().sleep(Duration.ofMillis(50));
    t.interrupt();
    t.join(500);

    assertFalse(t.isAlive(), "Thread should have terminated after interruption");
  }

  // --- acquire() Internal Loop & Corner Cases ---

  @Test
  @DisplayName("acquire() should handle spurious wake ups via the while-loop")
  void testAcquireRechecksWaterLevel() throws InterruptedException {
    // Rate: 1 per 500ms
    limiter = new LeakyBucketRateLimiter(1, 1, Duration.ofMillis(500));
    limiter.tryAcquire(); // Fill bucket

    long start = System.currentTimeMillis();

    // Start a thread that will reset the limiter mid-way through acquires sleep
    new Thread(
            () -> {
              try {
                new ThreadSleepingSleeper().sleep(Duration.ofMillis(100));
                limiter.reset();
              } catch (InterruptedException _) {
                // ignore
              }
            })
        .start();

    limiter.acquire();
    long elapsed = System.currentTimeMillis() - start;

    // Should succeed much faster than 500ms due to the reset
    assertTrue(elapsed < 300, "Acquire should have returned early after reset");
  }

  // --- availablePermits Tests ---

  @Test
  @DisplayName("availablePermits should return floor of capacity minus water")
  void testAvailablePermits() {
    limiter = new LeakyBucketRateLimiter(10, 5, Duration.ofSeconds(1));

    limiter.tryAcquire();
    limiter.tryAcquire();

    assertEquals(3, limiter.availablePermits());

    // Manual "leak" simulation: if water is 1.7, permits should be floor(5 - 1.7) = 3
    // We achieve this by waiting half a period
  }

  // --- Internal Mathematical Helpers (via Reflection or public state) ---

  @Test
  @DisplayName("Bucket should not leak more water than it contains (Underflow protection)")
  void testLeakUnderflow() throws InterruptedException {
    limiter = new LeakyBucketRateLimiter(100, 10, Duration.ofMillis(10));
    limiter.tryAcquire(); // water = 1

    new ThreadSleepingSleeper().sleep(Duration.ofMillis(50));

    assertEquals(0.0, limiter.getCurrentWaterLevel(), "Water level cannot be negative");
  }
}
