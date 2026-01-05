package com.workflow.sleeper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CountingSleeperTest {

  @Test
  void shouldCountSleepCalls() {
    CountingSleeper sleeper = new CountingSleeper();
    assertEquals(0, sleeper.getCalls());
    assertEquals(Duration.ZERO, sleeper.getLast());

    sleeper.sleep(Duration.ofMillis(100));
    assertEquals(1, sleeper.getCalls());
    assertEquals(Duration.ofMillis(100), sleeper.getLast());

    sleeper.sleep(Duration.ofMillis(200));
    assertEquals(2, sleeper.getCalls());
    assertEquals(Duration.ofMillis(200), sleeper.getLast());
  }

  @Test
  void shouldHandleNullDuration() {
    CountingSleeper sleeper = new CountingSleeper();

    sleeper.sleep(null);
    assertEquals(1, sleeper.getCalls());
    assertEquals(Duration.ZERO, sleeper.getLast());
  }

  @Test
  void shouldHandleZeroDuration() {
    CountingSleeper sleeper = new CountingSleeper();

    sleeper.sleep(Duration.ZERO);
    assertEquals(1, sleeper.getCalls());
    assertEquals(Duration.ZERO, sleeper.getLast());
  }

  @Test
  void shouldHandleMultipleConcurrentCalls() throws InterruptedException {
    CountingSleeper sleeper = new CountingSleeper();
    int threadCount = 10;
    Thread[] threads = new Thread[threadCount];

    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      threads[i] = new Thread(() -> sleeper.sleep(Duration.ofMillis(index * 10)));
      threads[i].start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    assertEquals(threadCount, sleeper.getCalls());
    assertNotNull(sleeper.getLast());
  }

  @Test
  void shouldRecordLastDurationOnly() {
    CountingSleeper sleeper = new CountingSleeper();

    sleeper.sleep(Duration.ofMillis(100));
    sleeper.sleep(Duration.ofMillis(200));
    sleeper.sleep(Duration.ofMillis(300));

    assertEquals(3, sleeper.getCalls());
    assertEquals(Duration.ofMillis(300), sleeper.getLast());
  }

  @Test
  void shouldHandleLargeDurations() {
    CountingSleeper sleeper = new CountingSleeper();

    Duration largeDuration = Duration.ofDays(365);
    sleeper.sleep(largeDuration);

    assertEquals(1, sleeper.getCalls());
    assertEquals(largeDuration, sleeper.getLast());
  }

  @Test
  void shouldHandleNegativeDurations() {
    CountingSleeper sleeper = new CountingSleeper();

    Duration negativeDuration = Duration.ofMillis(-100);
    sleeper.sleep(negativeDuration);

    assertEquals(1, sleeper.getCalls());
    assertEquals(negativeDuration, sleeper.getLast());
  }
}
