package com.workflow.task.executor;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.task.TaskDescriptor;

/**
 * Responsible for executing a task within a workflow context.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * TaskExecutor executor = new ConcreteTaskExecutor();
 * TaskDescriptor taskDescriptor = new TaskDescriptor("taskName", taskParameters);
 * WorkflowContext context = new WorkflowContext();
 * executor.execute(taskDescriptor, context);
 * }</pre>
 *
 * <p>Note: Implementations of this interface should handle any exceptions that may occur during
 * task execution and throw a TaskExecutionException if necessary.
 */
public interface TaskExecutor {

  /**
   * Execute the provided task within the given workflow context.
   *
   * @param taskDescriptor task descriptor
   * @param workflowContext workflow context
   */
  void execute(TaskDescriptor taskDescriptor, WorkflowContext workflowContext)
      throws TaskExecutionException;
}
