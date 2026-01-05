package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskTimeoutException;
import com.workflow.helper.FutureUtils;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Workflow wrapper that applies a timeout to the execution of an inner workflow.
 *
 * <p>This workflow ensures that the inner workflow completes within the specified timeout period.
 * If the workflow exceeds the timeout, execution is interrupted and a FAILED result is returned
 * with a {@link TaskTimeoutException}.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li><b>Transparent:</b> Wraps any workflow without modification
 *   <li><b>Configurable:</b> Flexible timeout duration configuration
 *   <li><b>Interruption:</b> Attempts to interrupt execution on timeout
 *   <li><b>Context Passthrough:</b> Passes context directly to inner workflow
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Preventing hung workflows from blocking resources
 *   <li>Testing workflow behavior under time constraints
 * </ul>
 *
 * <p><b>Example usage - Basic Timeout:</b>
 *
 * <pre>{@code
 * // Create a workflow with 10-second timeout
 * Workflow timedWorkflow = TimeoutWorkflow.builder()
 *     .name("TimedAPICall")
 *     .workflow(apiWorkflow)
 *     .timeoutMs(10000)  // 10 seconds
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * WorkflowResult result = timedWorkflow.execute(context);
 *
 * if (result.getStatus() == WorkflowStatus.FAILED &&
 *     result.getError() instanceof TaskTimeoutException) {
 *     System.err.println("Workflow timed out after 30 seconds");
 * }
 * }</pre>
 *
 * <p><b>Example usage - Parallel with Timeout:</b>
 *
 * <pre>{@code
 * // Timeout entire parallel execution
 * Workflow parallelAPIs = ParallelWorkflow.builder()
 *     .workflow(apiCall1)
 *     .workflow(apiCall2)
 *     .workflow(apiCall3)
 *     .build();
 *
 * Workflow timedParallel = TimeoutWorkflow.builder()
 *     .workflow(parallelAPIs)
 *     .timeoutMs(60000)  // All APIs must complete within 60 seconds
 *     .build();
 * }</pre>
 *
 * <p><b>Interrupt Handling:</b> When a timeout occurs, this workflow attempts to interrupt the
 * execution thread. However, the inner workflow must properly handle interruption by checking
 * {@code Thread.interrupted()} or catching {@code InterruptedException}. Workflows that ignore
 * interruption may continue running even after timeout.
 */
@Slf4j
public class TimeoutWorkflow extends AbstractWorkflow implements WorkflowContainer {
  private final String name;
  private final Workflow workflow;
  private final long timeoutMs;
  private final Executor executor;

  /**
   * Private constructor used by the Builder.
   *
   * @param builder the builder instance containing configuration
   */
  private TimeoutWorkflow(TimeoutWorkflowBuilder builder) {
    this.name = builder.name;
    this.workflow = builder.workflow;
    this.timeoutMs = builder.timeoutMs;
    this.executor = builder.executor;
  }

  /**
   * Executes the inner workflow with a timeout.
   *
   * <p>The implementation:
   *
   * <ol>
   *   <li>Submits the workflow execution as a Future
   *   <li>Waits for completion up to the specified timeout
   *   <li>Returns the workflow result if completed in time
   *   <li>Interrupts execution and returns FAILED if timeout occurs
   *   <li>Properly shuts down the executor in all cases
   * </ol>
   *
   * @param context the workflow context passed to the inner workflow
   * @param execContext execution context for building results
   * @return the result from the inner workflow, or FAILED if timeout occurs
   */
  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    Objects.requireNonNull(workflow, "workflow must not be null");

    if (timeoutMs <= 0) {
      log.warn("Timeout is not configured (timeoutMs={}) for workflow: {}", timeoutMs, getName());
      return workflow.execute(context);
    }

    log.debug("Executing workflow: {} with timeout: {}ms", workflow.getName(), timeoutMs);

    CompletableFuture<WorkflowResult> future =
        CompletableFuture.supplyAsync(() -> workflow.execute(context), executor);

    try {
      WorkflowResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
      log.debug(
          "Workflow: {} completed within timeout: {}ms with status: {}",
          workflow.getName(),
          timeoutMs,
          result.getStatus());
      return result;

    } catch (TimeoutException e) {
      log.error(
          "Workflow: {} exceeded timeout of {}ms, attempting to interrupt",
          workflow.getName(),
          timeoutMs);
      FutureUtils.cancelFuture(future); // Attempt to interrupt the workflow execution
      return execContext.failure(
          new TaskTimeoutException(
              "Workflow " + workflow.getName() + " exceeded timeout of " + timeoutMs + "ms", e));

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Restore interrupt status
      log.error("Workflow: {} was interrupted while waiting for completion", workflow.getName());
      FutureUtils.cancelFuture(future);
      return execContext.failure(e);

    } catch (ExecutionException e) {
      log.error(
          "Workflow: {} threw exception during execution: {}",
          workflow.getName(),
          e.getCause().getMessage(),
          e.getCause());
      return execContext.failure(e.getCause() != null ? e.getCause() : e);
    }
  }

  /**
   * Returns the workflow name.
   *
   * @return the provided name or a generated default name
   */
  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("Timeout");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    return List.of(workflow);
  }

  /**
   * Static factory method to create a new builder instance.
   *
   * @return a new TimeoutWorkflowBuilder
   */
  public static TimeoutWorkflowBuilder builder() {
    return new TimeoutWorkflowBuilder();
  }

  /** Builder for {@link TimeoutWorkflow}. */
  public static class TimeoutWorkflowBuilder {
    private String name;
    private Workflow workflow;
    private long timeoutMs;
    private Executor executor = Executors.newCachedThreadPool();

    /**
     * Sets the name of the workflow wrapper.
     *
     * @param name a descriptive name for logging and debugging
     * @return this builder
     */
    public TimeoutWorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the inner workflow to be executed with a timeout.
     *
     * @param workflow the workflow instance to wrap; must not be null
     * @return this builder
     */
    public TimeoutWorkflowBuilder workflow(Workflow workflow) {
      this.workflow = workflow;
      return this;
    }

    /**
     * Sets the timeout duration in milliseconds.
     *
     * @param timeoutMs the timeout limit; if &lt;= 0, timeout is disabled
     * @return this builder
     */
    public TimeoutWorkflowBuilder timeoutMs(long timeoutMs) {
      this.timeoutMs = timeoutMs;
      return this;
    }

    /**
     * Sets the executor used to run the workflow asynchronously.
     *
     * <p>If not provided, a default cached thread pool is used.
     *
     * @param executor the execution service; must not be null
     * @return this builder
     */
    public TimeoutWorkflowBuilder executor(Executor executor) {
      this.executor = executor;
      return this;
    }

    /**
     * Builds and returns a new {@link TimeoutWorkflow}.
     *
     * @return a configured TimeoutWorkflow instance
     * @throws NullPointerException if workflow or executor is null
     */
    public TimeoutWorkflow build() {
      ValidationUtils.requireNonNull(workflow, "workflow");
      ValidationUtils.requireNonNull(executor, "executor");
      return new TimeoutWorkflow(this);
    }
  }
}
