package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.SagaCompensationException;
import com.workflow.helper.TreeRenderer;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import com.workflow.saga.SagaStep;
import com.workflow.task.Task;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements the Saga pattern for distributed transactions with compensating actions.
 *
 * <p><b>Purpose:</b> Executes a sequence of steps where each step has an optional compensating
 * action. If any step fails, previously successful steps are compensated (rolled back) in reverse
 * order. This pattern is essential for maintaining data consistency across distributed systems
 * where traditional ACID transactions are not feasible.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Forward Execution:</b> Steps execute sequentially in the order they were added
 *   <li><b>Failure Detection:</b> If any step returns FAILED status, the saga stops forward
 *       execution
 *   <li><b>Compensation Trigger:</b> On failure, all previously successful steps are compensated
 *   <li><b>Compensation Order:</b> Compensations run in reverse order (last success first)
 *   <li><b>Compensation Continuation:</b> If a compensation fails, remaining compensations still
 *       execute
 *   <li><b>Context Sharing:</b> All steps and compensations share the same WorkflowContext
 *   <li><b>Failure Context:</b> The original failure is available via {@code SAGA_FAILURE_CAUSE}
 *       context key
 * </ul>
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe. Child workflows must also be thread-safe
 * if executed concurrently from multiple threads.
 *
 * <p><b>Common Use Cases:</b>
 *
 * <ul>
 *   <li>Order processing (reserve inventory → charge payment → ship order)
 *   <li>Travel booking (book flight → book hotel → book car)
 *   <li>Account transfers (debit source → credit destination)
 *   <li>Multi-service provisioning (create user → assign permissions → send notification)
 * </ul>
 *
 * <p><b>Example Usage - Order Processing:</b>
 *
 * <pre>{@code
 * SagaWorkflow orderSaga = SagaWorkflow.builder()
 *     .name("OrderProcessingSaga")
 *     .step(SagaStep.builder()
 *         .name("ReserveInventory")
 *         .action(new TaskWorkflow(new ReserveInventoryTask()))
 *         .compensation(new TaskWorkflow(new ReleaseInventoryTask()))
 *         .build())
 *     .step(SagaStep.builder()
 *         .name("ChargePayment")
 *         .action(new TaskWorkflow(new ChargePaymentTask()))
 *         .compensation(new TaskWorkflow(new RefundPaymentTask()))
 *         .build())
 *     .step(SagaStep.builder()
 *         .name("ShipOrder")
 *         .action(new TaskWorkflow(new ShipOrderTask()))
 *         .compensation(new TaskWorkflow(new CancelShipmentTask()))
 *         .build())
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("orderId", orderId);
 * WorkflowResult result = orderSaga.execute(context);
 *
 * if (result.isFailure()) {
 *     // Check if compensation also had issues
 *     if (result.getError() instanceof SagaCompensationException sce) {
 *         log.error("Saga failed and {} compensations also failed",
 *             sce.getCompensationFailureCount());
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example Usage - Using Task Convenience Methods:</b>
 *
 * <pre>{@code
 * SagaWorkflow saga = SagaWorkflow.builder()
 *     .name("SimpleSaga")
 *     .step(SagaStep.builder()
 *         .name("Step1")
 *         .action(ctx -> ctx.put("step1", "done"))
 *         .compensation(ctx -> ctx.remove("step1"))
 *         .build())
 *     .step(SagaStep.builder()
 *         .name("Step2")
 *         .action(ctx -> ctx.put("step2", "done"))
 *         .compensation(ctx -> ctx.remove("step2"))
 *         .build())
 *     .build();
 * }</pre>
 *
 * <p><b>Failure Handling:</b>
 *
 * <pre>{@code
 * // If step 3 fails after steps 1 and 2 succeed:
 * // 1. Step 3 action fails
 * // 2. Step 2 compensation runs
 * // 3. Step 1 compensation runs
 * // 4. SagaWorkflow returns FAILED with SagaCompensationException
 *
 * // If step 2 compensation also fails:
 * // 1. Step 3 action fails
 * // 2. Step 2 compensation fails (error recorded)
 * // 3. Step 1 compensation still runs (saga continues compensating)
 * // 4. SagaCompensationException contains both original and compensation errors
 * }</pre>
 *
 * @see SagaStep
 * @see SagaCompensationException
 * @see Workflow
 * @see AbstractWorkflow
 */
@Slf4j
public class SagaWorkflow extends AbstractWorkflow implements WorkflowContainer {

  /** Context key for accessing the original saga failure cause during compensation. */
  public static final String SAGA_FAILURE_CAUSE = "SAGA_FAILURE_CAUSE";

  /** Context key for accessing the name of the failed step during compensation. */
  public static final String SAGA_FAILED_STEP = "SAGA_FAILED_STEP";

  private final String name;
  private final List<SagaStep> steps;

  private SagaWorkflow(SagaWorkflowBuilder builder) {
    this.name = builder.name;
    this.steps = new ArrayList<>(builder.steps);
  }

  /**
   * Executes the saga: runs steps sequentially and compensates on failure.
   *
   * <p>The implementation:
   *
   * <ol>
   *   <li>Returns SUCCESS immediately if no steps are defined
   *   <li>Executes each step's action in order, tracking successful steps
   *   <li>On any step failure, stores failure info in context and runs compensations
   *   <li>Compensations run in reverse order (last success → first success)
   *   <li>If compensations fail, continues with remaining compensations
   *   <li>Returns SUCCESS if all steps succeed, FAILED otherwise
   * </ol>
   *
   * @param context the workflow context shared across all steps and compensations
   * @param execContext execution context for building results
   * @return SUCCESS if all steps succeed, FAILED if any step fails (with compensation attempted)
   */
  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    if (steps.isEmpty()) {
      log.debug("No steps to execute in SagaWorkflow: {}", getName());
      return execContext.success();
    }

    List<SagaStep> completedSteps = new ArrayList<>();

    for (SagaStep step : steps) {
      log.debug("Executing saga step: {}", step.getName());
      WorkflowResult stepResult = step.getAction().execute(context);

      if (stepResult == null) {
        String errorMsg =
            String.format(
                "Saga step '%s' action returned null result in SagaWorkflow '%s'",
                step.getName(), getName());
        log.error(errorMsg);
        Throwable error = new IllegalStateException(errorMsg);
        return compensateAndFail(context, execContext, completedSteps, step.getName(), error);
      }

      if (stepResult.getStatus() == WorkflowStatus.FAILED) {
        log.error("Saga step '{}' failed, initiating compensation", step.getName());
        Throwable error =
            stepResult.getError() != null
                ? stepResult.getError()
                : new RuntimeException("Step " + step.getName() + " failed without error details");
        return compensateAndFail(context, execContext, completedSteps, step.getName(), error);
      }

      // Step succeeded, track it for potential compensation
      completedSteps.add(step);
      log.debug("Saga step '{}' completed successfully", step.getName());
    }

    log.info("All {} saga steps completed successfully", steps.size());
    return execContext.success();
  }

  /**
   * Executes compensations for completed steps in reverse order.
   *
   * @param context the workflow context
   * @param execContext execution context for building results
   * @param completedSteps steps that completed successfully and need compensation
   * @param failedStepName name of the step that failed
   * @param originalError the error that triggered compensation
   * @return a FAILED WorkflowResult with appropriate error information
   */
  private WorkflowResult compensateAndFail(
      WorkflowContext context,
      ExecutionContext execContext,
      List<SagaStep> completedSteps,
      String failedStepName,
      Throwable originalError) {

    // Store failure information in context for compensation workflows
    context.put(SAGA_FAILURE_CAUSE, originalError);
    context.put(SAGA_FAILED_STEP, failedStepName);

    List<Throwable> compensationErrors = new ArrayList<>();
    int compensationsAttempted = 0;
    int compensationsSucceeded = 0;

    // Compensate in reverse order
    for (int i = completedSteps.size() - 1; i >= 0; i--) {
      SagaStep step = completedSteps.get(i);

      if (!step.hasCompensation()) {
        log.debug("Saga step '{}' has no compensation, skipping", step.getName());
        continue;
      }

      compensationsAttempted++;
      log.info("Executing compensation for saga step: {}", step.getName());

      try {
        WorkflowResult compensationResult = step.getCompensation().execute(context);

        if (compensationResult == null || compensationResult.getStatus() == WorkflowStatus.FAILED) {
          Throwable compError =
              compensationResult != null && compensationResult.getError() != null
                  ? compensationResult.getError()
                  : new RuntimeException(
                      "Compensation for step '" + step.getName() + "' failed without error");
          log.error(
              "Compensation for step '{}' failed: {}", step.getName(), compError.getMessage());
          compensationErrors.add(compError);
        } else {
          compensationsSucceeded++;
          log.info("Compensation for step '{}' completed successfully", step.getName());
        }
      } catch (Exception e) {
        log.error(
            "Compensation for step '{}' threw exception: {}", step.getName(), e.getMessage(), e);
        compensationErrors.add(e);
      }
    }

    // Clean up context
    context.remove(SAGA_FAILURE_CAUSE);
    context.remove(SAGA_FAILED_STEP);

    log.info(
        "Saga compensation complete: {}/{} compensations succeeded",
        compensationsSucceeded,
        compensationsAttempted);

    // Build appropriate exception
    Throwable resultError;
    if (compensationErrors.isEmpty()) {
      resultError = originalError;
    } else {
      String message =
          String.format(
              "Saga '%s' failed at step '%s' and %d of %d compensations also failed",
              getName(), failedStepName, compensationErrors.size(), compensationsAttempted);
      resultError = new SagaCompensationException(message, originalError, compensationErrors);
    }

    return execContext.failure(resultError);
  }

  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("Saga");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    List<Workflow> treeNodes = new ArrayList<>();

    for (int i = 0; i < steps.size(); i++) {
      SagaStep step = steps.get(i);
      String stepLabel = String.format("STEP %d: %s", i + 1, step.getName());

      // Create a custom container for the step to show both paths
      treeNodes.add(new SagaStepVisualizer(stepLabel, step));
    }

    return Collections.unmodifiableList(treeNodes);
  }

  /** Inner helper to render the Action/Compensation pair for a single Saga step. */
  private record SagaStepVisualizer(String label, SagaStep step)
      implements Workflow, WorkflowContainer {
    @Override
    public String getName() {
      return label;
    }

    @Override
    public WorkflowResult execute(WorkflowContext c) {
      return null;
    }

    @Override
    public List<Workflow> getSubWorkflows() {
      List<Workflow> paths = new ArrayList<>();
      paths.add(new TreeRenderer.TreeLabelWrapper("ACTION ->", step.getAction()));
      if (step.hasCompensation()) {
        paths.add(new TreeRenderer.TreeLabelWrapper("REVERT ->", step.getCompensation()));
      }
      return paths;
    }
  }

  /**
   * Returns all saga steps.
   *
   * @return unmodifiable list of saga steps
   */
  public List<SagaStep> getSteps() {
    return Collections.unmodifiableList(steps);
  }

  /**
   * Creates a new builder for {@link SagaWorkflow}.
   *
   * @return a new builder instance
   */
  public static SagaWorkflowBuilder builder() {
    return new SagaWorkflowBuilder();
  }

  /**
   * Builder for {@link SagaWorkflow}.
   *
   * <p>Provides methods to configure the saga:
   *
   * <ul>
   *   <li>{@link #name(String)} - Human-readable name for logging
   *   <li>{@link #step(SagaStep)} - Add a pre-built saga step
   *   <li>{@link #step(Workflow, Workflow)} - Add action/compensation pair
   *   <li>{@link #step(Task, Task)} - Add action/compensation tasks
   * </ul>
   */
  public static class SagaWorkflowBuilder {
    private String name;
    private final List<SagaStep> steps = new ArrayList<>();

    /**
     * Sets the name of the saga workflow.
     *
     * @param name a descriptive name for logging and debugging
     * @return this builder
     */
    public SagaWorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Adds a pre-built saga step.
     *
     * @param step the saga step to add; must not be null
     * @return this builder
     */
    public SagaWorkflowBuilder step(SagaStep step) {
      ValidationUtils.requireNonNull(step, "step");
      this.steps.add(step);
      return this;
    }

    /**
     * Adds multiple pre-built saga steps.
     *
     * @param steps the saga steps to add
     * @return this builder
     */
    public SagaWorkflowBuilder steps(List<SagaStep> steps) {
      if (steps != null) {
        for (SagaStep step : steps) {
          if (step != null) {
            this.steps.add(step);
          }
        }
      }
      return this;
    }

    /**
     * Convenience method to add a step with action and compensation workflows.
     *
     * @param action the forward action workflow; must not be null
     * @param compensation the compensation workflow; may be null
     * @return this builder
     */
    public SagaWorkflowBuilder step(Workflow action, Workflow compensation) {
      ValidationUtils.requireNonNull(action, "action");
      SagaStep step = SagaStep.builder().action(action).compensation(compensation).build();
      return step(step);
    }

    /**
     * Convenience method to add a step with action and compensation tasks.
     *
     * @param action the forward action task; must not be null
     * @param compensation the compensation task; may be null
     * @return this builder
     */
    public SagaWorkflowBuilder step(Task action, Task compensation) {
      ValidationUtils.requireNonNull(action, "action");
      SagaStep.SagaStepBuilder stepBuilder = SagaStep.builder().action(action);
      if (compensation != null) {
        stepBuilder.compensation(compensation);
      }
      return step(stepBuilder.build());
    }

    /**
     * Convenience method to add a step with only an action (no compensation).
     *
     * @param action the forward action workflow; must not be null
     * @return this builder
     */
    public SagaWorkflowBuilder step(Workflow action) {
      return step(action, null);
    }

    /**
     * Convenience method to add a step with only an action task (no compensation).
     *
     * @param action the forward action task; must not be null
     * @return this builder
     */
    public SagaWorkflowBuilder step(Task action) {
      return step(action, null);
    }

    /**
     * Builds and returns a new {@link SagaWorkflow}.
     *
     * @return a configured SagaWorkflow instance
     */
    public SagaWorkflow build() {
      return new SagaWorkflow(this);
    }
  }
}
