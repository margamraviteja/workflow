package com.workflow.sleeper;

import java.time.Duration;
import java.util.Random;

/**
 * * Jitter sleeper adds random jitter to the requested duration. * jitterFraction is between 0.0
 * and 1.0 (e.g., 0.2 = ±20% jitter).
 */
public final class JitterSleeper implements Sleeper {
  private final Random rnd = new Random();
  private final double jitterFraction;

  public JitterSleeper(double jitterFraction) {
    if (jitterFraction < 0.0 || jitterFraction > 1.0) {
      throw new IllegalArgumentException("jitterFraction must be between 0.0 and 1.0");
    }
    this.jitterFraction = jitterFraction;
  }

  @Override
  public void sleep(Duration d) throws InterruptedException {
    long base = Math.max(0, d == null ? 0L : d.toMillis());
    double jitter = (rnd.nextDouble() * 2 - 1) * jitterFraction; // [-j, +j]
    long millis = (long) Math.max(0, base + base * jitter);
    Thread.sleep(millis);
  }
}
