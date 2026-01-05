package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Executes multiple tasks concurrently and waits for all to complete or timeout.
 *
 * <p><b>Purpose:</b> Enables concurrent task execution at the task level, similar to {@link
 * com.workflow.ParallelWorkflow} but for individual tasks. Useful when you want parallel execution
 * without the overhead of creating a full workflow.
 *
 * <p><b>Execution Semantics:</b>
 *
 * <ul>
 *   <li><b>Concurrency:</b> All tasks are submitted to the provided {@link ExecutorService}
 *   <li><b>Synchronization:</b> Blocks until all tasks complete or timeout is exceeded
 *   <li><b>Context Sharing:</b> All tasks share the same context instance
 *   <li><b>Execution Order:</b> Tasks run in parallel; completion order is non-deterministic
 *   <li><b>Timeout:</b> If timeoutMillis > 0, enforces maximum execution time
 * </ul>
 *
 * <p><b>Exception Aggregation:</b>
 *
 * <ul>
 *   <li>If any task throws an exception, execution continues until all tasks complete or timeout
 *   <li>All exceptions are collected and suppressed into a single TaskExecutionException
 *   <li>If no tasks fail, the method returns successfully
 *   <li>If timeout is exceeded, running tasks are canceled
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe. The shared context must be thread-safe
 * (WorkflowContext is thread-safe). All participating tasks must be thread-safe.
 *
 * <p><b>Resource Management:</b> The provided ExecutorService is NOT closed by this task; caller is
 * responsible for shutdown.
 *
 * <p><b>Timeout Handling:</b>
 *
 * <ul>
 *   <li>timeoutMillis = 0: No timeout (wait indefinitely)
 *   <li>timeoutMillis > 0: Maximum execution time in milliseconds
 *   <li>On timeout: Tasks are canceled via Future.cancel()
 *   <li>On timeout: Remaining tasks in the timeout period are still awaited
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Parallel I/O operations (read multiple files concurrently)
 *   <li>Parallel service calls (fetch from multiple APIs)
 *   <li>Parallel data processing (process multiple datasets)
 *   <li>Map-reduce style operations
 * </ul>
 *
 * <p><b>Example Usage - Parallel File Reads:</b>
 *
 * <pre>{@code
 * ExecutorService executor = Executors.newFixedThreadPool(3);
 * List<Task> tasks = List.of(
 *     new FileReadTask(Path.of("file1.txt"), "content1"),
 *     new FileReadTask(Path.of("file2.txt"), "content2"),
 *     new FileReadTask(Path.of("file3.txt"), "content3")
 * );
 * Task parallelRead = new ParallelTask(tasks, executor, 10000);  // 10 second timeout
 *
 * WorkflowContext context = new WorkflowContext();
 * parallelRead.execute(context);
 *
 * String content1 = context.get("content1");
 * String content2 = context.get("content2");
 * String content3 = context.get("content3");
 * executor.shutdown();
 * }</pre>
 *
 * <p><b>Example Usage - Parallel API Calls With Timeout:</b>
 *
 * <pre>{@code
 * ExecutorService executor = Executors.newFixedThreadPool(5);
 * List<Task> apiCalls = List.of(
 *     new GetTask("https://api1.example.com/data", "result1"),
 *     new GetTask("https://api2.example.com/data", "result2"),
 *     new GetTask("https://api3.example.com/data", "result3")
 * );
 *
 * Task parallelApis = new ParallelTask(
 *     apiCalls,
 *     executor,
 *     5000  // 5 second timeout for all APIs to complete
 * );
 *
 * try {
 *     parallelApis.execute(context);
 *     System.out.println("All API calls completed");
 * } catch (TaskExecutionException e) {
 *     System.err.println("One or more API calls failed");
 *     for (Throwable suppressed : e.getSuppressed()) {
 *         System.err.println("  - " + suppressed.getMessage());
 *     }
 * } finally {
 *     executor.shutdown();
 * }
 * }</pre>
 *
 * <p><b>Exception Handling Example:</b>
 *
 * <pre>{@code
 * // If any task fails, execution continues but exception is collected
 * List<Task> tasks = List.of(
 *     new SuccessfulTask(),      // Succeeds
 *     new FailingTask(),          // Fails but others continue
 *     new AnotherSuccessfulTask() // Completes
 * );
 *
 * Task parallelTask = new ParallelTask(tasks, executor, 5000);
 * try {
 *     parallelTask.execute(context);
 * } catch (TaskExecutionException e) {
 *     // e has one suppressed exception from FailingTask
 *     System.out.println("Failures: " + e.getSuppressed().length);
 * }
 * }</pre>
 *
 * @see Task
 * @see AbstractTask
 * @see com.workflow.ParallelWorkflow
 * @see java.util.concurrent.ExecutorService
 */
public class ParallelTask extends AbstractTask {
  private final List<Task> tasks;
  private final ExecutorService executor;
  private final long timeoutMillis; // 0 means no timeout

  /**
   * Create a ParallelTask to execute tasks concurrently.
   *
   * @param tasks tasks to execute
   * @param executor executor service used to run tasks
   * @param timeoutMillis timeout in milliseconds (0 means no timeout)
   */
  public ParallelTask(List<Task> tasks, ExecutorService executor, long timeoutMillis) {
    this.tasks = tasks;
    this.executor = executor;
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    List<Callable<Void>> callables = new ArrayList<>();
    for (Task t : tasks) {
      callables.add(
          () -> {
            t.execute(context);
            return null;
          });
    }
    try {
      List<Future<Void>> futures =
          timeoutMillis > 0
              ? executor.invokeAll(callables, timeoutMillis, TimeUnit.MILLISECONDS)
              : new ArrayList<>(executor.invokeAll(callables));
      List<Throwable> errors = new ArrayList<>();
      for (Future<Void> future : futures) {
        getResult(future, errors);
      }
      if (!errors.isEmpty()) {
        TaskExecutionException ex = new TaskExecutionException("One or more parallel tasks failed");
        errors.forEach(ex::addSuppressed);
        throw ex;
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new TaskExecutionException("Parallel execution interrupted", ie);
    }
  }

  private static void getResult(Future<Void> f, List<Throwable> errors)
      throws InterruptedException {
    try {
      f.get();
    } catch (ExecutionException ee) {
      errors.add(ee.getCause());
    } catch (CancellationException ce) {
      errors.add(ce);
    }
  }
}
