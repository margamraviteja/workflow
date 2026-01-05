package com.workflow.sleeper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SleeperTest {

  @Test
  void noOpSleeper_doesNotSleep() {
    NoOpSleeper sleeper = new NoOpSleeper();

    long start = System.currentTimeMillis();
    sleeper.sleep(Duration.ofMillis(1000));
    long duration = System.currentTimeMillis() - start;

    assertTrue(duration < 100, "NoOpSleeper should complete almost instantly");
  }

  @Test
  void noOpSleeper_withNegativeDuration_doesNotThrow() {
    NoOpSleeper sleeper = new NoOpSleeper();

    assertDoesNotThrow(() -> sleeper.sleep(Duration.ofMillis(-1000)));
  }

  @Test
  void threadSleepingSleeper_withZeroDuration_returnsImmediately() throws InterruptedException {
    ThreadSleepingSleeper sleeper = new ThreadSleepingSleeper();

    long start = System.currentTimeMillis();
    sleeper.sleep(Duration.ZERO);
    long duration = System.currentTimeMillis() - start;

    assertTrue(duration < 50, "Zero sleep should complete quickly");
  }

  @Test
  void threadSleepingSleeper_withNegativeDuration_doesNotThrow() {
    ThreadSleepingSleeper sleeper = new ThreadSleepingSleeper();

    assertDoesNotThrow(() -> sleeper.sleep(Duration.ofMillis(-100)));
  }

  @Test
  void countingSleeper_tracksNumberOfSleeps() {
    CountingSleeper sleeper = new CountingSleeper();

    sleeper.sleep(Duration.ofMillis(100));
    sleeper.sleep(Duration.ofMillis(200));
    sleeper.sleep(Duration.ofMillis(300));

    assertEquals(3, sleeper.getCalls());
  }
}
