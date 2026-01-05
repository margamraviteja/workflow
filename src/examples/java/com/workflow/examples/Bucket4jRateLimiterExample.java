package com.workflow.examples;

import com.workflow.RateLimitedWorkflow;
import com.workflow.TaskWorkflow;
import com.workflow.Workflow;
import com.workflow.context.WorkflowContext;
import com.workflow.ratelimit.Bucket4jRateLimiter;
import com.workflow.ratelimit.RateLimitStrategy;
import com.workflow.task.TaskDescriptor;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.local.LocalBucket;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Example demonstrating Bucket4j rate limiting in workflows.
 *
 * <p>This example shows various ways to use the Bucket4jRateLimiter for controlling workflow
 * execution rates.
 */
@Slf4j
public class Bucket4jRateLimiterExample {

  public static void main(String[] args) throws Exception {
    log.info("=== Bucket4j Rate Limiter Examples ===\n");

    // Example 1: Basic rate limiting
    basicRateLimiting();

    // Example 2: Burst capacity
    burstCapacity();

    // Example 3: Advanced bandwidth configuration
    advancedConfiguration();

    // Example 4: Concurrent access
    concurrentAccess();

    // Example 5: Different refill strategies
    refillStrategies();

    log.info("\n=== All Examples Completed ===");
  }

  /**
   * Example 1: Basic rate limiting with Bucket4j.
   *
   * <p>This shows the simplest way to create a rate-limited workflow.
   */
  private static void basicRateLimiting() {
    log.info("Example 1: Basic Rate Limiting");
    log.info("--------------------------------");

    // Create a simple counter workflow
    AtomicInteger counter = new AtomicInteger(0);
    Workflow workflow =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .name("counter-workflow")
                .task(
                    _ -> {
                      int count = counter.incrementAndGet();
                      log.info("  Executed #{}", count);
                    })
                .build());

    // Create rate limiter: 5 requests per second
    RateLimitStrategy rateLimiter = new Bucket4jRateLimiter(5, Duration.ofSeconds(1));

    // Wrap workflow with rate limiting
    Workflow rateLimitedWorkflow =
        RateLimitedWorkflow.builder().workflow(workflow).rateLimitStrategy(rateLimiter).build();

    // Execute 10 times - first 5 should succeed immediately, next 5 should wait
    log.info("Executing 10 times (5 req/sec limit):");
    long start = System.currentTimeMillis();
    for (int i = 0; i < 10; i++) {
      rateLimitedWorkflow.execute(new WorkflowContext());
    }
    long duration = System.currentTimeMillis() - start;

    log.info("Completed in {}ms (expected ~1000ms)\n", duration);
  }

  /**
   * Example 2: Burst capacity demonstration.
   *
   * <p>Shows how Bucket4j allows burst traffic up to the bucket capacity.
   */
  private static void burstCapacity() {
    log.info("Example 2: Burst Capacity");
    log.info("-------------------------");

    Workflow workflow =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .name("burst-workflow")
                .task(_ -> log.info("  Processing request..."))
                .build());

    // Create limiter with burst capacity of 100, but refills at 50/sec
    // This allows burst of 100 requests, then 50 per second
    RateLimitStrategy rateLimiter =
        new Bucket4jRateLimiter(
            100, // burst capacity
            50, // refill rate
            Duration.ofSeconds(1),
            Bucket4jRateLimiter.RefillStrategy.GREEDY);

    Workflow rateLimitedWorkflow =
        RateLimitedWorkflow.builder().workflow(workflow).rateLimitStrategy(rateLimiter).build();

    log.info("Executing burst of 100 requests:");
    long start = System.currentTimeMillis();
    for (int i = 0; i < 100; i++) {
      rateLimitedWorkflow.execute(new WorkflowContext());
    }
    long duration = System.currentTimeMillis() - start;

    log.info("Burst completed in {}ms (should be very fast)", duration);
    log.info("(Further requests would be limited to 50/sec)\n");
  }

  /**
   * Example 3: Advanced bandwidth configuration.
   *
   * <p>Shows how to use Bucket4j's Bandwidth API directly for fine-grained control.
   */
  private static void advancedConfiguration() {
    log.info("Example 3: Advanced Configuration");
    log.info("----------------------------------");

    // Create custom bandwidth with specific refill strategy
    Bandwidth bandwidth =
        Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofSeconds(1)).build();

    RateLimitStrategy rateLimiter = new Bucket4jRateLimiter(bandwidth);
    rateLimiter.tryAcquire();

    // You can also create a bucket directly
    LocalBucket bucket =
        Bucket.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(50)
                    .refillIntervally(50, Duration.ofSeconds(1))
                    .build())
            .build();

    RateLimitStrategy bucketRateLimiter = new Bucket4jRateLimiter(bucket);
    bucketRateLimiter.tryAcquire();

    log.info("Created rate limiters with custom configurations");
    log.info("  - Greedy refill limiter: 100 req/sec");
    log.info("  - Intervally refill limiter: 50 req/sec\n");
  }

  /**
   * Example 4: Concurrent access demonstration.
   *
   * <p>Shows thread safety of the rate limiter under concurrent load.
   */
  private static void concurrentAccess() throws InterruptedException {
    log.info("Example 4: Concurrent Access");
    log.info("----------------------------");

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    Workflow workflow =
        new TaskWorkflow(
            TaskDescriptor.builder()
                .name("concurrent-workflow")
                .task(_ -> successCount.incrementAndGet())
                .build());

    // Rate limiter: 50 requests per second
    Bucket4jRateLimiter rateLimiter = new Bucket4jRateLimiter(50, Duration.ofSeconds(1));

    Workflow rateLimitedWorkflow =
        RateLimitedWorkflow.builder().workflow(workflow).rateLimitStrategy(rateLimiter).build();

    // Execute from multiple threads
    try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
      log.info("Starting 10 threads, each attempting 10 requests...");

      for (int i = 0; i < 10; i++) {
        executor.submit(
            () -> {
              for (int j = 0; j < 10; j++) {
                try {
                  rateLimitedWorkflow.execute(new WorkflowContext());
                } catch (Exception _) {
                  failureCount.incrementAndGet();
                }
              }
            });
      }

      executor.shutdown();
      boolean awaitTermination = executor.awaitTermination(10, TimeUnit.SECONDS);
      log.info("awaitTermination: {}", awaitTermination);

      log.info("Results:");
      log.info("  Successes: {}", successCount.get());
      log.info("  Failures: {}", failureCount.get());
      log.info("  (Expected ~50 successes, 50 failures)\n");
    }
  }

  /**
   * Example 5: Different refill strategies.
   *
   * <p>Compares GREEDY vs INTERVALLY refill strategies.
   */
  private static void refillStrategies() throws InterruptedException {
    log.info("Example 5: Refill Strategies");
    log.info("----------------------------");

    // GREEDY: All tokens added at once when period elapses
    log.info("GREEDY Strategy:");
    RateLimitStrategy greedyLimiter =
        new Bucket4jRateLimiter(
            10, 10, Duration.ofMillis(500), Bucket4jRateLimiter.RefillStrategy.GREEDY);

    log.info("  Initial tokens: {}", greedyLimiter.availablePermits());
    for (int i = 0; i < 10; i++) {
      greedyLimiter.tryAcquire();
    }
    log.info("  After consuming 10: {}", greedyLimiter.availablePermits());
    Thread.sleep(550);
    log.info("  After 550ms wait: {} (all refilled at once)", greedyLimiter.availablePermits());

    // INTERVALLY: Tokens added gradually over time
    log.info("INTERVALLY Strategy:");
    RateLimitStrategy intervallyLimiter =
        new Bucket4jRateLimiter(
            10, 10, Duration.ofMillis(500), Bucket4jRateLimiter.RefillStrategy.INTERVALLY);

    log.info("  Initial tokens: {}", intervallyLimiter.availablePermits());
    for (int i = 0; i < 10; i++) {
      intervallyLimiter.tryAcquire();
    }
    log.info("  After consuming 10: {}", intervallyLimiter.availablePermits());
    Thread.sleep(550);
    log.info("  After 550ms wait: {} (refilled gradually)\n", intervallyLimiter.availablePermits());
  }
}
