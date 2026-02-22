package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;

/**
 * Represents an atomic unit of work that can be executed within a {@link WorkflowContext}.
 *
 * <p><b>Purpose:</b> Tasks are short-lived, focused units of work typically representing a single
 * responsibility (e.g., read a file, make an HTTP request, transform data, run a shell command).
 * They are the basic building blocks of {@link com.workflow.Workflow workflows}.
 *
 * <p><b>Lifecycle:</b>
 *
 * <ol>
 *   <li>Create task instance (typically via builder)
 *   <li>Call {@link #execute(WorkflowContext)} with shared context
 *   <li>Task reads inputs from context as needed
 *   <li>Task executes its work (potentially modifying context state)
 *   <li>Task completes or throws {@link TaskExecutionException}
 * </ol>
 *
 * <p><b>Context Mutation:</b> Implementations should clearly document:
 *
 * <ul>
 *   <li>What context keys they read
 *   <li>What context keys they write/modify
 *   <li>Any side effects (file I/O, network calls, etc.)
 * </ul>
 *
 * <p><b>Exception Handling:</b> Implementations should throw {@link TaskExecutionException} for
 * execution failures. This ensures consistent error handling across the workflow framework.
 *
 * <p><b>Thread Safety:</b> Implementations should be thread-safe if used in parallel workflows. The
 * context object may be accessed by multiple threads simultaneously.
 *
 * <p><b>Example Implementation:</b>
 *
 * <pre>{@code
 * public class TransformTask extends AbstractTask {
 *     @Override
 *     protected void doExecute(WorkflowContext context) throws TaskExecutionException {
 *         String input = context.getTyped("input", String.class);
 *         String output = input.toUpperCase();
 *         context.put("output", output);
 *     }
 * }
 * }</pre>
 *
 * @see AbstractTask
 * @see TaskExecutionException
 * @see WorkflowContext
 * @see TaskDescriptor
 */
@FunctionalInterface
public interface Task {
  /**
   * Execute the task with the provided {@link WorkflowContext}. Implementations may throw a {@link
   * TaskExecutionException} to communicate execution failure to callers.
   *
   * @param context the shared workflow context (maybe null in some tests; callers should document
   *     their expectations)
   * @throws TaskExecutionException when execution fails
   */
  void execute(WorkflowContext context) throws TaskExecutionException;

  /**
   * Returns a human-friendly task name for logging. Default implementation returns the
   * implementation class name plus the identity hash.
   *
   * @return the task name
   */
  default String getName() {
    return this.getClass().getName() + ":" + System.identityHashCode(this);
  }
}
