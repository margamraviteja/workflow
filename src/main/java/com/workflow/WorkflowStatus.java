package com.workflow;

/**
 * Possible outcomes for a {@link Workflow} execution.
 *
 * <p>The statuses include:
 *
 * <ul>
 *   <li>SUCCESS: Execution completed successfully.
 *   <li>FAILED: Execution failed with an error.
 *   <li>SKIPPED: Execution was skipped (e.g., conditional not met).
 * </ul>
 */
public enum WorkflowStatus {
  /** Execution completed successfully. */
  SUCCESS,

  /** Execution failed with an error. */
  FAILED,

  /** Execution was skipped (e.g., conditional not met). */
  SKIPPED
}
