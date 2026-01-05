package com.workflow.sleeper;

import java.time.Duration;

/** * Functional interface to abstract sleeping for testability. */
@FunctionalInterface
public interface Sleeper {
  void sleep(Duration d) throws InterruptedException;
}
