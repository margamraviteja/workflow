package com.workflow;

import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Immutable result object summarizing a {@link Workflow} execution.
 *
 * <p>Contains: the {@link WorkflowStatus}, an optional {@link Throwable} for failures, and
 * timestamps for started/completed instants. Instances are produced by workflows and should be
 * treated as read-only value objects by callers.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * WorkflowResult result = workflow.execute(context);
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     System.out.println("Workflow completed successfully in " + result.getExecutionDuration());
 * } else {
 *     System.err.println("Workflow failed with error: " + result.getError());
 * }
 * }</pre>
 *
 * <p>Note: This class is immutable and thread-safe.
 *
 * @see com.workflow.helper.WorkflowResults
 */
@Getter
@Builder
@ToString
public class WorkflowResult {
  /** The final status of the workflow. */
  private final WorkflowStatus status;

  /** The error that caused a failure, if any (nullable). */
  private final Throwable error;

  /** When execution started (wall-clock Instant). */
  private final Instant startedAt;

  /** When execution completed (wall-clock Instant). */
  private final Instant completedAt;

  public boolean isSuccess() {
    return status == WorkflowStatus.SUCCESS;
  }

  public boolean isFailure() {
    return status == WorkflowStatus.FAILED;
  }

  public String getExecutionDuration() {
    long durationMillis = completedAt.toEpochMilli() - startedAt.toEpochMilli();
    long seconds = durationMillis / 1000;
    long millis = durationMillis % 1000;
    return String.format("%d.%03d seconds", seconds, millis);
  }

  public Duration getDuration() {
    return Duration.between(startedAt, completedAt);
  }
}
