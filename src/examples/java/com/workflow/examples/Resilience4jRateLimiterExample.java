package com.workflow.examples;

import com.workflow.RateLimitedWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.ratelimit.RateLimitStrategy;
import com.workflow.ratelimit.Resilience4jRateLimiter;
import com.workflow.task.Task;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.event.RateLimiterOnFailureEvent;
import io.github.resilience4j.ratelimiter.event.RateLimiterOnSuccessEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Examples demonstrating Resilience4j rate limiter integration in the workflow engine.
 *
 * <p>This example shows various Resilience4j rate limiting scenarios:
 *
 * <ul>
 *   <li>Basic Resilience4j rate limiting
 *   <li>Custom configuration with timeout
 *   <li>Event monitoring and metrics
 *   <li>Concurrent workflow execution
 *   <li>Integration with existing Resilience4j setup
 *   <li>Production-grade API throttling
 * </ul>
 */
@Slf4j
public class Resilience4jRateLimiterExample {

  public static void main(String[] args) {
    log.info("=== Resilience4j Rate Limiter Examples ===\n");

    // Example 1: Basic Resilience4j Rate Limiting
    basicResilience4jExample();

    // Example 2: Custom Configuration with Timeout
    customConfigExample();

    // Example 3: Event Monitoring and Metrics
    eventMonitoringExample();

    // Example 4: Concurrent Execution
    concurrentExecutionExample();

    // Example 5: Integration with Existing Resilience4j Setup
    existingRateLimiterExample();

    // Example 6: Production API Throttling
    productionApiExample();

    log.info("\n=== All Examples Complete ===");
  }

  private static void basicResilience4jExample() {
    log.info("\n--- Example 1: Basic Resilience4j Rate Limiting ---");
    log.info("Allows 10 requests per second using Resilience4j");

    // Create Resilience4j rate limiter: 10 requests per second
    RateLimitStrategy limiter = new Resilience4jRateLimiter(10, Duration.ofSeconds(1));

    AtomicInteger counter = new AtomicInteger(0);
    Task simpleTask =
        _ -> {
          int count = counter.incrementAndGet();
          log.info("Executing request #{}", count);
        };

    Workflow rateLimited =
        RateLimitedWorkflow.builder()
            .name("Resilience4jBasic")
            .workflow(SequentialWorkflow.builder().name("SimpleTask").task(simpleTask).build())
            .rateLimitStrategy(limiter)
            .build();

    WorkflowContext context = new WorkflowContext();
    Instant start = Instant.now();

    // Execute 15 requests (will be rate limited)
    for (int i = 0; i < 15; i++) {
      WorkflowResult result = rateLimited.execute(context);
      log.info("Request {} completed with status: {}", i + 1, result.getStatus());
    }

    Duration elapsed = Duration.between(start, Instant.now());
    log.info("Completed 15 requests in {}ms", elapsed.toMillis());
    logAverage(elapsed);
  }

  private static void logAverage(Duration elapsed) {
    log.info("Average rate: {} req/sec", (15.0 / (elapsed.toMillis() / 1000.0)));
  }

  private static void customConfigExample() {
    log.info("\n--- Example 2: Custom Configuration with Timeout ---");
    log.info("Custom Resilience4j config: 5 req/sec with 3-second timeout");

    // Create custom configuration
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(5) // 5 requests
            .limitRefreshPeriod(Duration.ofSeconds(1)) // per second
            .timeoutDuration(Duration.ofSeconds(3)) // wait up to 3 seconds
            .build();

    RateLimitStrategy limiter = new Resilience4jRateLimiter("customLimiter", config);

    Task task =
        _ -> log.info("Processing with custom config at {}", System.currentTimeMillis() % 10000);

    Workflow workflow =
        RateLimitedWorkflow.builder()
            .workflow(SequentialWorkflow.builder().task(task).build())
            .rateLimitStrategy(limiter)
            .build();

    WorkflowContext context = new WorkflowContext();

    // Execute 8 requests
    for (int i = 0; i < 8; i++) {
      workflow.execute(context);
    }

    log.info("Custom configuration example completed");
  }

  private static void eventMonitoringExample() {
    log.info("\n--- Example 3: Event Monitoring and Metrics ---");
    log.info("Monitor Resilience4j events and collect metrics");

    // Create rate limiter
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(5)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(2))
            .build();

    RateLimiter r4jLimiter = RateLimiter.of("monitoredLimiter", config);

    // Register event listeners
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    r4jLimiter
        .getEventPublisher()
        .onSuccess(
            event -> {
              successCount.incrementAndGet();
              log.info("✓ Success event - Available permits: {}", event.getNumberOfPermits());
            });

    r4jLimiter
        .getEventPublisher()
        .onFailure(
            _ -> {
              failureCount.incrementAndGet();
              log.warn("✗ Failure event - Acquisition failed");
            });

    // Wrap in workflow strategy
    RateLimitStrategy limiter = new Resilience4jRateLimiter(r4jLimiter);

    Task task = _ -> log.info("Executing monitored task");

    Workflow workflow =
        RateLimitedWorkflow.builder()
            .workflow(SequentialWorkflow.builder().task(task).build())
            .rateLimitStrategy(limiter)
            .build();

    WorkflowContext context = new WorkflowContext();

    // Execute requests and some will fail due to rate limit
    for (int i = 0; i < 10; i++) {
      workflow.execute(context);
      // Don't block - some will fail immediately
      if (i < 5) {
        try {
          Thread.sleep(50);
        } catch (InterruptedException _) {
          Thread.currentThread().interrupt();
        }
      }
    }

    // Display metrics
    log.info("\n=== Metrics Summary ===");
    log.info("Success events: {}", successCount.get());
    log.info("Failure events: {}", failureCount.get());
    logAvailablePermits(r4jLimiter);
    log.info("Waiting threads: {}", r4jLimiter.getMetrics().getNumberOfWaitingThreads());
  }

  private static void logAvailablePermits(RateLimiter r4jLimiter) {
    log.info("Available permits: {}", r4jLimiter.getMetrics().getAvailablePermissions());
  }

  private static void concurrentExecutionExample() {
    log.info("\n--- Example 4: Concurrent Execution ---");
    log.info("Multiple threads competing for rate-limited resource");

    // Rate limiter: 20 requests per second
    RateLimitStrategy limiter = new Resilience4jRateLimiter(20, Duration.ofSeconds(1));

    AtomicInteger taskCounter = new AtomicInteger(0);
    Task task =
        _ -> {
          int count = taskCounter.incrementAndGet();
          log.info("Thread {} executing task #{}", Thread.currentThread().getName(), count);
        };

    Workflow workflow =
        RateLimitedWorkflow.builder()
            .workflow(SequentialWorkflow.builder().task(task).build())
            .rateLimitStrategy(limiter)
            .build();

    // Execute from multiple threads
    int threadCount = 5;
    int requestsPerThread = 6;
    Instant start = Instant.now();
    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
      CountDownLatch latch = new CountDownLatch(threadCount);

      for (int i = 0; i < threadCount; i++) {
        executor.submit(
            () -> {
              WorkflowContext context = new WorkflowContext();
              for (int j = 0; j < requestsPerThread; j++) {
                workflow.execute(context);
              }
              latch.countDown();
            });
      }

      try {
        latch.await();
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }

      executor.shutdown();
    }

    Duration elapsed = Duration.between(start, Instant.now());
    int totalRequests = threadCount * requestsPerThread;
    log.info("Completed {} concurrent requests in {}ms", totalRequests, elapsed.toMillis());
    logAverage(elapsed);
  }

  private static void existingRateLimiterExample() {
    log.info("\n--- Example 5: Integration with Existing Resilience4j Setup ---");
    log.info("Reuse existing Resilience4j RateLimiter instance");

    // Simulate existing Resilience4j setup in your application
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(15)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(5))
            .build();

    // Your existing rate limiter instance
    RateLimiter existingLimiter = RateLimiter.of("existingAppLimiter", config);

    // Integrate with workflow engine
    RateLimitStrategy limiter = new Resilience4jRateLimiter(existingLimiter);

    Task task = _ -> log.info("Using existing rate limiter configuration");

    Workflow workflow =
        RateLimitedWorkflow.builder()
            .workflow(SequentialWorkflow.builder().task(task).build())
            .rateLimitStrategy(limiter)
            .build();

    WorkflowContext context = new WorkflowContext();

    for (int i = 0; i < 20; i++) {
      workflow.execute(context);
    }

    log.info("Integrated with existing Resilience4j setup successfully");
  }

  private static void productionApiExample() {
    log.info("\n--- Example 6: Production API Throttling ---");
    log.info("Real-world API rate limiting with Resilience4j");

    // Configuration for production API (e.g., GitHub API: 5000 req/hour)
    // For demo purposes, we'll use 50 req/10sec
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(50)
            .limitRefreshPeriod(Duration.ofSeconds(10))
            .timeoutDuration(Duration.ofMinutes(1)) // Longer timeout for production
            .build();

    RateLimiter r4jLimiter = RateLimiter.of("productionAPI", config);

    // Add monitoring
    r4jLimiter
        .getEventPublisher()
        .onEvent(
            event -> {
              if (event instanceof RateLimiterOnSuccessEvent) {
                log.debug("API call permitted");
              } else if (event instanceof RateLimiterOnFailureEvent) {
                log.warn("API call rejected - rate limit exceeded");
              }
            });

    Resilience4jRateLimiter limiter = new Resilience4jRateLimiter(r4jLimiter);

    // Simulate API calls
    Task apiCallTask =
        context -> {
          // Simulate API call
          String endpoint = context.getTyped("endpoint", String.class);
          log.info("Making API call to: {}", endpoint);

          // Simulate processing
          try {
            Thread.sleep(10);
          } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
          }
        };

    Workflow apiWorkflow =
        RateLimitedWorkflow.builder()
            .name("ProductionAPI")
            .workflow(SequentialWorkflow.builder().task(apiCallTask).build())
            .rateLimitStrategy(limiter)
            .build();

    // Simulate multiple API calls
    String[] endpoints = {"/api/users", "/api/repos", "/api/issues", "/api/pulls", "/api/commits"};

    Instant start = Instant.now();

    for (int i = 0; i < 60; i++) {
      WorkflowContext context = new WorkflowContext();
      context.put("endpoint", endpoints[i % endpoints.length]);
      apiWorkflow.execute(context);
    }

    Duration elapsed = Duration.between(start, Instant.now());

    log.info("\n=== Production API Statistics ===");
    log.info("Total API calls: 60");
    log.info("Time taken: {}ms", elapsed.toMillis());
    logAverage(elapsed);
    logAvailablePermits(r4jLimiter);

    // Show how to check metrics
    log.info("Limiter name: {}", limiter.getName());
    log.info("Limit for period: {}", limiter.getLimitForPeriod());
    log.info("Refresh period: {}", limiter.getLimitRefreshPeriod());
  }
}
