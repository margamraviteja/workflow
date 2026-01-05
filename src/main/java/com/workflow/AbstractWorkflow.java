package com.workflow;

import com.workflow.context.WorkflowContext;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for workflow implementations providing common functionality such as result
 * building, logging, name handling, and error handling.
 *
 * <p>This class eliminates boilerplate code by:
 *
 * <ul>
 *   <li>Automatically handling workflow lifecycle logging (start/completion/failure)
 *   <li>Providing null-safe context validation
 *   <li>Offering result builder utilities through {@link ExecutionContext}
 *   <li>Wrapping unexpected exceptions in {@link WorkflowResult}
 *   <li>Implementing consistent name resolution logic
 * </ul>
 *
 * <p>Subclasses should implement {@link #doExecute(WorkflowContext, ExecutionContext)} to define
 * their specific execution logic without worrying about infrastructure concerns.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Subclasses should document their own
 * thread-safety guarantees.
 *
 * <p><b>Example implementation:</b>
 *
 * <pre>{@code
 * public class ValidationWorkflow extends AbstractWorkflow {
 *     private final String name;
 *     private final List<Validator> validators;
 *
 *     public ValidationWorkflow(String name, List<Validator> validators) {
 *         this.name = name;
 *         this.validators = validators;
 *     }
 *
 *     @Override
 *     protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
 *         for (Validator validator : validators) {
 *             if (!validator.isValid(context)) {
 *                 return execContext.failure(
 *                     new ValidationException("Validation failed: " + validator.getName())
 *                 );
 *             }
 *         }
 *         return execContext.success();
 *     }
 *
 *     @Override
 *     public String getName() {
 *       return name != null ? name : getDefaultName();
 *     }
 * }
 * }</pre>
 *
 * @see Workflow
 * @see ExecutionContext
 */
@Slf4j
@NoArgsConstructor
public abstract class AbstractWorkflow implements Workflow {
  /**
   * Executes the workflow with comprehensive error handling and logging.
   *
   * <p>This method handles the workflow lifecycle:
   *
   * <ol>
   *   <li>Validates that the context is non-null
   *   <li>Logs workflow start
   *   <li>Invokes {@link #doExecute(WorkflowContext, ExecutionContext)}
   *   <li>Validates that the result is non-null
   *   <li>Logs workflow completion or failure
   *   <li>Wraps any unexpected exceptions in a {@link WorkflowResult}
   * </ol>
   *
   * @param context the shared execution context; must not be null
   * @return a {@link WorkflowResult} describing the outcome; never null
   * @throws NullPointerException if context is null
   */
  @Override
  public final WorkflowResult execute(WorkflowContext context) {
    Objects.requireNonNull(context, "WorkflowContext must not be null");
    log.info("Starting workflow: {}", getName());
    context.getListeners().notifyStart(getName(), context);

    ExecutionContext execContext = new ExecutionContext(Instant.now());

    try {
      WorkflowResult result = doExecute(context, execContext);
      Objects.requireNonNull(result, "Workflow result must not be null");

      if (result.getStatus() == WorkflowStatus.FAILED) {
        log.error("Workflow {} failed with error: {}", getName(), result.getError());
        context.getListeners().notifyFailure(getName(), context, result.getError());
      } else {
        log.info("Completed workflow: {} with status: {}", getName(), result.getStatus());
        context.getListeners().notifySuccess(getName(), context, result);
      }

      return result;
    } catch (Exception e) {
      log.error("Workflow {} threw exception: {}", getName(), e.getMessage(), e);
      context.getListeners().notifyFailure(getName(), context, e);
      return execContext.failure(e);
    }
  }

  /**
   * Subclasses implement this method to define their execution logic.
   *
   * <p>This method is called by {@link #execute(WorkflowContext)} after validation and logging.
   * Subclasses should:
   *
   * <ul>
   *   <li>Use the provided {@link ExecutionContext} to build results
   *   <li>Return a non-null {@link WorkflowResult}
   *   <li>Not worry about catching general exceptions (handled by parent)
   *   <li>Document their specific behavior and side effects
   * </ul>
   *
   * @param context the workflow context containing shared state
   * @param execContext execution context with helper methods for building results
   * @return the workflow result; must not be null
   */
  protected abstract WorkflowResult doExecute(
      WorkflowContext context, ExecutionContext execContext);

  /**
   * Returns the workflow name.
   *
   * <p>If a name was provided in the constructor, it is returned. Otherwise, returns the result of
   * {@link #getDefaultName()} which includes the class name and identity hash.
   *
   * @return the workflow name; never null or empty
   */
  @Override
  public String getName() {
    return getDefaultName();
  }

  /**
   * Helper class providing utilities for building workflow results with consistent timestamps.
   *
   * <p>This class ensures that all results from a single workflow execution use the same start time
   * and proper completion times. It provides convenient methods for common result types.
   *
   * <p><b>Example usage:</b>
   *
   * <pre>{@code
   * protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
   *     try {
   *         performWork(context);
   *         return execContext.success();
   *     } catch (ValidationException e) {
   *         return execContext.failure(e);
   *     }
   * }
   * }</pre>
   *
   * @see WorkflowResult
   */
  @Getter
  public static final class ExecutionContext {
    /** Returns the workflow start time. */
    private final Instant startedAt;

    /**
     * Creates an ExecutionContext with the specified start time.
     *
     * @param startedAt the workflow start time
     */
    public ExecutionContext(Instant startedAt) {
      this.startedAt = startedAt;
    }

    /**
     * Creates a successful result.
     *
     * @return a SUCCESS WorkflowResult with start and completion times
     */
    public WorkflowResult success() {
      return WorkflowResult.builder()
          .status(WorkflowStatus.SUCCESS)
          .startedAt(startedAt)
          .completedAt(Instant.now())
          .build();
    }

    /**
     * Creates a failed result with the given error.
     *
     * @param error the cause of failure; may be null (though discouraged)
     * @return a FAILED WorkflowResult with start and completion times
     */
    public WorkflowResult failure(Throwable error) {
      return WorkflowResult.builder()
          .status(WorkflowStatus.FAILED)
          .error(error)
          .startedAt(startedAt)
          .completedAt(Instant.now())
          .build();
    }

    /**
     * Creates a skipped result.
     *
     * <p>A skipped result indicates that the workflow chose not to execute, typically because a
     * precondition was not met (e.g., a conditional branch was not taken).
     *
     * @return a SKIPPED WorkflowResult with start and completion times
     */
    public WorkflowResult skipped() {
      return WorkflowResult.builder()
          .status(WorkflowStatus.SKIPPED)
          .startedAt(startedAt)
          .completedAt(Instant.now())
          .build();
    }

    /**
     * Creates a result with the given status.
     *
     * @param status the workflow status; must not be null
     * @return a WorkflowResult with the given status
     */
    public WorkflowResult result(WorkflowStatus status) {
      return WorkflowResult.builder()
          .status(status)
          .startedAt(startedAt)
          .completedAt(Instant.now())
          .build();
    }

    /**
     * Creates a result with the given status and error.
     *
     * @param status the workflow status; must not be null
     * @param error the error; may be null for non-FAILED statuses
     * @return a WorkflowResult with the given status and error
     */
    public WorkflowResult result(WorkflowStatus status, Throwable error) {
      return WorkflowResult.builder()
          .status(status)
          .error(error)
          .startedAt(startedAt)
          .completedAt(Instant.now())
          .build();
    }
  }
}
