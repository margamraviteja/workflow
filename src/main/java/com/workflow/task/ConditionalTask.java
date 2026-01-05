package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.util.function.Predicate;

/**
 * Conditionally executes an inner task based on a predicate evaluated against the workflow context.
 *
 * <p><b>Purpose:</b> Allows optional task execution within a workflow. The inner task is executed
 * only if the condition predicate evaluates to true. This enables branching logic at the task
 * level.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Condition Test:</b> The predicate is evaluated against the current context
 *   <li><b>True Branch:</b> If condition is true, the inner task is executed immediately
 *   <li><b>False Branch:</b> If condition is false, the inner task is skipped (no-op)
 *   <li><b>Exception Propagation:</b> If the predicate throws an exception, it is propagated as
 *       TaskExecutionException
 *   <li><b>Inner Task Exception:</b> If the inner task throws an exception, it is propagated to the
 *       caller
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe if both the predicate and inner task are
 * thread-safe.
 *
 * <p><b>Context Mutation:</b> The inner task may read and modify the context if the condition
 * evaluates to true. The predicate should not modify context (read-only recommended).
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Feature toggles (execute feature code only if enabled)
 *   <li>Optional enrichment (add extra data only for certain contexts)
 *   <li>Conditional logging (detailed logging only in debug mode)
 *   <li>Conditional cleanup (cleanup only if setup was successful)
 * </ul>
 *
 * <p><b>Example Usage - Feature Toggle:</b>
 *
 * <pre>{@code
 * // Only execute premium feature logic if user is premium
 * Task premiumFeature = new PremiumFeatureTask();
 * Task conditionalTask = new ConditionalTask(
 *     ctx -> Boolean.TRUE.equals(ctx.get("isPremiumUser")),
 *     premiumFeature
 * );
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("isPremiumUser", true);
 * context.put("userData", userData);
 *
 * conditionalTask.execute(context);
 * }</pre>
 *
 * <p><b>Example Usage - Optional Enrichment:</b>
 *
 * <pre>{@code
 * // Enrich data with additional information only if detailed mode is enabled
 * Task enrichmentTask = new DataEnrichmentTask();
 * Task optionalEnrichment = new ConditionalTask(
 *     ctx -> "detailed".equals(ctx.get("processingMode")),
 *     enrichmentTask
 * );
 *
 * // Even if enrichment is skipped, execution continues successfully
 * SequentialWorkflow workflow = SequentialWorkflow.builder()
 *     .task(new DataParsingTask())
 *     .task(optionalEnrichment)
 *     .task(new DataValidationTask())
 *     .build();
 * }</pre>
 *
 * @see Task
 * @see AbstractTask
 * @see com.workflow.ConditionalWorkflow
 */
public class ConditionalTask extends AbstractTask {
  private final Predicate<WorkflowContext> condition;
  private final Task inner;

  /**
   * Create a conditional wrapper that executes the inner task when the condition is true.
   *
   * @param condition predicate evaluated against the workflow context
   * @param inner the task to execute when the condition evaluates to true
   */
  public ConditionalTask(Predicate<WorkflowContext> condition, Task inner) {
    this.condition = condition;
    this.inner = inner;
  }

  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    if (condition.test(context)) {
      inner.execute(context);
    }
  }

  @Override
  public String getName() {
    return "ConditionalTask(" + inner.getName() + ")";
  }
}
