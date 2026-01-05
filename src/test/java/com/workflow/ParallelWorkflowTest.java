package com.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.workflow.context.WorkflowContext;
import com.workflow.execution.strategy.ExecutionStrategy;
import com.workflow.helper.FutureUtils;
import com.workflow.helper.Workflows;
import com.workflow.listener.WorkflowListeners;
import com.workflow.task.NoOpTask;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import com.workflow.test.WorkflowTestUtils;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Comprehensive test suite for ParallelWorkflow covering concurrency, fail-fast behavior, context
 * sharing, and error handling scenarios.
 */
class ParallelWorkflowTest {

  /** Creates an immediate execution strategy for testing. */
  private static ExecutionStrategy immediateStrategy() {
    return new ExecutionStrategy() {
      @Override
      public <T> CompletableFuture<T> submit(Callable<T> task) {
        try {
          T r = task.call();
          return CompletableFuture.completedFuture(r);
        } catch (Exception e) {
          CompletableFuture<T> cf = new CompletableFuture<>();
          cf.completeExceptionally(e);
          return cf;
        }
      }

      @Override
      public void close() {
        // No-op
      }
    };
  }

  @Test
  void execute_allWorkflowsSucceed_returnsSuccess() {
    Workflow w1 = WorkflowTestUtils.mockSuccessfulWorkflow("w1");
    Workflow w2 = WorkflowTestUtils.mockSuccessfulWorkflow("w2");

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("parallel-success")
            .workflow(w1)
            .workflow(w2)
            .executionStrategy(immediateStrategy())
            .build();

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), anyBoolean()))
          .thenReturn(CompletableFuture.completedFuture(null));

      WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

      WorkflowTestUtils.assertSuccess(result);
      verify(w1).execute(any());
      verify(w2).execute(any());
    }
  }

  @Test
  void execute_oneWorkflowFails_returnsFailure() {
    Workflow w1 = WorkflowTestUtils.mockSuccessfulWorkflow("w1");

    ParallelWorkflow wf =
        Workflows.parallel("parallel-fail")
            .workflow(w1)
            .executionStrategy(immediateStrategy())
            .failFast(true)
            .build();

    RuntimeException cause = new RuntimeException("boom");
    CompletableFuture<Void> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(cause);

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), eq(true))).thenReturn(failedFuture);

      WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

      WorkflowTestUtils.assertFailed(result);
      assertNotNull(result.getError());
    }
  }

  @Test
  void execute_emptyWorkflows_returnsSuccessImmediately() {
    ParallelWorkflow wf =
        ParallelWorkflow.builder().name("empty").executionStrategy(immediateStrategy()).build();

    WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void execute_shareContextTrue_allWorkflowsReceiveSameContext() {
    WorkflowContext original = new WorkflowContext();
    original.put("shared", "value");

    AtomicInteger executionCount = new AtomicInteger(0);

    Task task =
        context -> {
          executionCount.incrementAndGet();
          assertEquals("value", context.get("shared"));
          context.put("modified", executionCount.get());
        };

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("shared-context")
            .task(task)
            .task(task)
            .executionStrategy(immediateStrategy())
            .shareContext(true)
            .build();

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), anyBoolean()))
          .thenReturn(CompletableFuture.completedFuture(null));

      WorkflowResult result = wf.execute(original);

      WorkflowTestUtils.assertSuccess(result);
      assertEquals(2, executionCount.get());
      assertNotNull(original.get("modified"));
    }
  }

  @Test
  void execute_shareContextFalse_eachWorkflowGetsContextCopy() {
    WorkflowContext original = mock(WorkflowContext.class);
    WorkflowContext copy1 = mock(WorkflowContext.class);
    WorkflowContext copy2 = mock(WorkflowContext.class);

    when(original.copy()).thenReturn(copy1, copy2);
    when(original.getListeners()).thenReturn(new WorkflowListeners());

    Workflow w1 = WorkflowTestUtils.mockSuccessfulWorkflow("w1");
    Workflow w2 = WorkflowTestUtils.mockSuccessfulWorkflow("w2");

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("isolated-context")
            .workflow(w1)
            .workflow(w2)
            .executionStrategy(immediateStrategy())
            .shareContext(false)
            .build();

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), anyBoolean()))
          .thenReturn(CompletableFuture.completedFuture(null));

      wf.execute(original);

      verify(original, times(2)).copy();
    }
  }

  @Test
  void builder_taskConvenience_addsTaskWorkflow() {
    final boolean[] ran = {false};
    Task t = _ -> ran[0] = true;

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("task-builder")
            .task(t)
            .executionStrategy(immediateStrategy())
            .build();

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), anyBoolean()))
          .thenReturn(CompletableFuture.completedFuture(null));

      WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

      WorkflowTestUtils.assertSuccess(result);
      assertTrue(ran[0]);
    }
  }

  @Test
  void builder_taskDescriptorConvenience_addsTaskWorkflowWithPolicies() {
    final boolean[] ran = {false};
    Task t = _ -> ran[0] = true;

    TaskDescriptor descriptor = TaskDescriptor.builder().task(t).name("test-task").build();

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("task-descriptor-builder")
            .task(descriptor)
            .executionStrategy(immediateStrategy())
            .build();

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), anyBoolean()))
          .thenReturn(CompletableFuture.completedFuture(null));

      WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

      WorkflowTestUtils.assertSuccess(result);
      assertTrue(ran[0]);
    }
  }

  @Test
  void execute_failFastTrue_cancelsRemainingWorkflowsOnFailure() {
    Workflow w1 = WorkflowTestUtils.mockSuccessfulWorkflow("w1");
    Workflow w2 = WorkflowTestUtils.mockSuccessfulWorkflow("w2");

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("fail-fast-test")
            .workflow(w1)
            .workflow(w2)
            .executionStrategy(immediateStrategy())
            .failFast(true)
            .build();

    RuntimeException error = new RuntimeException("fail");
    CompletableFuture<Void> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(error);

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), eq(true))).thenReturn(failedFuture);

      WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

      WorkflowTestUtils.assertFailed(result);
    }
  }

  @Test
  void execute_failFastFalse_waitsForAllWorkflows() {
    Workflow w1 = WorkflowTestUtils.mockSuccessfulWorkflow("w1");
    Workflow w2 = WorkflowTestUtils.mockSuccessfulWorkflow("w2");

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("no-fail-fast")
            .workflow(w1)
            .workflow(w2)
            .executionStrategy(immediateStrategy())
            .failFast(false)
            .build();

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), eq(false)))
          .thenReturn(CompletableFuture.completedFuture(null));

      WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

      WorkflowTestUtils.assertSuccess(result);
      verify(w1).execute(any());
      verify(w2).execute(any());
    }
  }

  @Test
  void execute_executionStrategyThrows_propagatesException() {
    ExecutionStrategy badStrategy =
        new ExecutionStrategy() {
          @Override
          public <T> CompletableFuture<T> submit(Callable<T> task) {
            throw new RuntimeException("submit failed");
          }

          @Override
          public void close() {
            // No-op
          }
        };

    Workflow w = WorkflowTestUtils.mockSuccessfulWorkflow();

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("bad-strategy")
            .workflow(w)
            .executionStrategy(badStrategy)
            .build();

    WorkflowResult workflowResult = wf.execute(WorkflowTestUtils.createContext());
    assertEquals("submit failed", workflowResult.getError().getMessage());
  }

  @Test
  void execute_multipleWorkflows_allExecuteConcurrently() throws InterruptedException {
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch executionLatch = new CountDownLatch(3);

    Task task =
        _ -> {
          try {
            boolean await = startLatch.await(1, TimeUnit.SECONDS);
            if (!await) {
              throw new RuntimeException("Task timed out waiting to start");
            }
            executionLatch.countDown();
          } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
          }
        };

    ExecutionStrategy asyncStrategy =
        new ExecutionStrategy() {
          @Override
          public <T> CompletableFuture<T> submit(Callable<T> task) {
            return CompletableFuture.supplyAsync(
                () -> {
                  try {
                    return task.call();
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
          }

          @Override
          public void close() {
            // No-op
          }
        };

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("concurrent-test")
            .task(task)
            .task(task)
            .task(task)
            .executionStrategy(asyncStrategy)
            .build();

    CompletableFuture<WorkflowResult> futureResult =
        CompletableFuture.supplyAsync(() -> wf.execute(WorkflowTestUtils.createContext()));

    // Release all tasks at once
    startLatch.countDown();

    // All tasks should complete within reasonable time if executing concurrently
    boolean completed = executionLatch.await(2, TimeUnit.SECONDS);
    assertTrue(completed, "Tasks did not execute concurrently");

    WorkflowResult result = futureResult.join();
    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void getName_returnsProvidedName() {
    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("my-parallel")
            .workflow(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    assertEquals("my-parallel", wf.getName());
  }

  @Test
  void getName_withNullName_returnsDefaultName() {
    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name(null)
            .workflow(WorkflowTestUtils.mockSuccessfulWorkflow())
            .build();

    String name = wf.getName();
    assertNotNull(name);
    assertTrue(name.contains("ParallelWorkflow"));
  }

  @Test
  void execute_mixedTasksAndWorkflows_allExecuteSuccessfully() {
    final AtomicInteger taskCount = new AtomicInteger(0);
    Task task = _ -> taskCount.incrementAndGet();

    Workflow workflow = WorkflowTestUtils.mockSuccessfulWorkflow("workflow");

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("mixed")
            .task(task)
            .workflow(workflow)
            .task(task)
            .executionStrategy(immediateStrategy())
            .build();

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), anyBoolean()))
          .thenReturn(CompletableFuture.completedFuture(null));

      WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

      WorkflowTestUtils.assertSuccess(result);
      assertEquals(2, taskCount.get());
      verify(workflow).execute(any());
    }
  }

  @Test
  void execute_extractsCauseFromExecutionException() {
    Workflow w = WorkflowTestUtils.mockSuccessfulWorkflow();

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("execution-exception")
            .workflow(w)
            .executionStrategy(immediateStrategy())
            .build();

    IllegalStateException cause = new IllegalStateException("root cause");
    CompletableFuture<Void> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(cause);

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), anyBoolean())).thenReturn(failedFuture);

      WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

      WorkflowTestUtils.assertFailed(result);
      assertNotNull(result.getError());
    }
  }

  @Test
  void execute_withNoOpTask_completesSuccessfully() {
    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("noop-test")
            .task(new NoOpTask())
            .task(new NoOpTask())
            .executionStrategy(immediateStrategy())
            .build();

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), anyBoolean()))
          .thenReturn(CompletableFuture.completedFuture(null));

      WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

      WorkflowTestUtils.assertSuccess(result);
    }
  }

  @Test
  void builder_workflowsConvenience_executesAllWorkflows() {
    Workflow w1 = WorkflowTestUtils.mockSuccessfulWorkflow("w1");
    Workflow w2 = WorkflowTestUtils.mockSuccessfulWorkflow("w2");

    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("parallel-success")
            .workflows(java.util.Arrays.asList(w1, w2, null))
            .executionStrategy(immediateStrategy())
            .build();

    try (MockedStatic<FutureUtils> fu = Mockito.mockStatic(FutureUtils.class)) {
      fu.when(() -> FutureUtils.allOf(anyList(), anyBoolean()))
          .thenReturn(CompletableFuture.completedFuture(null));

      WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

      WorkflowTestUtils.assertSuccess(result);
      verify(w1).execute(any());
      verify(w2).execute(any());
    }
  }

  @Test
  void builder_workflowsConvenience_withNullList() {
    ParallelWorkflow wf =
        ParallelWorkflow.builder()
            .name("parallel-success")
            .workflows(null)
            .executionStrategy(immediateStrategy())
            .build();

    WorkflowResult result = wf.execute(WorkflowTestUtils.createContext());

    WorkflowTestUtils.assertSuccess(result);
  }

  @Test
  void getWorkflowType_returnsCorrectType() {
    ParallelWorkflow wf = ParallelWorkflow.builder().build();
    assertEquals("[Parallel]", wf.getWorkflowType());
  }

  @Test
  void getSubWorkflows_returnsUnmodifiableList() {
    Workflow w1 = WorkflowTestUtils.mockSuccessfulWorkflow("w1");
    ParallelWorkflow wf = ParallelWorkflow.builder().workflow(w1).build();
    java.util.List<Workflow> subWorkflows = wf.getSubWorkflows();
    assertEquals(1, subWorkflows.size());
    assertThrows(UnsupportedOperationException.class, () -> subWorkflows.add(w1));
  }

  @Test
  void builder_withNullExecutionStrategy_throwsException() {
    ParallelWorkflow.ParallelWorkflowBuilder builder =
        ParallelWorkflow.builder().executionStrategy(null);
    assertThrows(NullPointerException.class, builder::build);
  }
}
