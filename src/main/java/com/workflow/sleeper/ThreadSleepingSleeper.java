package com.workflow.sleeper;

import java.time.Duration;

/** * Sleeps using Thread sleep for the requested duration. */
public final class ThreadSleepingSleeper implements Sleeper {
  @Override
  public void sleep(Duration d) throws InterruptedException {
    long ms = Math.max(0, d == null ? 0L : d.toMillis());
    Thread.sleep(ms);
  }
}
