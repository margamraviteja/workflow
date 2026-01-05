package com.workflow.helper;

import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import java.time.Instant;
import lombok.experimental.UtilityClass;

/**
 * Small helper producing commonly used {@link WorkflowResult} instances for success/failure cases.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * WorkflowResult successResult = WorkflowResults.success(startedAt, completedAt);
 * WorkflowResult failureResult = WorkflowResults.failure(startedAt, completedAt, error);
 * }</pre>
 *
 * @see WorkflowResult
 */
@UtilityClass
public final class WorkflowResults {
  /**
   * Create a successful WorkflowResult populated with start/complete times.
   *
   * @param startedAt when execution started
   * @param completedAt when execution completed
   * @return a SUCCESS {@link WorkflowResult}
   */
  public static WorkflowResult success(Instant startedAt, Instant completedAt) {
    return WorkflowResult.builder()
        .status(WorkflowStatus.SUCCESS)
        .startedAt(startedAt)
        .completedAt(completedAt)
        .build();
  }

  /**
   * Create a failed WorkflowResult populated with start/complete times and the error.
   *
   * @param startedAt when execution started
   * @param completedAt when execution completed
   * @param error cause of the failure
   * @return a FAILED {@link WorkflowResult}
   */
  public static WorkflowResult failure(Instant startedAt, Instant completedAt, Throwable error) {
    return WorkflowResult.builder()
        .status(WorkflowStatus.FAILED)
        .error(error)
        .startedAt(startedAt)
        .completedAt(completedAt)
        .build();
  }
}
