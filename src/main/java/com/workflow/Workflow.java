package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.TreeRenderer;

/**
 * Core abstraction representing a unit of work that can be executed with a {@link
 * com.workflow.context.WorkflowContext} and produces a {@link WorkflowResult}.
 *
 * <p>Implementations may be composed (for example, a {@link SequentialWorkflow} contains several
 * child {@code Workflow}s) and are expected to be thread-safe where documented by the
 * implementation. A {@code Workflow} should not mutate external state except via the provided
 * {@link WorkflowContext} or well-documented side effects.
 *
 * <p>Typical implementations include wrappers around a single {@link com.workflow.task.Task}
 * ({@link TaskWorkflow}) or composition types like {@link SequentialWorkflow} and {@link
 * ParallelWorkflow}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Workflow myWorkflow = new SomeConcreteWorkflow();
 * WorkflowContext context = new WorkflowContext();
 * WorkflowResult result = myWorkflow.execute(context);
 * }</pre>
 *
 * <p>Note: Implementations should document their behavior regarding retries, timeouts, and error
 * handling.
 */
public interface Workflow {
  /**
   * Execute the workflow with the given {@link WorkflowContext}.
   *
   * @param context the shared execution context; implementations may read and write keys to it
   * @return a {@link WorkflowResult} describing outcome, start/completion timestamps, and any error
   *     information
   */
  WorkflowResult execute(WorkflowContext context);

  /**
   * Human-friendly workflow name used in logs and monitoring. Implementations should return a
   * stable, informative name when possible.
   *
   * @return the workflow name
   */
  String getName();

  /**
   * Generates a default name when an implementation does not provide one. Usually the fully
   * qualified class name plus identity hash is sufficient for debugging.
   *
   * @return a default name for this workflow instance
   */
  default String getDefaultName() {
    return this.getClass().getName() + ":" + System.identityHashCode(this);
  }

  /**
   * Self-identifies the execution pattern (e.g., [Sequence], [Parallel]).
   *
   * @return String a descriptive string representing the execution pattern of this workflow
   */
  default String getWorkflowType() {
    return "";
  }

  /** Returns a text-based tree representation of this workflow hierarchy. */
  default String toTreeString() {
    return TreeRenderer.render(this);
  }
}
