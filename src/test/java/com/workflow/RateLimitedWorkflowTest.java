package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.ratelimit.FixedWindowRateLimiter;
import com.workflow.ratelimit.RateLimitStrategy;
import com.workflow.ratelimit.TokenBucketRateLimiter;
import com.workflow.sleeper.ThreadSleepingSleeper;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RateLimitedWorkflowTest {

  @Test
  void testBasicRateLimiting() {
    AtomicInteger executionCount = new AtomicInteger(0);
    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            executionCount.incrementAndGet();
            return execContext.success();
          }
        };

    RateLimitStrategy limiter = new FixedWindowRateLimiter(5, Duration.ofSeconds(1));
    Workflow rateLimited =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowContext context = new WorkflowContext();

    // Should allow 5 executions
    for (int i = 0; i < 5; i++) {
      WorkflowResult result = rateLimited.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    }

    assertEquals(5, executionCount.get());
  }

  @Test
  void testRateLimitBlocking() {
    AtomicInteger executionCount = new AtomicInteger(0);
    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            executionCount.incrementAndGet();
            return execContext.success();
          }
        };

    RateLimitStrategy limiter = new FixedWindowRateLimiter(2, Duration.ofMillis(200));
    Workflow rateLimited =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowContext context = new WorkflowContext();

    long start = System.currentTimeMillis();

    // Execute 4 times - should block after 2
    for (int i = 0; i < 4; i++) {
      rateLimited.execute(context);
    }

    long elapsed = System.currentTimeMillis() - start;

    assertEquals(4, executionCount.get());
    assertTrue(
        elapsed >= 150, "Should have blocked for rate limit, elapsed: " + elapsed + "ms"); // At
    // least
    // 1
    // window
    // wait
  }

  @Test
  void testWorkflowNameGeneration() {
    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            return execContext.success();
          }
        };

    RateLimitStrategy limiter = new FixedWindowRateLimiter(10, Duration.ofSeconds(1));

    // Without explicit name
    Workflow workflow1 =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    assertTrue(workflow1.getName().contains("RateLimited"));

    // With explicit name
    Workflow workflow2 =
        RateLimitedWorkflow.builder()
            .name("CustomRateLimited")
            .workflow(innerWorkflow)
            .rateLimitStrategy(limiter)
            .build();

    assertEquals("CustomRateLimited", workflow2.getName());
  }

  @Test
  void testContextPassthrough() {
    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            context.put("executed", true);
            context.put("value", 42);
            return execContext.success();
          }
        };

    RateLimitStrategy limiter = new FixedWindowRateLimiter(10, Duration.ofSeconds(1));
    Workflow rateLimited =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowContext context = new WorkflowContext();
    context.put("input", "test");

    rateLimited.execute(context);

    // Context should be passed through
    assertEquals("test", context.get("input"));
    assertTrue(context.getTyped("executed", Boolean.class));
    assertEquals(42, context.getTyped("value", Integer.class));
  }

  @Test
  void testErrorPropagation() {
    Workflow failingWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            return execContext.failure(new RuntimeException("Test error"));
          }
        };

    RateLimitStrategy limiter = new FixedWindowRateLimiter(10, Duration.ofSeconds(1));
    Workflow rateLimited =
        RateLimitedWorkflow.builder().workflow(failingWorkflow).rateLimitStrategy(limiter).build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = rateLimited.execute(context);

    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertNotNull(result.getError());
    assertEquals("Test error", result.getError().getMessage());
  }

  @Test
  void testConcurrentExecutions() throws InterruptedException {
    AtomicInteger executionCount = new AtomicInteger(0);
    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            executionCount.incrementAndGet();
            return execContext.success();
          }
        };

    RateLimitStrategy limiter = new FixedWindowRateLimiter(50, Duration.ofSeconds(1));
    Workflow rateLimited =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    int threadCount = 10;
    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
      CountDownLatch latch = new CountDownLatch(threadCount);

      for (int i = 0; i < threadCount; i++) {
        executor.submit(
            () -> {
              WorkflowContext context = new WorkflowContext();
              for (int j = 0; j < 10; j++) {
                rateLimited.execute(context);
              }
              latch.countDown();
            });
      }

      assertTrue(latch.await(10, TimeUnit.SECONDS));
      executor.shutdown();
    }

    assertEquals(100, executionCount.get());
  }

  @Test
  void testSharedRateLimiter() {
    AtomicInteger workflow1Count = new AtomicInteger(0);
    AtomicInteger workflow2Count = new AtomicInteger(0);

    Workflow innerWorkflow1 =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            workflow1Count.incrementAndGet();
            return execContext.success();
          }
        };

    Workflow innerWorkflow2 =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            workflow2Count.incrementAndGet();
            return execContext.success();
          }
        };

    // Shared rate limiter
    RateLimitStrategy sharedLimiter = new FixedWindowRateLimiter(20, Duration.ofSeconds(1));

    Workflow rateLimited1 =
        RateLimitedWorkflow.builder()
            .workflow(innerWorkflow1)
            .rateLimitStrategy(sharedLimiter)
            .build();

    Workflow rateLimited2 =
        RateLimitedWorkflow.builder()
            .workflow(innerWorkflow2)
            .rateLimitStrategy(sharedLimiter)
            .build();

    WorkflowContext context = new WorkflowContext();

    // Execute both workflows - should share the rate limit
    for (int i = 0; i < 15; i++) {
      rateLimited1.execute(context);
    }

    for (int i = 0; i < 15; i++) {
      rateLimited2.execute(context);
    }

    // Total executions should be 30, but not necessarily evenly distributed
    int totalExecutions = workflow1Count.get() + workflow2Count.get();
    assertTrue(totalExecutions >= 20, "At least 20 should execute in first window");
  }

  @Test
  void testWithTokenBucket() {
    AtomicInteger executionCount = new AtomicInteger(0);
    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            executionCount.incrementAndGet();
            return execContext.success();
          }
        };

    // Token bucket: 10 tokens/sec, capacity 20 (allows burst)
    RateLimitStrategy limiter = new TokenBucketRateLimiter(10, 20, Duration.ofSeconds(1));

    Workflow rateLimited =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();

    WorkflowContext context = new WorkflowContext();

    long start = System.currentTimeMillis();

    // Should allow burst of 20, then throttle
    for (int i = 0; i < 25; i++) {
      rateLimited.execute(context);
    }

    long elapsed = System.currentTimeMillis() - start;

    assertEquals(25, executionCount.get());
    // Should have waited for token refill after burst
    assertTrue(elapsed >= 400, "Should have waited for tokens, elapsed: " + elapsed + "ms");
  }

  @Test
  void testGetters() {
    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            return execContext.success();
          }
        };
    RateLimitStrategy limiter = new FixedWindowRateLimiter(10, Duration.ofSeconds(1));

    RateLimitedWorkflow rateLimited =
        RateLimitedWorkflow.builder()
            .name("TestWorkflow")
            .workflow(innerWorkflow)
            .rateLimitStrategy(limiter)
            .build();

    assertEquals("TestWorkflow", rateLimited.getName());
  }

  @Test
  void testGetWorkflowType() {
    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            return execContext.success();
          }
        };
    RateLimitStrategy limiter = new FixedWindowRateLimiter(10, Duration.ofSeconds(1));
    RateLimitedWorkflow rateLimited =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();
    assertEquals("[Rate-Limited]", rateLimited.getWorkflowType());
  }

  @Test
  void testGetSubWorkflows() {
    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            return execContext.success();
          }
        };
    RateLimitStrategy limiter = new FixedWindowRateLimiter(10, Duration.ofSeconds(1));
    RateLimitedWorkflow rateLimited =
        RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(limiter).build();
    assertEquals(1, rateLimited.getSubWorkflows().size());
    assertEquals(innerWorkflow, rateLimited.getSubWorkflows().getFirst());
  }

  @Test
  void testGetSubWorkflows_withNullWorkflow() {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(10, Duration.ofSeconds(1));
    RateLimitedWorkflow.RateLimitedWorkflowBuilder builder =
        RateLimitedWorkflow.builder().rateLimitStrategy(limiter);
    assertThrows(NullPointerException.class, builder::build);
  }

  @Test
  void testBuilder_nullWorkflow_throwsException() {
    RateLimitStrategy limiter = new FixedWindowRateLimiter(10, Duration.ofSeconds(1));
    try {
      RateLimitedWorkflow.builder().rateLimitStrategy(limiter).workflow(null).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void testBuilder_nullRateLimitStrategy_throwsException() {
    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            return execContext.success();
          }
        };
    try {
      RateLimitedWorkflow.builder().workflow(innerWorkflow).rateLimitStrategy(null).build();
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  @Test
  void testInterruption() throws InterruptedException {
    RateLimitStrategy blockingLimiter =
        new RateLimitStrategy() {
          @Override
          public void acquire() throws InterruptedException {
            new ThreadSleepingSleeper().sleep(Duration.ofMillis(5000)); // Sleep for a long time
          }

          @Override
          public boolean tryAcquire() {
            return false;
          }

          @Override
          public boolean tryAcquire(long timeoutMillis) {
            return false;
          }

          @Override
          public void reset() {
            // No-op
          }
        };

    Workflow innerWorkflow =
        new AbstractWorkflow() {
          @Override
          protected WorkflowResult doExecute(
              WorkflowContext context, ExecutionContext execContext) {
            return execContext.success();
          }
        };

    RateLimitedWorkflow rateLimited =
        RateLimitedWorkflow.builder()
            .workflow(innerWorkflow)
            .rateLimitStrategy(blockingLimiter)
            .build();

    Thread thread =
        new Thread(
            () -> {
              WorkflowResult result = rateLimited.execute(new WorkflowContext());
              assertEquals(WorkflowStatus.FAILED, result.getStatus());
              assertInstanceOf(InterruptedException.class, result.getError());
            });

    thread.start();
    new ThreadSleepingSleeper()
        .sleep(Duration.ofMillis(100)); // Give the thread time to start blocking
    thread.interrupt(); // Interrupt the thread
    thread.join(1000); // Wait for thread to finish
  }
}
