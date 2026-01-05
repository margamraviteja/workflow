package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.policy.RetryPolicy;
import com.workflow.sleeper.Sleeper;
import java.time.Duration;
import java.util.Objects;

/**
 * A decorator that adds retry capabilities to any {@link Task} using a pluggable {@link
 * RetryPolicy}.
 *
 * <p><b>Purpose:</b> This class provides a robust mechanism for handling transient failures. By
 * separating the execution loop from the retry logic, it allows for highly customizable resilience
 * patterns including exponential backoff, jittered delays, and exception-specific retries.
 *
 * <p><b>Key Responsibilities:</b>
 *
 * <ul>
 *   <li><b>Execution Loop:</b> Orchestrates the repeated execution of the delegate task.
 *   <li><b>Policy Delegation:</b> Consults a {@link RetryPolicy} to decide if a failure warrants a
 *       retry and how long to wait.
 *   <li><b>Interruption Handling:</b> Safely manages thread interruptions during backoff periods.
 * </ul>
 *
 * <p><b>Example Usage - Selective Retries with Jitter:</b>
 *
 * <pre>{@code
 * Task apiTask = new HttpPostTask("https://api.service.com/v1/resource");
 * * // Only retry on IO and Timeout exceptions, up to 5 times, with jittered exponential backoff
 * RetryPolicy policy = RetryPolicy.limitedRetriesWithBackoff(
 * 5,
 * RetryPolicy.BackoffStrategy.exponentialWithJitter(100, 5000),
 * IOException.class,
 * TimeoutException.class
 * );
 * * Task resilientTask = new RetryingTask(apiTask, policy);
 * resilientTask.execute(context);
 * }</pre>
 *
 * <p><b>Example Usage - Simple Fixed Delay:</b>
 *
 * <pre>{@code
 * // Retry 3 times with a constant 1-second delay between attempts
 * Task simpleTask = new RetryingTask(
 * new DatabaseUpdateTask(),
 * RetryPolicy.fixedBackoff(3, 1000)
 * );
 * }</pre>
 *
 * @see RetryPolicy
 * @see RetryPolicy.BackoffStrategy
 * @see Task
 */
public final class RetryingTask extends AbstractTask {

  private final Task delegate;
  private final RetryPolicy retryPolicy;
  private final Sleeper sleeper;

  public RetryingTask(Task delegate, RetryPolicy retryPolicy) {
    this(delegate, retryPolicy, Thread::sleep);
  }

  RetryingTask(Task delegate, RetryPolicy retryPolicy, Sleeper sleeper) {
    this.delegate = Objects.requireNonNull(delegate, "delegate is required");
    this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy is required");
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper is required");
  }

  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    int attempt = 1;

    while (true) {
      try {
        delegate.execute(context);
        return;
      } catch (Exception e) {
        handleRetry(attempt, e);
        attempt++;
      }
    }
  }

  /**
   * Evaluates the policy and handles the backoff sleep. Extracted to reduce Cognitive Complexity.
   */
  private void handleRetry(int attempt, Exception e) throws TaskExecutionException {
    if (!retryPolicy.shouldRetry(attempt, e)) {
      throw wrapException(e, attempt);
    }

    long delayMs = retryPolicy.backoff().computeDelayMs(attempt);
    if (delayMs > 0) {
      performBackoff(delayMs, attempt);
    }
  }

  private void performBackoff(long delayMs, int attempt) throws TaskExecutionException {
    try {
      sleeper.sleep(Duration.ofMillis(delayMs));
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new TaskExecutionException("Retry sequence interrupted at attempt " + attempt, ie);
    }
  }

  private TaskExecutionException wrapException(Exception e, int attempt) {
    if (e instanceof TaskExecutionException taskExecutionException) {
      return taskExecutionException;
    }
    return new TaskExecutionException(
        "Retry policy exhausted or rejected exception at attempt " + attempt, e);
  }

  @Override
  public String getName() {
    return "RetryingTask(" + delegate.getName() + ")";
  }
}
