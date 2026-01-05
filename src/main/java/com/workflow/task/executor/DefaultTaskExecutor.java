package com.workflow.task.executor;

import static com.workflow.helper.FutureUtils.cancelFuture;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.exception.TaskTimeoutException;
import com.workflow.policy.RetryPolicy;
import com.workflow.policy.TimeoutPolicy;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.util.Objects;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Default task executor with built-in support for retries and timeouts.
 *
 * <p><b>Purpose:</b> Executes tasks from a {@link TaskDescriptor} with automatic retry and timeout
 * logic. Bridges the gap between task-level execution and workflow-level execution.
 *
 * <p><b>Execution Flow:</b>
 *
 * <ol>
 *   <li>Validate taskDescriptor and context are non-null
 *   <li>Extract task, retry policy, and timeout policy from descriptor
 *   <li>If timeout is configured and > 0, wrap task execution with timeout
 *   <li>Execute with retry loop (see below)
 *   <li>Return on success or throw on failure
 * </ol>
 *
 * <p><b>Retry Mechanism:</b>
 *
 * <ul>
 *   <li><b>Attempt 1:</b> Task executes immediately
 *   <li><b>On Failure:</b> Check RetryPolicy.shouldRetry(attempt, exception)
 *   <li><b>If Retry Approved:</b> Compute backoff delay from RetryPolicy.BackoffStrategy
 *   <li><b>Sleep:</b> Block thread for the computed backoff duration
 *   <li><b>Retry:</b> Execute task again at next attempt
 *   <li><b>No More Retries:</b> Throw the exception
 * </ul>
 *
 * <p><b>Timeout Mechanism:</b>
 *
 * <ul>
 *   <li>Timeout is applied to entire retry loop (not per-attempt)
 *   <li>Uses CompletableFuture.orTimeout(millis, TimeUnit) for implementation
 *   <li>Times out with TaskTimeoutException if exceeded
 *   <li>Allows partial retry attempts within timeout window
 * </ul>
 *
 * <p><b>Thread Safety:</b> This executor is thread-safe. Multiple threads can invoke execute()
 * concurrently.
 *
 * <p><b>Interrupt Handling:</b> If the executing thread is interrupted:
 *
 * <ul>
 *   <li>The interrupt status is restored
 *   <li>TaskExecutionException is thrown
 * </ul>
 *
 * <p><b>Exception Hierarchy:</b>
 *
 * <ul>
 *   <li>TaskTimeoutException: When timeout is exceeded (extends TaskExecutionException)
 *   <li>TaskExecutionException: When task fails (wrapped exception in cause)
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Resilient task execution with automatic retries
 *   <li>Timeout protection for potentially long-running tasks
 *   <li>Backoff strategies for flaky operations
 * </ul>
 *
 * <p><b>Example Usage - Simple Execution:</b>
 *
 * <pre>{@code
 * TaskExecutor executor = new DefaultTaskExecutor();
 * TaskDescriptor descriptor = TaskDescriptor.builder()
 *     .task(new FileReadTask(Path.of("data.txt"), "fileContent"))
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * executor.execute(descriptor, context);
 * String content = context.getTyped("fileContent", String.class);
 * }</pre>
 *
 * <p><b>Example Usage - With Retry and Timeout:</b>
 *
 * <pre>{@code
 * TaskDescriptor descriptor = TaskDescriptor.builder()
 *     .task(new HttpGetTask("https://api.example.com/data"))
 *     .retryPolicy(RetryPolicy.limitedRetries(3))
 *     .timeoutPolicy(TimeoutPolicy.ofMillis(10000))  // 10 second total timeout
 *     .build();
 *
 * TaskExecutor executor = new DefaultTaskExecutor();
 * try {
 *     executor.execute(descriptor, context);
 *     System.out.println("Task completed within timeout");
 * } catch (TaskTimeoutException e) {
 *     System.err.println("Task timed out after retries");
 * } catch (TaskExecutionException e) {
 *     System.err.println("Task failed: " + e.getMessage());
 * }
 * }</pre>
 *
 * <p><b>Example Usage - Custom Retry Policy:</b>
 *
 * <pre>{@code
 * RetryPolicy customRetry = new RetryPolicy() {
 *     @Override
 *     public boolean shouldRetry(int attempt, Exception error) {
 *         // Only retry on IOException, max 5 times
 *         return attempt < 5 && error instanceof IOException;
 *     }
 *
 *     @Override
 *     public BackoffStrategy backoff() {
 *         return BackoffStrategy.exponential(100);  // 100ms * 2^(attempt-1)
 *     }
 * };
 *
 * TaskDescriptor descriptor = TaskDescriptor.builder()
 *     .task(unreliableTask)
 *     .retryPolicy(customRetry)
 *     .build();
 *
 * executor.execute(descriptor, context);
 * }</pre>
 *
 * @see TaskDescriptor
 * @see RetryPolicy
 * @see TimeoutPolicy
 * @see com.workflow.exception.TaskTimeoutException
 */
@Slf4j
public final class DefaultTaskExecutor implements TaskExecutor {

  private ExecutorService executor;

  public DefaultTaskExecutor() {
    // default constructor
  }

  public DefaultTaskExecutor(ExecutorService executor) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
  }

  @Override
  public void execute(TaskDescriptor taskDescriptor, WorkflowContext workflowContext)
      throws TaskExecutionException {
    Objects.requireNonNull(taskDescriptor, "TaskDescriptor must not be null");
    Objects.requireNonNull(workflowContext, "WorkflowContext must not be null");

    Task task = taskDescriptor.getTask();
    if (task == null) {
      throw new TaskExecutionException("Task in TaskDescriptor must not be null");
    }

    CompletableFuture<Void> completableFuture = null;
    try {
      TimeoutPolicy timeout =
          taskDescriptor.getTimeoutPolicy() == null
              ? TimeoutPolicy.NONE
              : taskDescriptor.getTimeoutPolicy();

      if (timeout == TimeoutPolicy.NONE || timeout.timeoutMs() <= 0) {
        executeWithRetry(taskDescriptor, workflowContext);
        return;
      }

      if (executor != null) {
        completableFuture =
            CompletableFuture.runAsync(
                    () -> executeWithRetry(taskDescriptor, workflowContext), executor)
                .orTimeout(timeout.timeoutMs(), TimeUnit.MILLISECONDS);
      } else {
        completableFuture =
            CompletableFuture.runAsync(() -> executeWithRetry(taskDescriptor, workflowContext))
                .orTimeout(timeout.timeoutMs(), TimeUnit.MILLISECONDS);
      }
      completableFuture.get();
    } catch (InterruptedException e) {
      // The thread calling this utility was interrupted
      cancelFuture(completableFuture);
      Thread.currentThread().interrupt();
      throw new TaskExecutionException("Task execution was interrupted", e);
    } catch (ExecutionException e) {
      throw extractExecutionException(e);
    }
  }

  private static TaskExecutionException extractExecutionException(ExecutionException e) {
    Throwable cause = e.getCause();
    if (cause instanceof TimeoutException timeoutException) {
      return new TaskTimeoutException(timeoutException);
    }
    if (cause instanceof TaskExecutionException taskExecutionException) {
      return taskExecutionException;
    }
    return new TaskExecutionException(cause != null ? cause : e);
  }

  private void executeWithRetry(TaskDescriptor taskDescriptor, WorkflowContext workflowContext) {
    RetryPolicy retry = getRetryPolicy(taskDescriptor);

    Task task = taskDescriptor.getTask();
    int attempt = 0;

    while (true) {
      attempt++;
      try {
        task.execute(workflowContext);
        logSuccess(attempt, task);
        return; // Success!
      } catch (Exception e) {
        if (retry.shouldRetry(attempt, e)) {
          RetryPolicy.BackoffStrategy backoffStrategy = getBackoffStrategy(retry);
          long delay = backoffStrategy.computeDelayMs(attempt);

          log.warn(
              "Task {} failed on attempt #{}, retrying after {}ms: {}",
              task.getClass().getSimpleName(),
              attempt,
              delay,
              e.getMessage());

          sleep(delay);
        } else {
          // No more retries
          logFailure(attempt, task);
          throw e instanceof TaskExecutionException taskExecutionException
              ? taskExecutionException
              : new TaskExecutionException("Task failed: " + e.getMessage(), e);
        }
      }
    }
  }

  private static void logSuccess(int attempt, Task task) {
    if (attempt > 1) {
      log.info("Task {} succeeded on attempt #{}", task.getClass().getSimpleName(), attempt);
    }
  }

  private static void logFailure(int attempt, Task task) {
    if (attempt > 1) {
      log.error("Task {} failed after {} attempts", task.getClass().getSimpleName(), attempt);
    }
  }

  private static RetryPolicy getRetryPolicy(TaskDescriptor taskDescriptor) {
    return taskDescriptor.getRetryPolicy() == null
        ? RetryPolicy.NONE
        : taskDescriptor.getRetryPolicy();
  }

  private static RetryPolicy.BackoffStrategy getBackoffStrategy(RetryPolicy retry) {
    return retry.backoff() != null ? retry.backoff() : RetryPolicy.BackoffStrategy.NO_BACKOFF;
  }

  static void sleep(long delayMs) {
    if (delayMs <= 0) {
      return;
    }

    try {
      TimeUnit.MILLISECONDS.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TaskExecutionException("Sleep interrupted during retry backoff", e);
    }
  }
}
