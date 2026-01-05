package com.workflow.sleeper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ThreadSleepingSleeperTest {

  @Test
  void shouldSleepForRequestedDuration() throws InterruptedException {
    ThreadSleepingSleeper sleeper = new ThreadSleepingSleeper();
    long start = System.currentTimeMillis();
    sleeper.sleep(Duration.ofMillis(50));
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed >= 50, "Should sleep at least 50ms, but slept " + elapsed);
    assertTrue(elapsed < 150, "Should not sleep more than 150ms, but slept " + elapsed);
  }

  @Test
  void shouldHandleNullDuration() throws InterruptedException {
    ThreadSleepingSleeper sleeper = new ThreadSleepingSleeper();
    long start = System.currentTimeMillis();
    sleeper.sleep(null);
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed < 50, "Null duration should not sleep");
  }

  @Test
  void shouldHandleZeroDuration() throws InterruptedException {
    ThreadSleepingSleeper sleeper = new ThreadSleepingSleeper();
    long start = System.currentTimeMillis();
    sleeper.sleep(Duration.ZERO);
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed < 50, "Zero duration should not sleep");
  }

  @Test
  void shouldHandleNegativeDuration() throws InterruptedException {
    ThreadSleepingSleeper sleeper = new ThreadSleepingSleeper();
    long start = System.currentTimeMillis();
    sleeper.sleep(Duration.ofMillis(-100));
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed < 50, "Negative duration should not sleep");
  }

  @Test
  void shouldBeInterruptible() {
    ThreadSleepingSleeper sleeper = new ThreadSleepingSleeper();

    Thread thread =
        new Thread(
            () -> {
              try {
                sleeper.sleep(Duration.ofSeconds(10));
                fail("Should have been interrupted");
              } catch (InterruptedException _) {
                // Expected
                assertTrue(Thread.currentThread().isInterrupted());
              }
            });

    thread.start();
    try {
      new ThreadSleepingSleeper()
          .sleep(Duration.ofMillis(50)); // Give thread time to start sleeping
      thread.interrupt();
      thread.join(1000);
      assertFalse(thread.isAlive(), "Thread should have terminated after interrupt");
    } catch (InterruptedException _) {
      fail("Test thread should not be interrupted");
    }
  }

  @Test
  void shouldHandleVeryShortDurations() throws InterruptedException {
    ThreadSleepingSleeper sleeper = new ThreadSleepingSleeper();
    long start = System.currentTimeMillis();
    sleeper.sleep(Duration.ofNanos(100));
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed < 50, "Very short duration should complete quickly");
  }

  @Test
  void shouldHandleMultipleConsecutiveSleeps() throws InterruptedException {
    ThreadSleepingSleeper sleeper = new ThreadSleepingSleeper();
    long start = System.currentTimeMillis();

    sleeper.sleep(Duration.ofMillis(20));
    sleeper.sleep(Duration.ofMillis(20));
    sleeper.sleep(Duration.ofMillis(20));

    long elapsed = System.currentTimeMillis() - start;
    assertTrue(elapsed >= 60, "Total sleep should be at least 60ms, but was " + elapsed);
  }
}
