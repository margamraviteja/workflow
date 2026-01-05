package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.util.List;

/**
 * Executes a sequence of tasks in order, similar to {@link com.workflow.SequentialWorkflow} but at
 * the task level.
 *
 * <p><b>Purpose:</b> Allows composing multiple tasks into a single task unit. This is useful when
 * you want to group related tasks together and treat them as an atomic unit within a larger
 * workflow or within another composite structure.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Order:</b> Tasks execute in the exact order they were provided in the list
 *   <li><b>Short-circuit:</b> Stops immediately at the first task that throws an exception
 *   <li><b>Context Sharing:</b> All tasks share the same context instance
 *   <li><b>Empty List:</b> If the task list is empty, execution completes successfully with no
 *       operations
 * </ul>
 *
 * <p><b>Exception Handling:</b> Any {@link com.workflow.exception.TaskExecutionException} thrown by
 * a task in the sequence is immediately propagated. Subsequent tasks are not executed.
 *
 * <p><b>Thread Safety:</b> This task is thread-safe. However, all tasks in the sequence must also
 * be thread-safe if executed in a parallel context.
 *
 * <p><b>Context Mutation:</b> Each task in the sequence may read and write context keys. Earlier
 * tasks may establish state that later tasks depend on.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Grouping validation steps (validate input → validate state → validate constraints)
 *   <li>Setup workflows (initialize → configure → verify)
 *   <li>Cleanup sequences (flush buffers → close connections → cleanup resources)
 *   <li>Multi-stage transformations (parse → transform → validate)
 * </ul>
 *
 * <p><b>Example Usage - Validation Pipeline:</b>
 *
 * <pre>{@code
 * List<Task> validationTasks = List.of(
 *     new SchemaValidationTask(),        // Validate structure
 *     new BusinessRuleValidationTask(),  // Validate business rules
 *     new PermissionCheckTask()          // Validate permissions
 * );
 * Task compositeValidator = new CompositeTask(validationTasks);
 *
 * WorkflowContext context = new WorkflowContext();
 * context.put("data", inputData);
 * try {
 *     compositeValidator.execute(context);
 *     System.out.println("All validations passed");
 * } catch (TaskExecutionException e) {
 *     System.err.println("Validation failed: " + e.getMessage());
 * }
 * }</pre>
 *
 * <p><b>Example Usage - In Sequential Workflow:</b>
 *
 * <pre>{@code
 * // Create a composite setup task
 * CompositeTask setupPhase = new CompositeTask(List.of(
 *     new CreateConnectionTask(),
 *     new LoadConfigurationTask(),
 *     new WarmupCacheTask()
 * ));
 *
 * // Use in a larger workflow
 * SequentialWorkflow workflow = SequentialWorkflow.builder()
 *     .task(setupPhase)  // Setup phase
 *     .task(new ProcessDataTask())
 *     .task(new PersistResultsTask())
 *     .build();
 * }</pre>
 *
 * @see Task
 * @see AbstractTask
 * @see com.workflow.SequentialWorkflow
 */
public class CompositeTask extends AbstractTask {
  private final List<Task> tasks;

  /**
   * Create a composite task that executes the provided list of tasks in order.
   *
   * @param tasks the tasks to execute in sequence
   */
  public CompositeTask(List<Task> tasks) {
    this.tasks = tasks;
  }

  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    for (Task t : tasks) {
      t.execute(context);
    }
  }

  @Override
  public String getName() {
    return "CompositeTask[" + tasks.size() + "]";
  }
}
