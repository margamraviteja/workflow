package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;

/**
 * Introduces a delay by blocking the current thread for a specified duration.
 *
 * <p><b>Purpose:</b> Introduces deliberate pauses in workflow execution. Useful for rate limiting,
 * pacing requests, waiting for external processes to complete, or testing timeout behaviors.
 *
 * <p><b>Blocking Behavior:</b>
 *
 * <ul>
 *   <li><b>Thread Blocking:</b> Blocks the executing thread for the entire specified duration
 *   <li><b>No Context Mutation:</b> Does not read or modify the workflow context
 *   <li><b>Interruptible:</b> Respects thread interruption; re-interrupts thread on
 *       InterruptedException
 * </ul>
 *
 * <p><b>Exception Handling:</b> If the thread is interrupted while sleeping, this task:
 *
 * <ol>
 *   <li>Calls Thread.currentThread().interrupt() to restore interrupt status
 *   <li>Throws TaskExecutionException wrapping the InterruptedException
 * </ol>
 *
 * <p><b>Performance Implications:</b> Since this task blocks the executing thread, using it in a
 * parallel workflow will block that execution thread and potentially reduce parallelism.
 *
 * <p><b>Thread Safety:</b> This task is thread-safe. Multiple threads can safely execute delay
 * tasks concurrently.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Rate limiting (space out requests to external services)
 *   <li>Retry backoff (wait before retrying failed operations)
 *   <li>Polling wait (wait before checking external status)
 *   <li>Test setup (simulate slow operations during testing)
 *   <li>Workflow pacing (ensure minimum time between stages)
 * </ul>
 *
 * <p><b>Example Usage - Basic Delay:</b>
 *
 * <pre>{@code
 * // Introduce a 2-second delay
 * Task delayTask = new DelayTask(2000);
 * WorkflowContext context = new WorkflowContext();
 * long start = System.currentTimeMillis();
 * delayTask.execute(context);
 * long elapsed = System.currentTimeMillis() - start;
 * System.out.println("Elapsed: " + elapsed + "ms (expected ~2000ms)");
 * }</pre>
 *
 * <p><b>Example Usage - Rate Limiting in Sequential Workflow:</b>
 *
 * <pre>{@code
 * // Process items with delays between requests
 * SequentialWorkflow rateLimitedPipeline = SequentialWorkflow.builder()
 *     .name("RateLimitedApi")
 *     .task(new HttpPostTask("https://api.example.com/request1", "input", "result1"))
 *     .task(new DelayTask(1000))  // 1 second rate limiting
 *     .task(new HttpPostTask("https://api.example.com/request2", "input", "result2"))
 *     .task(new DelayTask(1000))  // 1 second rate limiting
 *     .task(new HttpPostTask("https://api.example.com/request3", "input", "result3"))
 *     .build();
 *
 * WorkflowResult result = rateLimitedPipeline.execute(context);
 * }</pre>
 *
 * @see Task
 * @see AbstractTask
 */
public class DelayTask extends AbstractTask {
  private final long millis;

  /**
   * Create a delay task that sleeps the current thread for the given duration.
   *
   * @param millis delay duration in milliseconds
   */
  public DelayTask(long millis) {
    this.millis = millis;
  }

  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TaskExecutionException("Delay interrupted", e);
    }
  }
}
