package com.workflow.sleeper;

import java.time.Duration;

/** No-op sleeper useful for tests that want no delay. */
public final class NoOpSleeper implements Sleeper {
  @Override
  public void sleep(Duration d) {
    // intentionally do nothing
  }
}
