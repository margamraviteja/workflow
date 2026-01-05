package com.workflow.sleeper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Counting sleeper records how many times it was called and the last duration. Does not actually
 * sleep (useful for fast unit tests).
 */
public final class CountingSleeper implements Sleeper {
  private final AtomicInteger calls = new AtomicInteger();
  private final AtomicReference<Duration> last = new AtomicReference<>(Duration.ZERO);

  @Override
  public void sleep(Duration d) {
    calls.incrementAndGet();
    last.set(d == null ? Duration.ZERO : d);
  }

  public int getCalls() {
    return calls.get();
  }

  public Duration getLast() {
    return last.get();
  }
}
