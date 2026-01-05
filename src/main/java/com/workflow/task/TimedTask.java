package com.workflow.task;

import com.workflow.context.WorkflowContext;
import lombok.Builder;

/**
 * Wraps another task and measures its execution time, storing the duration in the workflow context.
 *
 * <p><b>Purpose:</b> Enables performance monitoring by automatically recording how long a task
 * takes to execute. The measured duration is stored in the context for later inspection,
 * aggregation, or alerting.
 *
 * <p><b>Timing Measurement:</b>
 *
 * <ul>
 *   <li><b>Granularity:</b> Uses nanosecond precision (System.nanoTime()) for high accuracy
 *   <li><b>Measurement:</b> Records time from before task.execute() to after it returns
 *   <li><b>Storage:</b> Duration in milliseconds is stored in the context at the configured key
 *   <li><b>Exception Behavior:</b> If delegate task throws exception, the exception is propagated
 *       and timing is NOT recorded
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe if the delegate task is thread-safe. Note that
 * nanosecond timers may not be reliable across threads in all JVM implementations.
 *
 * <p><b>Context Mutation:</b> Writes a single long value (duration in milliseconds) to the
 * configured {@code durationKey} in the context. Does not modify other context values unless the
 * delegate task does.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Performance profiling of individual tasks within workflows
 *   <li>SLA monitoring (track if tasks complete within expected time)
 *   <li>Resource cost calculation (charge based on execution time)
 *   <li>Workflow optimization (identify slowest stages)
 * </ul>
 *
 * <p><b>Example Usage - Basic Timing:</b>
 *
 * <pre>{@code
 * Task fileReadTask = new FileReadTask(Path.of("/large-file.txt"), "fileContent");
 * TimedTask timedTask = TimedTask.builder()
 *     .delegate(fileReadTask)
 *     .durationKey("readDurationMs")
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * timedTask.execute(context);
 *
 * long durationMs = context.getTyped("readDurationMs", Long.class);
 * System.out.println("File read completed in " + durationMs + "ms");
 * }</pre>
 *
 * <p><b>Example Usage - Performance Monitoring in Sequential Workflow:</b>
 *
 * <pre>{@code
 * SequentialWorkflow workflow = SequentialWorkflow.builder()
 *     .name("DataProcessingPipeline")
 *     .task(TimedTask.builder()
 *         .delegate(new ValidationTask())
 *         .durationKey("validationTimeMs")
 *         .build())
 *     .task(TimedTask.builder()
 *         .delegate(new TransformationTask())
 *         .durationKey("transformationTimeMs")
 *         .build())
 *     .task(TimedTask.builder()
 *         .delegate(new PersistenceTask())
 *         .durationKey("persistenceTimeMs")
 *         .build())
 *     .build();
 *
 * WorkflowResult result = workflow.execute(context);
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     long validation = context.getTyped("validationTimeMs", Long.class);
 *     long transformation = context.getTyped("transformationTimeMs", Long.class);
 *     long persistence = context.getTyped("persistenceTimeMs", Long.class);
 *     System.out.println("Pipeline: validation=" + validation + "ms, " +
 *                       "transformation=" + transformation + "ms, " +
 *                       "persistence=" + persistence + "ms");
 * }
 * }</pre>
 *
 * @see Task
 * @see AbstractTask
 */
@Builder
public class TimedTask extends AbstractTask {
  private final Task delegate;
  private final String durationKey;

  @Override
  protected void doExecute(WorkflowContext context) {
    long start = System.nanoTime();
    delegate.execute(context);
    long durationMs = (System.nanoTime() - start) / 1_000_000;
    context.put(durationKey, durationMs);
  }

  @Override
  public String getName() {
    return "TimedTask(" + delegate.getName() + ")";
  }
}
