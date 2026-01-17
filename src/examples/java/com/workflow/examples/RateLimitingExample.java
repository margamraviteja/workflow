package com.workflow.examples;

import com.workflow.ParallelWorkflow;
import com.workflow.RateLimitedWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.WorkflowStatus;
import com.workflow.context.WorkflowContext;
import com.workflow.ratelimit.FixedWindowRateLimiter;
import com.workflow.ratelimit.LeakyBucketRateLimiter;
import com.workflow.ratelimit.RateLimitStrategy;
import com.workflow.ratelimit.SlidingWindowRateLimiter;
import com.workflow.ratelimit.TokenBucketRateLimiter;
import com.workflow.task.Task;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Examples demonstrating rate limiting capabilities in the workflow engine.
 *
 * <p>This example shows various rate limiting scenarios:
 *
 * <ul>
 *   <li>Fixed window rate limiting
 *   <li>Sliding window rate limiting
 *   <li>Token bucket with burst support
 *   <li>Leaky bucket for steady rate
 *   <li>Shared rate limiters
 *   <li>API call throttling
 * </ul>
 *
 * <p><b>Note:</b> For Resilience4j rate limiter examples, see {@link
 * Resilience4jRateLimiterExample}.
 */
@Slf4j
@UtilityClass
public class RateLimitingExample {

  static void main() {
    log.info("=== Rate Limiting Examples ===\n");

    // Example 1: Fixed Window Rate Limiting
    fixedWindowExample();

    // Example 2: Sliding Window Rate Limiting
    slidingWindowExample();

    // Example 3: Token Bucket with Burst
    tokenBucketExample();

    // Example 4: Leaky Bucket
    leakyBucketExample();

    // Example 5: Shared Rate Limiter
    sharedRateLimiterExample();

    // Example 6: API Throttling
    apiThrottlingExample();

    log.info("\n=== All Examples Complete ===");
  }

  private static void fixedWindowExample() {
    log.info("\n--- Example 1: Fixed Window Rate Limiting ---");
    log.info("Allows 5 requests per second");

    // Create rate limiter: 5 requests per second
    RateLimitStrategy limiter = new FixedWindowRateLimiter(5, Duration.ofSeconds(1));

    // Create simple workflow
    AtomicInteger counter = new AtomicInteger(0);
    Task simpleTask =
        _ -> {
          int count = counter.incrementAndGet();
          log.info("Executing request #{}", count);
        };

    Workflow rateLimited =
        RateLimitedWorkflow.builder()
            .name("FixedWindowExample")
            .workflow(SequentialWorkflow.builder().name("SimpleTask").task(simpleTask).build())
            .rateLimitStrategy(limiter)
            .build();

    WorkflowContext context = new WorkflowContext();
    Instant start = Instant.now();

    // Try to execute 10 requests (will be rate limited)
    for (int i = 0; i < 10; i++) {
      rateLimited.execute(context);
    }

    Duration elapsed = Duration.between(start, Instant.now());
    log.info("Completed 10 requests in {}ms", elapsed.toMillis());
    logAverage(elapsed);
  }

  private static void logAverage(Duration elapsed) {
    log.info("Average rate: {} req/sec", 10.0 / (elapsed.toMillis() / 1000.0));
  }

  private static void slidingWindowExample() {
    log.info("\n--- Example 2: Sliding Window Rate Limiting ---");
    log.info("Allows 10 requests per second with sliding window");

    RateLimitStrategy limiter = new SlidingWindowRateLimiter(10, Duration.ofSeconds(1));

    AtomicInteger counter = new AtomicInteger(0);
    Task task =
        _ -> {
          int count = counter.incrementAndGet();
          log.info("Request #{} at {}", count, Instant.now().toEpochMilli() % 10000);
        };

    Workflow rateLimited =
        RateLimitedWorkflow.builder()
            .workflow(SequentialWorkflow.builder().task(task).build())
            .rateLimitStrategy(limiter)
            .build();

    WorkflowContext context = new WorkflowContext();
    Instant start = Instant.now();

    // Execute 15 requests
    for (int i = 0; i < 15; i++) {
      rateLimited.execute(context);
    }

    Duration elapsed = Duration.between(start, Instant.now());
    log.info("Completed 15 requests in {}ms", elapsed.toMillis());
  }

  private static void tokenBucketExample() {
    log.info("\n--- Example 3: Token Bucket with Burst ---");
    log.info("Allows 5 req/sec with burst capacity of 10");

    // Token bucket: 5 tokens per second, capacity 10
    RateLimitStrategy limiter = new TokenBucketRateLimiter(5, 10, Duration.ofSeconds(1));

    AtomicInteger counter = new AtomicInteger(0);
    Task task =
        _ -> {
          int count = counter.incrementAndGet();
          log.info("Burst request #{}", count);
        };

    Workflow rateLimited =
        RateLimitedWorkflow.builder()
            .workflow(SequentialWorkflow.builder().task(task).build())
            .rateLimitStrategy(limiter)
            .build();

    WorkflowContext context = new WorkflowContext();

    log.info("Initial burst (should be fast):");
    Instant burstStart = Instant.now();
    // First 10 should execute quickly (burst)
    for (int i = 0; i < 10; i++) {
      rateLimited.execute(context);
    }
    Duration burstTime = Duration.between(burstStart, Instant.now());
    log.info("Burst of 10 completed in {}ms", burstTime.toMillis());

    log.info("\nSteady state (should be throttled):");
    Instant steadyStart = Instant.now();
    // Next 10 should be throttled
    for (int i = 0; i < 10; i++) {
      rateLimited.execute(context);
    }
    Duration steadyTime = Duration.between(steadyStart, Instant.now());
    log.info("Next 10 completed in {}ms (throttled)", steadyTime.toMillis());
  }

  private static void leakyBucketExample() {
    log.info("\n--- Example 4: Leaky Bucket ---");
    log.info("Constant output rate of 10 req/sec");

    RateLimitStrategy limiter = new LeakyBucketRateLimiter(10, 15, Duration.ofSeconds(1));

    AtomicInteger counter = new AtomicInteger(0);
    Task task = _ -> log.info("Steady request #{}", counter.incrementAndGet());

    Workflow rateLimited =
        RateLimitedWorkflow.builder()
            .workflow(SequentialWorkflow.builder().task(task).build())
            .rateLimitStrategy(limiter)
            .build();

    WorkflowContext context = new WorkflowContext();
    Instant start = Instant.now();

    // Execute 20 requests
    for (int i = 0; i < 20; i++) {
      rateLimited.execute(context);
    }

    Duration elapsed = Duration.between(start, Instant.now());
    log.info("Completed 20 requests in {}ms", elapsed.toMillis());
    logAverage(elapsed);
  }

  private static void sharedRateLimiterExample() {
    log.info("\n--- Example 5: Shared Rate Limiter ---");
    log.info("Two workflows sharing the same rate limit");

    // Shared rate limiter: 10 requests per second total
    RateLimitStrategy sharedLimiter = new FixedWindowRateLimiter(10, Duration.ofSeconds(1));

    AtomicInteger workflow1Counter = new AtomicInteger(0);
    AtomicInteger workflow2Counter = new AtomicInteger(0);

    Task task1 = _ -> log.info("Workflow 1 - Request #{}", workflow1Counter.incrementAndGet());

    Task task2 = _ -> log.info("Workflow 2 - Request #{}", workflow2Counter.incrementAndGet());

    Workflow rateLimited1 =
        RateLimitedWorkflow.builder()
            .name("Workflow1")
            .workflow(SequentialWorkflow.builder().task(task1).build())
            .rateLimitStrategy(sharedLimiter)
            .build();

    Workflow rateLimited2 =
        RateLimitedWorkflow.builder()
            .name("Workflow2")
            .workflow(SequentialWorkflow.builder().task(task2).build())
            .rateLimitStrategy(sharedLimiter)
            .build();

    WorkflowContext context = new WorkflowContext();

    // Execute both workflows alternately
    for (int i = 0; i < 8; i++) {
      rateLimited1.execute(context);
      rateLimited2.execute(context);
    }

    log.info("Total: Workflow1={}, Workflow2={}", workflow1Counter.get(), workflow2Counter.get());
  }

  private static void apiThrottlingExample() {
    log.info("\n--- Example 6: API Call Throttling ---");
    log.info("Simulating rate-limited API calls");

    // API has rate limit: 100 req/min
    RateLimitStrategy apiLimiter = new SlidingWindowRateLimiter(100, Duration.ofMinutes(1));

    // Simulate API call task
    AtomicInteger apiCallCount = new AtomicInteger(0);
    Task apiCallTask =
        ctx -> {
          int callNum = apiCallCount.incrementAndGet();
          String userId = ctx.getTyped("userId", String.class);

          // Simulate API call
          log.info("API Call #{}: Fetching data for user {}", callNum, userId);

          // Simulate response
          ctx.put("apiResponse", "Data for " + userId);
        };

    // Create rate-limited workflow
    Workflow rateLimitedApi =
        RateLimitedWorkflow.builder()
            .name("RateLimitedAPI")
            .workflow(SequentialWorkflow.builder().task(apiCallTask).build())
            .rateLimitStrategy(apiLimiter)
            .build();

    // Create batch processing workflow
    Workflow batchProcessor =
        SequentialWorkflow.builder()
            .name("BatchProcessor")
            .workflow(rateLimitedApi) // Each API call is rate limited
            .build();

    // Process batch of users
    List<String> userIds = List.of("user1", "user2", "user3", "user4", "user5");

    log.info("Processing batch of {} users...", userIds.size());
    Instant start = Instant.now();

    for (String userId : userIds) {
      WorkflowContext context = new WorkflowContext();
      context.put("userId", userId);

      WorkflowResult result = batchProcessor.execute(context);

      if (result.getStatus() == WorkflowStatus.SUCCESS) {
        String response = context.getTyped("apiResponse", String.class);
        log.info("Processed: {}", response);
      }
    }

    Duration elapsed = Duration.between(start, Instant.now());
    log.info("Batch processing completed in {}ms", elapsed.toMillis());
    log.info("Total API calls: {}", apiCallCount.get());
  }

  /** Example showing parallel workflows with shared rate limiter. */
  public static void parallelWithRateLimitExample() {
    log.info("\n--- Bonus: Parallel Workflows with Rate Limiting ---");

    // Shared rate limiter for all parallel tasks
    RateLimitStrategy sharedLimiter = new TokenBucketRateLimiter(10, 20, Duration.ofSeconds(1));

    List<Workflow> rateLimitedWorkflows = new ArrayList<>();

    for (int i = 0; i < 5; i++) {
      int taskNum = i;
      Task task =
          _ -> {
            log.info("Parallel task {} executing", taskNum);
            try {
              Thread.sleep(10); // Simulate work
            } catch (InterruptedException _) {
              Thread.currentThread().interrupt();
            }
          };

      Workflow rateLimited =
          RateLimitedWorkflow.builder()
              .workflow(SequentialWorkflow.builder().task(task).build())
              .rateLimitStrategy(sharedLimiter)
              .build();

      rateLimitedWorkflows.add(rateLimited);
    }

    // Execute all in parallel
    Workflow parallel =
        ParallelWorkflow.builder()
            .name("ParallelRateLimited")
            .workflows(rateLimitedWorkflows)
            .build();

    WorkflowContext context = new WorkflowContext();
    Instant start = Instant.now();

    parallel.execute(context);

    Duration elapsed = Duration.between(start, Instant.now());
    log.info("Parallel execution with rate limiting completed in {}ms", elapsed.toMillis());
  }
}
