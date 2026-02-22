package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.task.NoOpTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for RepeatWorkflow Increases coverage for RepeatWorkflow class */
@DisplayName("RepeatWorkflow - Enhanced Tests")
class RepeatWorkflowEnhancedTest {

  private WorkflowContext context;

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();
  }

  @Nested
  @DisplayName("Count-Based Repeat Tests")
  class CountBasedTests {

    @Test
    @DisplayName("Repeat workflow fixed count")
    void testRepeatFixedCount() {
      AtomicInteger counter = new AtomicInteger(0);

      Workflow repeatingWorkflow = new TaskWorkflow(_ -> counter.incrementAndGet());

      RepeatWorkflow repeat =
          RepeatWorkflow.builder()
              .name("FixedCountRepeat")
              .times(5)
              .workflow(repeatingWorkflow)
              .build();

      WorkflowResult result = repeat.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(5, counter.get());
    }

    @Test
    @DisplayName("Repeat workflow zero count")
    void testRepeatZeroCount() {
      AtomicInteger counter = new AtomicInteger(0);

      Workflow workflow = new TaskWorkflow(_ -> counter.incrementAndGet());

      RepeatWorkflow repeat =
          RepeatWorkflow.builder().name("ZeroCountRepeat").times(0).workflow(workflow).build();

      WorkflowResult result = repeat.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(0, counter.get());
    }

    @Test
    @DisplayName("Repeat workflow single count")
    void testRepeatSingleCount() {
      AtomicInteger counter = new AtomicInteger(0);

      Workflow workflow = new TaskWorkflow(_ -> counter.incrementAndGet());

      RepeatWorkflow repeat =
          RepeatWorkflow.builder().name("SingleCountRepeat").times(1).workflow(workflow).build();

      WorkflowResult result = repeat.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(1, counter.get());
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Repeat stops on workflow failure")
    void testRepeatStopsOnFailure() {
      AtomicInteger counter = new AtomicInteger(0);

      Workflow workflow =
          new TaskWorkflow(
              _ -> {
                int count = counter.incrementAndGet();
                if (count == 3) {
                  throw new RuntimeException("Failed at iteration 3");
                }
              });

      RepeatWorkflow repeat =
          RepeatWorkflow.builder().name("FailingRepeat").times(10).workflow(workflow).build();

      WorkflowResult result = repeat.execute(context);

      assertEquals(WorkflowStatus.FAILED, result.getStatus());
      assertEquals(3, counter.get()); // Should stop at failure
      assertNotNull(result.getError());
    }
  }

  @Nested
  @DisplayName("Context Interaction Tests")
  class ContextInteractionTests {

    @Test
    @DisplayName("Repeat with context modifications")
    void testRepeatWithContextModifications() {
      List<String> log = new ArrayList<>();

      Workflow workflow =
          new TaskWorkflow(
              ctx -> {
                int iteration = log.size() + 1;
                log.add("iteration_" + iteration);
                ctx.put("last_iteration", iteration);
              });

      RepeatWorkflow repeat =
          RepeatWorkflow.builder().name("ContextModRepeat").times(5).workflow(workflow).build();

      WorkflowResult result = repeat.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(5, context.get("last_iteration"));
      assertEquals(5, log.size());
    }

    @Test
    @DisplayName("Repeat with accumulating context data")
    void testRepeatWithAccumulatingData() {
      context.put("values", new ArrayList<Integer>());

      Workflow workflow =
          new TaskWorkflow(
              ctx -> {
                @SuppressWarnings("unchecked")
                List<Integer> values = ctx.getTyped("values", List.class);
                values.add(values.size() + 1);
              });

      RepeatWorkflow repeat =
          RepeatWorkflow.builder().name("AccumulatingRepeat").times(10).workflow(workflow).build();

      WorkflowResult result = repeat.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      @SuppressWarnings("unchecked")
      List<Integer> values = context.getTyped("values", List.class);
      assertEquals(10, values.size());
    }

    @Test
    @DisplayName("Repeat with scoped context")
    void testRepeatWithScopedContext() {
      Workflow workflow =
          new TaskWorkflow(
              ctx -> {
                WorkflowContext scoped = ctx.scope("iteration");
                scoped.put("data", "value");
              });

      RepeatWorkflow repeat =
          RepeatWorkflow.builder().name("ScopedRepeat").times(3).workflow(workflow).build();

      WorkflowResult result = repeat.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals("value", context.get("iteration.data"));
    }
  }

  @Nested
  @DisplayName("Nested Workflow Tests")
  class NestedWorkflowTests {

    @Test
    @DisplayName("Repeat with nested sequential workflow")
    void testRepeatWithNestedSequential() {
      AtomicInteger taskACount = new AtomicInteger(0);
      AtomicInteger taskBCount = new AtomicInteger(0);

      Workflow sequential =
          SequentialWorkflow.builder()
              .name("NestedSequential")
              .task(_ -> taskACount.incrementAndGet())
              .task(_ -> taskBCount.incrementAndGet())
              .build();

      RepeatWorkflow repeat =
          RepeatWorkflow.builder().name("RepeatSequential").times(3).workflow(sequential).build();

      WorkflowResult result = repeat.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(3, taskACount.get());
      assertEquals(3, taskBCount.get());
    }

    @Test
    @DisplayName("Repeat with nested parallel workflow")
    void testRepeatWithNestedParallel() {
      AtomicInteger task1Count = new AtomicInteger(0);
      AtomicInteger task2Count = new AtomicInteger(0);

      Workflow parallel =
          ParallelWorkflow.builder()
              .name("NestedParallel")
              .task(_ -> task1Count.incrementAndGet())
              .task(_ -> task2Count.incrementAndGet())
              .shareContext(true)
              .build();

      RepeatWorkflow repeat =
          RepeatWorkflow.builder().name("RepeatParallel").times(3).workflow(parallel).build();

      WorkflowResult result = repeat.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(3, task1Count.get());
      assertEquals(3, task2Count.get());
    }

    @Test
    @DisplayName("Nested repeat workflows")
    void testNestedRepeat() {
      AtomicInteger counter = new AtomicInteger(0);

      Workflow innerWorkflow = new TaskWorkflow(_ -> counter.incrementAndGet());

      RepeatWorkflow innerRepeat =
          RepeatWorkflow.builder().name("InnerRepeat").times(3).workflow(innerWorkflow).build();

      RepeatWorkflow outerRepeat =
          RepeatWorkflow.builder().name("OuterRepeat").times(4).workflow(innerRepeat).build();

      WorkflowResult result = outerRepeat.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(12, counter.get()); // 4 * 3 = 12
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Repeat performance with simple workflow")
    void testRepeatPerformanceSimple() {
      Workflow workflow = new TaskWorkflow(new NoOpTask());

      RepeatWorkflow repeat =
          RepeatWorkflow.builder().name("PerfTestRepeat").times(10000).workflow(workflow).build();

      long startTime = System.currentTimeMillis();
      WorkflowResult result = repeat.execute(context);
      long duration = System.currentTimeMillis() - startTime;

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertTrue(
          duration < 1000,
          "10000 simple iterations should complete in < 1 second, took " + duration + "ms");
    }

    @Test
    @DisplayName("Repeat with minimal overhead")
    void testRepeatMinimalOverhead() {
      AtomicInteger counter = new AtomicInteger(0);

      Workflow workflow = new TaskWorkflow(_ -> counter.incrementAndGet());

      RepeatWorkflow repeat =
          RepeatWorkflow.builder()
              .name("MinimalOverheadRepeat")
              .times(1000)
              .workflow(workflow)
              .build();

      WorkflowResult result = repeat.execute(context);

      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(1000, counter.get());
    }
  }
}
