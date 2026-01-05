package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes a list of child {@link Workflow}s sequentially in the order they were added.
 *
 * <p>Each child workflow is executed one at a time. If any workflow fails (returns {@link
 * WorkflowStatus#FAILED}), the sequence stops immediately and returns a failed result with the
 * error from the failing workflow. All child workflows share the same {@link WorkflowContext},
 * allowing them to pass data through the pipeline.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Order:</b> Workflows execute in the exact order they were added to the builder
 *   <li><b>Short-circuit:</b> Stops at the first failure <b>Context Sharing:</b> All workflows
 *       receive the same context instance
 *   <li><b>Empty List:</b> Returns SUCCESS immediately if no workflows are provided
 *   <li><b>Null Results:</b> Treats null results as failures with descriptive errors
 * </ul>
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe. However, child workflows must also be
 * thread-safe if executed concurrently from multiple threads.
 *
 * <p><b>Common Use Cases:</b>
 *
 * <ul>
 *   <li>Data processing pipelines (validate → transform → save)
 *   <li>Multi-stage deployments (build → test → deploy)
 *   <li>Sequential ETL operations
 *   <li>Ordered initialization sequences
 * </ul>
 *
 * <p><b>Example usage - Data Pipeline:</b>
 *
 * <pre>{@code
 * Workflow pipeline = SequentialWorkflow.builder()
 *     .name("DataProcessingPipeline")
 *     .workflow(validationWorkflow)   // First: validate input
 *     .workflow(transformWorkflow)    // Second: transform data
 *     .workflow(enrichmentWorkflow)   // Third: enrich with additional data
 *     .workflow(persistenceWorkflow)  // Fourth: save to database
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("rawData", inputData);
 * WorkflowResult result = pipeline.execute(context);
 *
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     Object processedData = context.get("processedData");
 *     System.out.println("Pipeline completed in " + result.getExecutionDuration());
 * }
 * }</pre>
 *
 * <p><b>Example usage - Using Task Convenience Methods:</b>
 *
 * <pre>{@code
 * SequentialWorkflow workflow = SequentialWorkflow.builder()
 *     .name("SimpleSequence")
 *     .task(new ValidationTask())           // Automatically wrapped in TaskWorkflow
 *     .task(TaskDescriptor.builder()        // With retry and timeout policies
 *         .task(new HttpCallTask())
 *         .retryPolicy(RetryPolicy.limitedRetries(3))
 *         .timeoutPolicy(TimeoutPolicy.ofMillis(5000))
 *         .build())
 *     .workflow(new ComplexWorkflow())      // Mix tasks and workflows
 *     .build();
 * }</pre>
 *
 * <p><b>Failure Handling:</b> When a child workflow fails, the sequence stops immediately and
 * returns a FAILED result. The error from the failing workflow is preserved in the result.
 * Subsequent workflows in the sequence are not executed.
 *
 * <pre>{@code
 * // If workflow2 fails, workflow3 and workflow4 will NOT execute
 * SequentialWorkflow.builder()
 *     .workflow(workflow1)  // executes
 *     .workflow(workflow2)  // executes and fails
 *     .workflow(workflow3)  // SKIPPED
 *     .workflow(workflow4)  // SKIPPED
 *     .build();
 * }</pre>
 *
 * @see Workflow
 * @see AbstractWorkflow
 * @see ParallelWorkflow
 */
@Slf4j
public class SequentialWorkflow extends AbstractWorkflow implements WorkflowContainer {
  private final String name;
  private final List<Workflow> workflows;

  /**
   * Private constructor used by the Builder.
   *
   * @param builder the builder instance containing configuration
   */
  private SequentialWorkflow(SequentialWorkflowBuilder builder) {
    this.name = builder.name;
    this.workflows = builder.workflows;
  }

  /**
   * Executes all child workflows sequentially until completion or first failure.
   *
   * <p>The implementation:
   *
   * <ol>
   *   <li>Returns SUCCESS immediately if the workflow list is empty
   *   <li>Executes each workflow in order
   *   <li>Checks for null results and treats them as failures
   *   <li>Stops at the first FAILED status and returns the error
   *   <li>Returns SUCCESS if all workflows complete successfully
   * </ol>
   *
   * @param context the workflow context shared across all child workflows
   * @param execContext execution context for building results
   * @return SUCCESS if all workflows succeed, FAILED if any workflow fails or returns null
   */
  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    // Handle empty workflows
    if (workflows.isEmpty()) {
      log.debug("No workflows to execute in SequentialWorkflow: {}", getName());
      return execContext.success();
    }

    for (Workflow workflow : workflows) {
      log.debug("Executing child workflow: {}", workflow.getName());
      WorkflowResult result = workflow.execute(context);

      // Null check for safety
      if (result == null) {
        String errorMsg =
            String.format(
                "Child workflow '%s' returned null result in SequentialWorkflow '%s'",
                workflow.getName(), getName());
        log.error(errorMsg);
        return execContext.failure(new IllegalStateException(errorMsg));
      }

      if (result.getStatus() == WorkflowStatus.FAILED) {
        log.error("Child workflow {} failed, stopping sequence", workflow.getName());
        return execContext.failure(result.getError());
      }
    }

    return execContext.success();
  }

  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("Sequence");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    return Collections.unmodifiableList(workflows);
  }

  /**
   * Creates a new builder for {@link SequentialWorkflow}.
   *
   * @return a new builder instance
   */
  public static SequentialWorkflowBuilder builder() {
    return new SequentialWorkflowBuilder();
  }

  /**
   * Builder for {@link SequentialWorkflow} with convenience methods for adding tasks.
   *
   * <p>The builder provides three ways to add execution units:
   *
   * <ul>
   *   <li>{@link #workflow(Workflow)} - Add a complete workflow
   *   <li>{@link #task(Task)} - Add a task (automatically wrapped in TaskWorkflow)
   *   <li>{@link #task(TaskDescriptor)} - Add a task with policies (wrapped in TaskWorkflow)
   * </ul>
   */
  public static class SequentialWorkflowBuilder {
    private String name;
    private final List<Workflow> workflows = new ArrayList<>();

    /**
     * Sets the name of the sequential workflow.
     *
     * @param name a descriptive name for logging and debugging
     * @return this builder
     */
    public SequentialWorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Adds a single workflow to the sequence.
     *
     * @param workflow the workflow to add; must not be null
     * @return this builder
     */
    public SequentialWorkflowBuilder workflow(Workflow workflow) {
      if (workflow != null) {
        this.workflows.add(workflow);
      }
      return this;
    }

    /**
     * Adds a list of workflows to the sequence.
     *
     * @param workflows list of workflows to add; must not be null
     * @return this builder
     */
    public SequentialWorkflowBuilder workflows(List<? extends Workflow> workflows) {
      if (workflows != null) {
        this.workflows.addAll(workflows.stream().filter(Objects::nonNull).toList());
      }
      return this;
    }

    /**
     * Convenience method to add a Task as a child workflow. The task is automatically wrapped in a
     * {@link TaskWorkflow}.
     *
     * @param task the task to execute; must not be null
     * @throws NullPointerException if task is null
     * @return this builder
     */
    public SequentialWorkflowBuilder task(Task task) {
      ValidationUtils.requireNonNull(task, "task");
      return workflow(new TaskWorkflow(task));
    }

    /**
     * Convenience method to add a TaskDescriptor as a child workflow. The task descriptor is
     * wrapped in a {@link TaskWorkflow}.
     *
     * @throws NullPointerException if taskDescriptor (or the task within it) or taskExecutor is
     *     null
     * @return this builder
     */
    public SequentialWorkflowBuilder task(TaskDescriptor taskDescriptor) {
      ValidationUtils.requireNonNull(taskDescriptor, "taskDescriptor");
      ValidationUtils.requireNonNull(taskDescriptor.getTask(), "task");
      return workflow(new TaskWorkflow(taskDescriptor));
    }

    /**
     * Builds and returns a new {@link SequentialWorkflow}.
     *
     * @return a configured SequentialWorkflow instance
     */
    public SequentialWorkflow build() {
      return new SequentialWorkflow(this);
    }
  }
}
