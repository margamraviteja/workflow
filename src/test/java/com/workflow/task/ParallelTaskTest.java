package com.workflow.task;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ParallelTaskTest {

  private ExecutorService executor;

  @AfterEach
  void tearDown() {
    if (executor != null && !executor.isShutdown()) {
      executor.shutdownNow();
    }
  }

  @Test
  void execute_allTasksSucceed_noTimeout_completesNormally() {
    executor = Executors.newFixedThreadPool(3);
    WorkflowContext ctx = mock(WorkflowContext.class);

    AtomicBoolean a = new AtomicBoolean(false);
    AtomicBoolean b = new AtomicBoolean(false);
    AtomicBoolean c = new AtomicBoolean(false);

    Task t1 = _ -> a.set(true);
    Task t2 = _ -> b.set(true);
    Task t3 = _ -> c.set(true);

    ParallelTask pt = new ParallelTask(Arrays.asList(t1, t2, t3), executor, 0);

    // Should not throw
    pt.execute(ctx);

    assertTrue(a.get());
    assertTrue(b.get());
    assertTrue(c.get());
  }

  @Test
  void execute_someTasksThrow_exceptionsAggregatedAndThrown() {
    executor = Executors.newFixedThreadPool(3);
    WorkflowContext ctx = mock(WorkflowContext.class);

    Task ok =
        _ -> {
          /* no-op */
        };
    Task fail1 =
        _ -> {
          throw new TaskExecutionException("task1 failed");
        };
    Task fail2 =
        _ -> {
          throw new RuntimeException("task2 runtime");
        };

    ParallelTask pt = new ParallelTask(Arrays.asList(ok, fail1, fail2), executor, 0);

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> pt.execute(ctx));
    assertEquals("One or more parallel tasks failed", ex.getMessage());

    // suppressed should contain the two causes (order not guaranteed)
    Throwable[] suppressed = ex.getSuppressed();
    assertEquals(2, suppressed.length);

    boolean foundTaskExec = false;
    boolean foundRuntime = false;
    for (Throwable s : suppressed) {
      if (s instanceof TaskExecutionException && "task1 failed".equals(s.getMessage())) {
        foundTaskExec = true;
      } else if (s instanceof RuntimeException && "task2 runtime".equals(s.getMessage())) {
        foundRuntime = true;
      }
    }
    assertTrue(foundTaskExec, "Expected TaskExecutionException cause to be suppressed");
    assertTrue(foundRuntime, "Expected RuntimeException cause to be suppressed");
  }

  @Test
  void execute_withTimeout_longRunningTasksCancelled_andCancellationReported() {
    // small pool; tasks will sleep longer than timeout
    executor = Executors.newFixedThreadPool(3);
    WorkflowContext ctx = mock(WorkflowContext.class);

    ParallelTask pt = getParallelTask();

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> pt.execute(ctx));
    assertEquals("One or more parallel tasks failed", ex.getMessage());

    // suppressed should contain CancellationException or causes resulting from interruption
    Throwable[] suppressed = ex.getSuppressed();
    assertTrue(
        suppressed.length >= 1,
        "Expected at least one suppressed exception due to timeout/cancellation");

    boolean hasCancellation = false;
    for (Throwable s : suppressed) {
      if (s instanceof CancellationException
          || (s instanceof RuntimeException && s.getCause() instanceof InterruptedException)) {
        hasCancellation = true;
      }
    }
    assertTrue(
        hasCancellation,
        "Expected a CancellationException or interrupted cause among suppressed exceptions");
  }

  private @NonNull ParallelTask getParallelTask() {
    Task longTask1 =
        _ -> {
          try {
            await().atLeast(200, TimeUnit.MILLISECONDS).until(() -> true);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };
    Task longTask2 =
        _ -> {
          try {
            await().atLeast(200, TimeUnit.MILLISECONDS).until(() -> true);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };

    // timeout 50ms -> tasks will be canceled by invokeAll
    return new ParallelTask(Arrays.asList(longTask1, longTask2), executor, 50);
  }

  @Test
  void execute_invokeAllThrowsInterruptedException_threadReInterruptedAndWrapped()
      throws Exception {
    // Use a mock executor that throws InterruptedException from invokeAll
    ExecutorService mockExecutor = mock(ExecutorService.class);
    executor = mockExecutor; // so tearDown won't try to shut down a real executor

    WorkflowContext ctx = mock(WorkflowContext.class);

    // Prepare a dummy list to match signature; we don't care about the argument here
    when(mockExecutor.invokeAll(anyCollection()))
        .thenThrow(new InterruptedException("simulated interrupt"));
    when(mockExecutor.invokeAll(anyCollection(), anyLong(), any(TimeUnit.class)))
        .thenThrow(new InterruptedException("simulated interrupt"));

    ParallelTask pt = new ParallelTask(Collections.emptyList(), mockExecutor, 0);

    // Ensure interrupted flag is clear before call
    assertFalse(Thread.currentThread().isInterrupted());

    TaskExecutionException ex = assertThrows(TaskExecutionException.class, () -> pt.execute(ctx));
    assertEquals("Parallel execution interrupted", ex.getMessage());
    assertNotNull(ex.getCause());
    assertInstanceOf(InterruptedException.class, ex.getCause());

    // The implementation should re-interrupt the current thread
    assertTrue(
        Thread.currentThread().isInterrupted(),
        "Thread should be re-interrupted after InterruptedException");
  }

  @Test
  void execute_emptyTaskList_completesNormally() {
    executor = Executors.newSingleThreadExecutor();
    WorkflowContext ctx = mock(WorkflowContext.class);

    ParallelTask pt = new ParallelTask(Collections.emptyList(), executor, 0);

    // Should not throw
    pt.execute(ctx);
    assertTrue(true);
  }

  @Test
  void getName_reflectsNumberOfTasks() {
    ExecutorService dummy = Executors.newSingleThreadExecutor();
    try {
      ParallelTask pt0 = new ParallelTask(Collections.emptyList(), dummy, 0);
      assertNotNull(pt0.getName());
    } finally {
      dummy.shutdownNow();
    }
  }

  @Test
  void execute_whenExecutorThrowsRuntimeException_propagatesRuntimeException()
      throws InterruptedException {
    ExecutorService mockExecutor = mock(ExecutorService.class);
    executor = mockExecutor;

    WorkflowContext ctx = mock(WorkflowContext.class);

    // Make invokeAll throw a runtime exception (unchecked)
    when(mockExecutor.invokeAll(anyCollection()))
        .thenThrow(new RuntimeException("invokeAll failed"));

    ParallelTask pt = new ParallelTask(Collections.singletonList(_ -> {}), mockExecutor, 0);

    RuntimeException ex = assertThrows(RuntimeException.class, () -> pt.execute(ctx));
    assertEquals("Task failed: invokeAll failed", ex.getMessage());
  }
}
