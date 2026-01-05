package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.execution.strategy.ExecutionStrategy;
import com.workflow.execution.strategy.ThreadPoolExecutionStrategy;
import com.workflow.helper.FutureUtils;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes a list of child {@link Workflow}s in parallel using a configurable {@link
 * ExecutionStrategy}.
 *
 * <p>This workflow provides concurrent execution with fine-grained control over:
 *
 * <ul>
 *   <li><b>Execution Strategy:</b> Thread pool, reactive, or custom strategies
 *   <li><b>Fail-Fast Behavior:</b> Stop all workflows on first failure or wait for all
 *   <li><b>Context Sharing:</b> Share a single context or provide isolated copies
 * </ul>
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Concurrency:</b> All workflows start simultaneously (subject to executor capacity)
 *   <li><b>Order:</b> Completion order is non-deterministic
 *   <li><b>Blocking:</b> Waits for all workflows to complete before returning
 *   <li><b>Empty List:</b> Returns SUCCESS immediately if no workflows are provided
 * </ul>
 *
 * <p><b>Context Sharing Modes:</b>
 *
 * <table border="1">
 *   <tr>
 *     <th>Mode</th>
 *     <th>Behavior</th>
 *     <th>Use Case</th>
 *   </tr>
 *   <tr>
 *     <td>shareContext=true (default)</td>
 *     <td>All workflows share same context</td>
 *     <td>Workflows need to read/write shared state</td>
 *   </tr>
 *   <tr>
 *     <td>shareContext=false</td>
 *     <td>Each workflow gets context copy</td>
 *     <td>Independent workflows, isolation needed</td>
 *   </tr>
 * </table>
 *
 * <p><b>Fail-Fast Modes:</b>
 *
 * <table border="1">
 *   <tr>
 *     <th>Mode</th>
 *     <th>Behavior</th>
 *     <th>Use Case</th>
 *   </tr>
 *   <tr>
 *     <td>failFast=true</td>
 *     <td>Cancel all on first failure</td>
 *     <td>Fast feedback, save resources</td>
 *   </tr>
 *   <tr>
 *     <td>failFast=false (default)</td>
 *     <td>Let all workflows complete</td>
 *     <td>Collect all results/errors</td>
 *   </tr>
 * </table>
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe. The {@link WorkflowContext} is thread-safe
 * when shared. Child workflows must be thread-safe.
 *
 * <p><b>Example usage - Independent Parallel Tasks:</b>
 *
 * <pre>{@code
 * Workflow parallelWorkflow = ParallelWorkflow.builder()
 *     .name("DataFetchers")
 *     .workflow(new FetchUserWorkflow())
 *     .workflow(new FetchOrdersWorkflow())
 *     .workflow(new FetchInventoryWorkflow())
 *     .failFast(true)              // Stop all if any fails
 *     .shareContext(false)          // Isolated execution
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("userId", 12345);
 * WorkflowResult result = parallelWorkflow.execute(context);
 * }</pre>
 *
 * <p><b>Example usage - Shared Context:</b>
 *
 * <pre>{@code
 * // Multiple workflows updating shared metrics
 * Workflow metricsCollector = ParallelWorkflow.builder()
 *     .name("MetricsAggregator")
 *     .task(new CpuMetricsTask())
 *     .task(new MemoryMetricsTask())
 *     .task(new DiskMetricsTask())
 *     .shareContext(true)           // All write to same context
 *     .failFast(false)              // Collect all available metrics
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * parallelWorkflow.execute(context);
 * Map<String, Metric> allMetrics = context.get("metrics");
 * }</pre>
 *
 * <p><b>Example usage - Custom Execution Strategy:</b>
 *
 * <pre>{@code
 * ExecutionStrategy reactorStrategy = new ReactorExecutionStrategy();
 *
 * ParallelWorkflow workflow = ParallelWorkflow.builder()
 *     .name("ReactiveParallel")
 *     .workflow(workflow1)
 *     .workflow(workflow2)
 *     .executionStrategy(reactorStrategy)  // Use Project Reactor
 *     .build();
 * }</pre>
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li>Use failFast=true to save resources when any failure invalidates the entire operation
 *   <li>Use shareContext=false when workflows are truly independent to avoid contention
 *   <li>Choose execution strategy based on your workload (IO-bound vs CPU-bound)
 *   <li>Default {@link ThreadPoolExecutionStrategy} uses cached thread pool (grows as needed)
 * </ul>
 *
 * <p><b>Error Handling:</b> If any workflow fails, the overall result is FAILED. The error from the
 * first failure (in fail-fast mode) or any failure (in non-fail-fast mode) is included in the
 * result.
 *
 * @see Workflow
 * @see ExecutionStrategy
 * @see ThreadPoolExecutionStrategy
 * @see SequentialWorkflow
 * @see TimeoutWorkflow
 */
@Slf4j
public class ParallelWorkflow extends AbstractWorkflow implements WorkflowContainer {
  private final String name;
  private final List<Workflow> workflows;

  /**
   * Whether to cancel all workflows when the first one fails.
   *
   * <p>Default: false (wait for all to complete)
   */
  private final boolean failFast;

  /**
   * Whether all workflows share the same context instance.
   *
   * <p>Default: true (shared context)
   */
  private final boolean shareContext;

  /**
   * The strategy used to execute workflows concurrently.
   *
   * <p>Default: {@link ThreadPoolExecutionStrategy}
   */
  private final ExecutionStrategy executionStrategy;

  /** Internal constructor for the builder. */
  private ParallelWorkflow(ParallelWorkflowBuilder builder) {
    this.name = builder.name;
    this.workflows = builder.workflows;
    this.failFast = builder.failFast;
    this.shareContext = builder.shareContext;
    this.executionStrategy =
        Objects.requireNonNull(builder.executionStrategy, "executionStrategy must not be null");
  }

  /**
   * Executes all child workflows in parallel and waits for completion.
   *
   * <p>The implementation:
   *
   * <ol>
   *   <li>Returns SUCCESS immediately if the workflow list is empty
   *   <li>Submits all workflows to the execution strategy
   *   <li>Creates context copies if shareContext=false
   *   <li>Waits for all futures to complete using {@link FutureUtils}
   *   <li>Returns SUCCESS if all complete successfully, FAILED otherwise
   * </ol>
   *
   * @param context the workflow context; shared or copied based on shareContext setting
   * @return SUCCESS if all workflows succeed, FAILED if any workflow fails
   */
  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    // Handle empty workflows
    if (workflows.isEmpty()) {
      log.debug("No workflows to execute in ParallelWorkflow: {}", getName());
      return execContext.success();
    }

    List<CompletableFuture<WorkflowResult>> futures = new ArrayList<>();
    for (Workflow workflow : workflows) {
      WorkflowContext executionContext = shareContext ? context : context.copy();
      futures.add(executionStrategy.submit(() -> workflow.execute(executionContext)));
    }

    AtomicReference<Throwable> errorRef = new AtomicReference<>(null);
    CompletableFuture<Void> allFutures = FutureUtils.allOf(futures, failFast);
    allFutures.whenComplete(
        (_, ex) -> {
          if (ex != null) {
            errorRef.set(ex);
          }
        });
    allFutures.join(); // Wait for all to complete

    if (errorRef.get() != null) {
      log.error("One or more workflows failed in ParallelWorkflow: {}", getName(), errorRef.get());
      return execContext.failure(errorRef.get());
    }
    return execContext.success();
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
    return WorkflowSupport.formatWorkflowType("Parallel");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    return Collections.unmodifiableList(workflows);
  }

  /**
   * Static factory method to create a new builder.
   *
   * @return a new ParallelWorkflowBuilder
   */
  public static ParallelWorkflowBuilder builder() {
    return new ParallelWorkflowBuilder();
  }

  /**
   * Builder for {@link ParallelWorkflow} with convenience methods for adding tasks.
   *
   * <p>The builder supports configuration of:
   *
   * <ul>
   *   <li>Child workflows via {@link #workflow(Workflow)}
   *   <li>Tasks via {@link #task(Task)} or {@link #task(TaskDescriptor)}
   *   <li>Fail-fast behavior via {@link #failFast(boolean)}
   *   <li>Context sharing via {@link #shareContext(boolean)}
   *   <li>Execution strategy via {@link #executionStrategy(ExecutionStrategy)}
   * </ul>
   */
  public static class ParallelWorkflowBuilder {
    private String name;
    private final List<Workflow> workflows = new ArrayList<>();
    private boolean failFast = false;
    private boolean shareContext = true;
    private ExecutionStrategy executionStrategy = new ThreadPoolExecutionStrategy();

    /**
     * Sets the name of the parallel workflow.
     *
     * @param name descriptive name for logging
     * @return this builder
     */
    public ParallelWorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Adds a single workflow to the parallel execution list.
     *
     * @param workflow the workflow to add
     * @return this builder
     */
    public ParallelWorkflowBuilder workflow(Workflow workflow) {
      if (workflow != null) {
        this.workflows.add(workflow);
      }
      return this;
    }

    /**
     * Adds a list of workflows to the parallel execution list.
     *
     * @param workflows the workflows to add
     * @return this builder
     */
    public ParallelWorkflowBuilder workflows(List<? extends Workflow> workflows) {
      if (workflows != null) {
        this.workflows.addAll(workflows.stream().filter(Objects::nonNull).toList());
      }
      return this;
    }

    /**
     * Convenience method to add a single task as a child workflow. The task is automatically
     * wrapped in a {@link TaskWorkflow}.
     *
     * @param task the task to execute; must not be null
     * @throws NullPointerException if task is null
     * @return this builder
     */
    public ParallelWorkflowBuilder task(Task task) {
      ValidationUtils.requireNonNull(task, "task");
      return workflow(new TaskWorkflow(task));
    }

    /**
     * Convenience method to add a task descriptor as a child workflow. The task descriptor is
     * wrapped in a {@link TaskWorkflow}.
     *
     * @param taskDescriptor the task descriptor
     * @throws NullPointerException if taskDescriptor (or the task within it) or taskExecutor is
     *     null
     * @return this builder
     */
    public ParallelWorkflowBuilder task(TaskDescriptor taskDescriptor) {
      ValidationUtils.requireNonNull(taskDescriptor, "taskDescriptor");
      ValidationUtils.requireNonNull(taskDescriptor.getTask(), "task");
      return workflow(new TaskWorkflow(taskDescriptor));
    }

    /**
     * Sets whether to cancel all workflows when the first one fails.
     *
     * @param failFast true to stop on first failure, false (default) to wait for all
     * @return this builder
     */
    public ParallelWorkflowBuilder failFast(boolean failFast) {
      this.failFast = failFast;
      return this;
    }

    /**
     * Sets whether all workflows share the same context instance.
     *
     * @param shareContext true for shared context (default), false for isolated copies
     * @return this builder
     */
    public ParallelWorkflowBuilder shareContext(boolean shareContext) {
      this.shareContext = shareContext;
      return this;
    }

    /**
     * Sets the strategy used to execute workflows concurrently.
     *
     * @param executionStrategy the concurrent execution strategy
     * @return this builder
     */
    public ParallelWorkflowBuilder executionStrategy(ExecutionStrategy executionStrategy) {
      this.executionStrategy = executionStrategy;
      return this;
    }

    /**
     * Builds and returns a new {@link ParallelWorkflow}.
     *
     * @return a configured ParallelWorkflow instance
     * @throws NullPointerException if executionStrategy is null
     */
    public ParallelWorkflow build() {
      ValidationUtils.requireNonNull(executionStrategy, "executionStrategy");
      return new ParallelWorkflow(this);
    }
  }
}
