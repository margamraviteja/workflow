package com.workflow.exception;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Exception thrown when a saga compensation process encounters errors.
 *
 * <p>This exception wraps the original failure that triggered the saga rollback, along with any
 * errors that occurred during the compensation process itself.
 *
 * <p><b>Error Hierarchy:</b>
 *
 * <ul>
 *   <li>{@link #getCause()} - The original exception that caused the saga to fail
 *   <li>{@link #getCompensationErrors()} - List of errors from failed compensation steps
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * try {
 *     sagaWorkflow.execute(context);
 * } catch (Exception e) {
 *     if (e instanceof SagaCompensationException sce) {
 *         System.err.println("Original failure: " + sce.getCause());
 *         System.err.println("Compensation failures: " + sce.getCompensationErrors().size());
 *         for (Throwable compError : sce.getCompensationErrors()) {
 *             System.err.println("  - " + compError.getMessage());
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see com.workflow.SagaWorkflow
 */
@Getter
public class SagaCompensationException extends RuntimeException {

  private final List<Throwable> compensationErrors;

  /**
   * Constructs a new SagaCompensationException.
   *
   * @param message descriptive message about the saga failure
   * @param originalCause the exception that triggered the saga rollback
   * @param compensationErrors list of errors from failed compensation steps
   */
  public SagaCompensationException(
      String message, Throwable originalCause, List<Throwable> compensationErrors) {
    super(message, originalCause);
    this.compensationErrors =
        compensationErrors != null
            ? Collections.unmodifiableList(compensationErrors)
            : Collections.emptyList();
  }

  /**
   * Returns true if any compensation step failed.
   *
   * @return true if compensationErrors is non-empty
   */
  public boolean hasCompensationFailures() {
    return !compensationErrors.isEmpty();
  }

  /**
   * Returns the count of failed compensation steps.
   *
   * @return number of compensation errors
   */
  public int getCompensationFailureCount() {
    return compensationErrors.size();
  }
}
