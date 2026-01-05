package com.workflow.task;

import com.workflow.context.WorkflowContext;

/**
 * A task that performs no operations when executed.
 *
 * <p><b>Purpose:</b> Acts as a placeholder or null-object pattern for scenarios where a task is
 * required structurally but no actual work needs to be performed. Useful for:
 *
 * <ul>
 *   <li>Representing optional or conditional branches where sometimes nothing is needed
 *   <li>Placeholder during development before actual task implementation
 *   <li>Testing and validation without side effects
 *   <li>Default fallback when no concrete task is available
 * </ul>
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Context Mutation:</b> Does not read or modify the workflow context
 *   <li><b>Side Effects:</b> No side effects whatsoever
 *   <li><b>Duration:</b> Completes instantly with minimal overhead
 *   <li><b>Exception:</b> Never throws exceptions
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe (stateless).
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Default tasks in switch/branching logic
 *   <li>Placeholder in dynamic task lists
 *   <li>Testing task composition without side effects
 *   <li>Documentation of intent ("this branch intentionally does nothing")
 *   <li>Simplifying conditional logic in workflows
 * </ul>
 *
 * <p><b>Example Usage - Optional Task With Fallback:</b>
 *
 * <pre>{@code
 * Task actualTask = new DataProcessingTask();
 * Task optionalTask = new ConditionalTask(
 *     ctx -> Boolean.TRUE.equals(ctx.get("isEnabled")),
 *     actualTask
 * );
 * // If condition is false, the task completes silently with no-op behavior
 * }</pre>
 *
 * <p><b>Example Usage - Default in Switch Logic:</b>
 *
 * <pre>{@code
 * SwitchTask switchTask = SwitchTask.builder()
 *     .switchKey("processingType")
 *     .cases(Map.of(
 *         "standard", new StandardProcessTask(),
 *         "premium", new PremiumProcessTask()
 *     ))
 *     .defaultTask(new NoOpTask())  // Do nothing for unrecognized types
 *     .build();
 * }</pre>
 *
 * <p><b>Example Usage - Testing Workflow Composition:</b>
 *
 * <pre>{@code
 * // Test that a workflow composes correctly without side effects
 * SequentialWorkflow testWorkflow = SequentialWorkflow.builder()
 *     .task(new NoOpTask())
 *     .task(new NoOpTask())
 *     .task(new NoOpTask())
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * WorkflowResult result = testWorkflow.execute(context);
 * assert result.getStatus() == WorkflowStatus.SUCCESS;
 * }</pre>
 *
 * @see Task
 * @see AbstractTask
 */
public class NoOpTask extends AbstractTask {
  @Override
  protected void doExecute(WorkflowContext context) {
    // intentionally empty
  }
}
