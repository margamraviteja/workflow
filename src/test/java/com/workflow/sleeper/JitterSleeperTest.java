package com.workflow.sleeper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JitterSleeperTest {

  @Test
  void constructor_withValidJitterFraction_createsInstance() {
    assertDoesNotThrow(() -> new JitterSleeper(0.0));
    assertDoesNotThrow(() -> new JitterSleeper(0.5));
    assertDoesNotThrow(() -> new JitterSleeper(1.0));
  }

  @Test
  void constructor_withNegativeJitterFraction_throwsException() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new JitterSleeper(-0.1));

    assertEquals("jitterFraction must be between 0.0 and 1.0", exception.getMessage());
  }

  @Test
  void constructor_withJitterFractionGreaterThanOne_throwsException() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new JitterSleeper(1.1));

    assertEquals("jitterFraction must be between 0.0 and 1.0", exception.getMessage());
  }

  @Test
  void sleep_withZeroJitter_sleepsExactDuration() throws InterruptedException {
    JitterSleeper sleeper = new JitterSleeper(0.0);
    Duration duration = Duration.ofMillis(100);

    long start = System.currentTimeMillis();
    sleeper.sleep(duration);
    long elapsed = System.currentTimeMillis() - start;

    // With 0 jitter, should sleep approximately the requested duration (±20ms tolerance)
    assertTrue(elapsed >= 80 && elapsed <= 150, "Expected ~100ms, got " + elapsed + "ms");
  }

  @Test
  void sleep_withJitter_sleepsWithinExpectedRange() throws InterruptedException {
    JitterSleeper sleeper = new JitterSleeper(0.5); // 50% jitter
    Duration duration = Duration.ofMillis(200);

    long start = System.currentTimeMillis();
    sleeper.sleep(duration);
    long elapsed = System.currentTimeMillis() - start;

    // With 50% jitter, duration should be between 100ms (200 - 50%) and 300ms (200 + 50%)
    // Add some tolerance for system timing
    assertTrue(
        elapsed >= 80 && elapsed <= 350,
        "Expected between 100ms and 300ms with tolerance, got " + elapsed + "ms");
  }

  @Test
  void sleep_withMaxJitter_sleepsWithinExpectedRange() throws InterruptedException {
    JitterSleeper sleeper = new JitterSleeper(1.0); // 100% jitter
    Duration duration = Duration.ofMillis(200);

    long start = System.currentTimeMillis();
    sleeper.sleep(duration);
    long elapsed = System.currentTimeMillis() - start;

    // With 100% jitter, duration should be between 0ms and 400ms
    assertTrue(
        elapsed >= 0 && elapsed <= 450,
        "Expected between 0ms and 400ms with tolerance, got " + elapsed + "ms");
  }

  @Test
  void sleep_withNullDuration_doesNotThrow() throws InterruptedException {
    JitterSleeper sleeper = new JitterSleeper(0.5);

    long start = System.currentTimeMillis();
    sleeper.sleep(null);
    long elapsed = System.currentTimeMillis() - start;

    // Should complete quickly when duration is null
    assertTrue(elapsed < 100, "Expected quick completion with null duration");
  }

  @Test
  void sleep_withZeroDuration_completesQuickly() throws InterruptedException {
    JitterSleeper sleeper = new JitterSleeper(0.5);

    long start = System.currentTimeMillis();
    sleeper.sleep(Duration.ZERO);
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed < 100, "Expected quick completion with zero duration");
  }

  @Test
  void sleep_withNegativeDuration_doesNotThrow() throws InterruptedException {
    JitterSleeper sleeper = new JitterSleeper(0.5);

    long start = System.currentTimeMillis();
    sleeper.sleep(Duration.ofMillis(-100));
    long elapsed = System.currentTimeMillis() - start;

    // Should complete quickly with negative duration (treated as 0)
    assertTrue(elapsed < 100, "Expected quick completion with negative duration");
  }

  @Test
  void sleep_multipleCalls_producesVariedResults() throws InterruptedException {
    JitterSleeper sleeper = new JitterSleeper(0.5);
    Duration duration = Duration.ofMillis(200);

    long elapsed1 = measureSleepDuration(sleeper, duration);
    long elapsed2 = measureSleepDuration(sleeper, duration);
    long elapsed3 = measureSleepDuration(sleeper, duration);

    // With randomness, at least two of the three should be different (accounting for timing
    // precision)
    boolean hasDifferences =
        Math.abs(elapsed1 - elapsed2) > 10
            || Math.abs(elapsed2 - elapsed3) > 10
            || Math.abs(elapsed1 - elapsed3) > 10;

    assertTrue(
        hasDifferences,
        "Expected varied sleep durations due to jitter: "
            + elapsed1
            + ", "
            + elapsed2
            + ", "
            + elapsed3);
  }

  @Test
  void sleep_canBeInterrupted() {
    JitterSleeper sleeper = new JitterSleeper(0.2);
    Duration duration = Duration.ofSeconds(10);

    Thread thread =
        new Thread(() -> assertThrows(InterruptedException.class, () -> sleeper.sleep(duration)));

    thread.start();
    thread.interrupt();

    assertDoesNotThrow(() -> thread.join(1000));
  }

  @Test
  void sleep_withSmallDuration_handlesCorrectly() throws InterruptedException {
    JitterSleeper sleeper = new JitterSleeper(0.2);
    Duration duration = Duration.ofMillis(10);

    long start = System.currentTimeMillis();
    sleeper.sleep(duration);
    long elapsed = System.currentTimeMillis() - start;

    // With 20% jitter, should be between 8ms and 12ms (with tolerance)
    assertTrue(elapsed >= 0 && elapsed <= 50, "Expected short sleep, got " + elapsed + "ms");
  }

  @Test
  void sleep_withLargeDuration_handlesCorrectly() throws InterruptedException {
    JitterSleeper sleeper = new JitterSleeper(0.1); // 10% jitter
    Duration duration = Duration.ofMillis(500);

    long start = System.currentTimeMillis();
    sleeper.sleep(duration);
    long elapsed = System.currentTimeMillis() - start;

    // With 10% jitter, should be between 450ms and 550ms (with tolerance)
    assertTrue(elapsed >= 400 && elapsed <= 600, "Expected ~500ms ±10%, got " + elapsed + "ms");
  }

  private long measureSleepDuration(JitterSleeper sleeper, Duration duration)
      throws InterruptedException {
    long start = System.currentTimeMillis();
    sleeper.sleep(duration);
    return System.currentTimeMillis() - start;
  }
}
