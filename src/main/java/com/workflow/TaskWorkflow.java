package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import com.workflow.task.executor.DefaultTaskExecutor;
import com.workflow.task.executor.TaskExecutor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps a {@link Task} to make it executable as a {@link Workflow} with support for retry and
 * timeout policies.
 *
 * <p><b>Purpose:</b> Bridges the gap between individual tasks and workflow orchestration. A
 * TaskWorkflow allows a single task to be composed into larger workflows while applying
 * cross-cutting concerns like retries, timeouts, and logging.
 *
 * <p><b>Execution Flow:</b>
 *
 * <ol>
 *   <li>Validates the task and task descriptor are non-null
 *   <li>Delegates to {@link TaskExecutor} to execute the task
 *   <li>TaskExecutor applies retry policy if execution fails
 *   <li>TaskExecutor applies timeout policy if configured
 *   <li>Returns success result if task completes successfully
 *   <li>Returns failure result if task fails after all retries exhausted
 * </ol>
 *
 * <p><b>Retry Policy:</b> If task execution throws an exception, the configured {@link
 * com.workflow.policy.RetryPolicy} determines whether to retry. Retries use exponential backoff or
 * other configured strategies.
 *
 * <p><b>Timeout Policy:</b> If configured with a positive timeout via {@link
 * com.workflow.policy.TimeoutPolicy}, the task execution is wrapped with a timeout. If the task
 * does not complete within the timeout, a {@link com.workflow.exception.TaskTimeoutException} is
 * thrown.
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe. The underlying task must also be
 * thread-safe if used in parallel workflows.
 *
 * <p><b>Context Mutation:</b> The task may read and write keys in the shared {@link
 * WorkflowContext}. Refer to the specific task implementation for details on which keys are
 * accessed.
 *
 * <p><b>Common Use Cases:</b>
 *
 * <ul>
 *   <li>Wrapping I/O tasks (file read/write, HTTP calls) with timeout protection
 *   <li>Applying retry policies to flaky external service calls
 *   <li>Creating reusable workflow components from existing task implementations
 *   <li>Combining multiple tasks into sequential or parallel workflows
 * </ul>
 *
 * <p><b>Example Usage - Simple Task Wrapper:</b>
 *
 * <pre>{@code
 * // Wrap a simple task as a workflow
 * Task readTask = new FileReadTask(Path.of("/data.txt"), "fileContent");
 * Workflow readWorkflow = new TaskWorkflow(readTask);
 *
 * WorkflowContext context = new WorkflowContext();
 * WorkflowResult result = readWorkflow.execute(context);
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     String content = context.get("fileContent");
 * }
 * }</pre>
 *
 * <p><b>Example Usage - Task with Retry and Timeout:</b>
 *
 * <pre>{@code
 * Task httpTask = new PostTask("https://api.example.com/data", "requestBody", "response");
 * TaskDescriptor descriptor = TaskDescriptor.builder()
 *     .task(httpTask)
 *     .name("ReliableHttpCall")
 *     .retryPolicy(RetryPolicy.limitedRetries(3))  // Retry up to 3 times
 *     .timeoutPolicy(TimeoutPolicy.ofMillis(5000))  // 5 second timeout
 *     .build();
 *
 * Workflow taskWorkflow = new TaskWorkflow(descriptor);
 * WorkflowResult result = taskWorkflow.execute(context);
 * }</pre>
 *
 * <p><b>Example Usage - Custom Executor:</b>
 *
 * <pre>{@code
 * TaskExecutor customExecutor = new CustomTaskExecutor();  // Custom implementation
 * Workflow taskWorkflow = new TaskWorkflow(descriptor, customExecutor);
 * }</pre>
 *
 * @see Task
 * @see TaskDescriptor
 * @see TaskExecutor
 * @see com.workflow.policy.RetryPolicy
 * @see com.workflow.policy.TimeoutPolicy
 */
@Slf4j
public class TaskWorkflow extends AbstractWorkflow {
  private final TaskDescriptor taskDescriptor;
  private final TaskExecutor taskExecutor;

  /**
   * Internal constructor used by the Builder.
   *
   * @param builder the builder instance containing configuration
   */
  private TaskWorkflow(TaskWorkflowBuilder builder) {
    this.taskDescriptor = builder.taskDescriptor;
    this.taskExecutor = builder.taskExecutor;
  }

  /**
   * Construct a TaskWorkflow that wraps a single Task instance.
   *
   * @param task the task to wrap as a workflow
   */
  public TaskWorkflow(Task task) {
    this(TaskDescriptor.builder().task(task).build());
  }

  /**
   * Construct a TaskWorkflow from a TaskDescriptor.
   *
   * @param taskDescriptor the descriptor containing the task and policies
   */
  public TaskWorkflow(TaskDescriptor taskDescriptor) {
    this(taskDescriptor, new DefaultTaskExecutor());
  }

  /**
   * Construct a TaskWorkflow with a custom TaskExecutor.
   *
   * @param taskDescriptor the descriptor containing the task and policies
   * @param taskExecutor the executor used to run the task
   */
  public TaskWorkflow(TaskDescriptor taskDescriptor, TaskExecutor taskExecutor) {
    ValidationUtils.requireNonNull(taskDescriptor, "taskDescriptor");
    ValidationUtils.requireNonNull(taskDescriptor.getTask(), "task");
    ValidationUtils.requireNonNull(taskExecutor, "taskExecutor");
    this(builder().taskDescriptor(taskDescriptor).taskExecutor(taskExecutor));
  }

  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext executionContext) {
    taskExecutor.execute(taskDescriptor, context);
    return executionContext.success();
  }

  @Override
  public String getName() {
    String name = taskDescriptor.getName();
    if (name == null || name.isBlank()) {
      name = taskDescriptor.getTask().getName();
    }
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatTaskType("Task");
  }

  /**
   * Static factory method to create a new builder instance.
   *
   * @return a new TaskWorkflowBuilder
   */
  public static TaskWorkflowBuilder builder() {
    return new TaskWorkflowBuilder();
  }

  /**
   * Builder for {@link TaskWorkflow}. *
   *
   * <p>Provides a fluent API to construct a task-based workflow with optional custom executors.
   */
  public static class TaskWorkflowBuilder {
    private TaskDescriptor taskDescriptor;
    private TaskExecutor taskExecutor = new DefaultTaskExecutor();

    /**
     * Sets the task descriptor containing the task logic and associated policies.
     *
     * @param taskDescriptor the descriptor; must not be null
     * @return this builder
     */
    public TaskWorkflowBuilder taskDescriptor(TaskDescriptor taskDescriptor) {
      this.taskDescriptor = taskDescriptor;
      return this;
    }

    /**
     * Convenience method to set the task directly. This will wrap the task in a default {@link
     * TaskDescriptor}.
     *
     * @param task the task to execute; must not be null
     * @return this builder
     */
    public TaskWorkflowBuilder task(Task task) {
      this.taskDescriptor = TaskDescriptor.builder().task(task).build();
      return this;
    }

    /**
     * Sets a custom task executor for running the task.
     *
     * @param taskExecutor the executor to use; defaults to {@link DefaultTaskExecutor}
     * @return this builder
     */
    public TaskWorkflowBuilder taskExecutor(TaskExecutor taskExecutor) {
      this.taskExecutor = taskExecutor;
      return this;
    }

    /**
     * Builds and returns a configured {@link TaskWorkflow}.
     *
     * @return a new TaskWorkflow instance
     * @throws NullPointerException if taskDescriptor (or the task within it) or taskExecutor is
     *     null
     */
    public TaskWorkflow build() {
      ValidationUtils.requireNonNull(taskDescriptor, "taskDescriptor");
      ValidationUtils.requireNonNull(taskDescriptor.getTask(), "task");
      ValidationUtils.requireNonNull(taskExecutor, "taskExecutor");
      return new TaskWorkflow(this);
    }
  }
}
