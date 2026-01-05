package com.workflow.policy;

import java.time.Duration;

/**
 * Represents timeout configuration for a task or workflow. When the returned {@code timeoutMs()} is
 * zero or negative, it indicates that no timeout is configured.
 *
 * <p>This interface provides factory methods for creating timeout policies with different time
 * units and a utility method to get the timeout as a {@link Duration}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Create timeout policies
 * TimeoutPolicy fiveSeconds = TimeoutPolicy.ofSeconds(5);
 * TimeoutPolicy twoMinutes = TimeoutPolicy.ofMinutes(2);
 * TimeoutPolicy customMillis = TimeoutPolicy.ofMillis(5000);
 *
 * // Check timeout value
 * long timeout = fiveSeconds.timeoutMs(); // returns 5000
 *
 * // Get as Duration
 * Duration duration = fiveSeconds.getDuration(); // Duration.ofMillis(5000)
 *
 * // No timeout
 * TimeoutPolicy noTimeout = TimeoutPolicy.NONE;
 * System.out.println(noTimeout.timeoutMs()); // 0
 * }</pre>
 *
 * @see com.workflow.task.TaskDescriptor
 * @see com.workflow.TimeoutWorkflow
 */
public interface TimeoutPolicy {
  /**
   * Timeout duration in milliseconds. A value &lt;= 0 means "no timeout".
   *
   * @return timeout in milliseconds or a value &lt;= 0 for no timeout
   */
  long timeoutMs();

  /**
   * Returns the timeout as a {@link Duration}.
   *
   * @return Duration representing the timeout
   */
  default Duration getDuration() {
    return Duration.ofMillis(timeoutMs());
  }

  /** A convenience constant representing no timeout. */
  TimeoutPolicy NONE = () -> 0;

  /**
   * Create a timeout policy with the specified duration in milliseconds.
   *
   * @param millis timeout duration in milliseconds; a value &lt;= 0 indicates no timeout
   * @return a {@link TimeoutPolicy} returning the given timeout
   */
  static TimeoutPolicy ofMillis(long millis) {
    return () -> millis;
  }

  /**
   * Create a timeout policy with the specified duration in seconds.
   *
   * @param seconds timeout duration in seconds; must be positive
   * @return a {@link TimeoutPolicy} with the specified timeout
   * @throws IllegalArgumentException if seconds is negative or zero
   */
  static TimeoutPolicy ofSeconds(long seconds) {
    if (seconds <= 0) {
      throw new IllegalArgumentException("Timeout seconds must be positive, got: " + seconds);
    }
    return () -> seconds * 1000;
  }

  /**
   * Create a timeout policy with the specified duration in minutes.
   *
   * @param minutes timeout duration in minutes; must be positive
   * @return a {@link TimeoutPolicy} with the specified timeout
   * @throws IllegalArgumentException if minutes is negative or zero
   */
  static TimeoutPolicy ofMinutes(long minutes) {
    if (minutes <= 0) {
      throw new IllegalArgumentException("Timeout minutes must be positive, got: " + minutes);
    }
    return () -> minutes * 60 * 1000;
  }
}
