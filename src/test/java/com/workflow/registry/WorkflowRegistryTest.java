package com.workflow.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.helper.WorkflowResults;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowRegistryTest {

  private WorkflowRegistry registry;
  private Workflow testWorkflow;

  @BeforeEach
  void setUp() {
    registry = new WorkflowRegistry();
    testWorkflow = SequentialWorkflow.builder().name("testWorkflow").build();
  }

  @Test
  void shouldRegisterWorkflow() {
    registry.register("workflow1", testWorkflow);

    assertTrue(registry.isRegistered("workflow1"));
    Optional<Workflow> retrieved = registry.getWorkflow("workflow1");
    assertTrue(retrieved.isPresent());
    assertEquals(testWorkflow, retrieved.get());
  }

  @Test
  void shouldThrowExceptionWhenRegisteringDuplicateName() {
    registry.register("workflow1", testWorkflow);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> registry.register("workflow1", testWorkflow));

    assertTrue(exception.getMessage().contains("workflow1"));
    assertTrue(exception.getMessage().contains("already registered"));
  }

  @Test
  void shouldReturnEmptyOptionalForUnregisteredWorkflow() {
    Optional<Workflow> retrieved = registry.getWorkflow("nonexistent");

    assertFalse(retrieved.isPresent());
  }

  @Test
  void shouldReturnFalseForUnregisteredWorkflow() {
    assertFalse(registry.isRegistered("nonexistent"));
  }

  @Test
  void shouldRegisterMultipleWorkflows() {
    Workflow workflow1 = SequentialWorkflow.builder().name("workflow1").build();
    Workflow workflow2 = SequentialWorkflow.builder().name("workflow2").build();
    Workflow workflow3 = SequentialWorkflow.builder().name("workflow3").build();

    registry.register("workflow1", workflow1);
    registry.register("workflow2", workflow2);
    registry.register("workflow3", workflow3);

    assertTrue(registry.isRegistered("workflow1"));
    assertTrue(registry.isRegistered("workflow2"));
    assertTrue(registry.isRegistered("workflow3"));

    assertEquals(workflow1, registry.getWorkflow("workflow1").get());
    assertEquals(workflow2, registry.getWorkflow("workflow2").get());
    assertEquals(workflow3, registry.getWorkflow("workflow3").get());
  }

  @Test
  void shouldHandleNullWorkflow() {
    registry.register("nullWorkflow", null);

    assertTrue(registry.isRegistered("nullWorkflow"));
    Optional<Workflow> retrieved = registry.getWorkflow("nullWorkflow");
    assertFalse(retrieved.isPresent());
  }

  @Test
  void shouldBeThreadSafe() throws InterruptedException {
    try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
      CountDownLatch latch = new CountDownLatch(100);
      AtomicInteger successCount = new AtomicInteger(0);
      AtomicInteger failCount = new AtomicInteger(0);

      for (int i = 0; i < 100; i++) {
        final int index = i;
        executor.submit(
            () -> {
              try {
                Workflow workflow = SequentialWorkflow.builder().name("workflow" + index).build();
                registry.register("workflow" + index, workflow);
                successCount.incrementAndGet();
              } catch (Exception _) {
                failCount.incrementAndGet();
              } finally {
                latch.countDown();
              }
            });
      }

      latch.await(5, TimeUnit.SECONDS);
      executor.shutdown();

      assertEquals(100, successCount.get());
      assertEquals(0, failCount.get());

      for (int i = 0; i < 100; i++) {
        assertTrue(registry.isRegistered("workflow" + i));
      }
    }
  }

  @Test
  void shouldHandleConcurrentReadsAndWrites() throws InterruptedException {
    try (ExecutorService executor = Executors.newFixedThreadPool(20)) {
      CountDownLatch writeLatch = new CountDownLatch(10);
      CountDownLatch readLatch = new CountDownLatch(100);

      // Register workflows
      for (int i = 0; i < 10; i++) {
        final int index = i;
        executor.submit(
            () -> {
              try {
                Workflow workflow = SequentialWorkflow.builder().name("workflow" + index).build();
                registry.register("workflow" + index, workflow);
              } finally {
                writeLatch.countDown();
              }
            });
      }

      writeLatch.await(2, TimeUnit.SECONDS);

      // Read workflows concurrently
      for (int i = 0; i < 100; i++) {
        final int index = i % 10;
        executor.submit(
            () -> {
              try {
                Optional<Workflow> workflow = registry.getWorkflow("workflow" + index);
                assertTrue(workflow.isPresent());
              } finally {
                readLatch.countDown();
              }
            });
      }

      readLatch.await(5, TimeUnit.SECONDS);
      executor.shutdown();
    }
  }

  @Test
  void shouldHandleEmptyStringName() {
    registry.register("", testWorkflow);

    assertTrue(registry.isRegistered(""));
    Optional<Workflow> retrieved = registry.getWorkflow("");
    assertTrue(retrieved.isPresent());
  }

  @Test
  void shouldHandleSpecialCharactersInName() {
    registry.register("workflow-with-special-chars!@#$%", testWorkflow);

    assertTrue(registry.isRegistered("workflow-with-special-chars!@#$%"));
    Optional<Workflow> retrieved = registry.getWorkflow("workflow-with-special-chars!@#$%");
    assertTrue(retrieved.isPresent());
  }

  @Test
  void shouldHandleLongWorkflowNames() {
    String longName = "a".repeat(1000);
    registry.register(longName, testWorkflow);

    assertTrue(registry.isRegistered(longName));
    Optional<Workflow> retrieved = registry.getWorkflow(longName);
    assertTrue(retrieved.isPresent());
  }

  @Test
  void shouldNotAllowDuplicateRegistrationEvenWithDifferentWorkflows() {
    Workflow workflow1 = SequentialWorkflow.builder().name("workflow1").build();
    Workflow workflow2 = SequentialWorkflow.builder().name("workflow2").build();

    registry.register("sameName", workflow1);

    assertThrows(IllegalArgumentException.class, () -> registry.register("sameName", workflow2));
  }

  @Test
  void shouldPreserveWorkflowIdentity() {
    Workflow customWorkflow =
        new Workflow() {
          @Override
          public WorkflowResult execute(WorkflowContext context) {
            CompletableFuture.completedFuture("custom");
            return WorkflowResults.success(Instant.now(), Instant.now());
          }

          @Override
          public String getName() {
            return "custom";
          }
        };

    registry.register("custom", customWorkflow);

    Optional<Workflow> retrieved = registry.getWorkflow("custom");
    assertTrue(retrieved.isPresent());
    assertSame(customWorkflow, retrieved.get());
  }

  @Test
  void shouldHandleConcurrentDuplicateRegistrations() throws InterruptedException {
    try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
      CountDownLatch latch = new CountDownLatch(10);
      AtomicInteger exceptionCount = new AtomicInteger(0);

      for (int i = 0; i < 10; i++) {
        executor.submit(
            () -> {
              try {
                Workflow workflow = SequentialWorkflow.builder().name("duplicate").build();
                registry.register("duplicate", workflow);
              } catch (IllegalArgumentException _) {
                exceptionCount.incrementAndGet();
              } finally {
                latch.countDown();
              }
            });
      }

      latch.await(5, TimeUnit.SECONDS);
      executor.shutdown();

      // Exactly one should succeed, the rest should fail
      assertEquals(9, exceptionCount.get());
      assertTrue(registry.isRegistered("duplicate"));
    }
  }
}
