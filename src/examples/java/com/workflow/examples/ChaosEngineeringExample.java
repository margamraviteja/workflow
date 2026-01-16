package com.workflow.examples;

import com.workflow.*;
import com.workflow.chaos.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.policy.RetryPolicy;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * Demonstrates chaos engineering patterns for testing workflow resilience.
 *
 * <p>This example shows how to:
 *
 * <ul>
 *   <li>Test retry policies with failure injection
 *   <li>Test timeout handling with latency injection
 *   <li>Test fallback mechanisms with unreliable services
 *   <li>Test saga compensation with chaotic failures
 *   <li>Combine multiple chaos strategies
 * </ul>
 */
@Slf4j
public class ChaosEngineeringExample {

  public static final String SOURCE = "source";

  static void main() {
    ChaosEngineeringExample example = new ChaosEngineeringExample();

    log.info("=== Chaos Engineering Examples ===");

    example.testRetryWithFailureInjection();
    example.testTimeoutWithLatencyInjection();
    example.testFallbackWithUnreliableService();
    example.testMultipleChaosStrategies();
    example.testPerformanceDegradation();

    log.info("=== All examples completed ===");
  }

  /**
   * Test retry policy with random failures.
   *
   * <p>This demonstrates how to validate that retry mechanisms work correctly when services fail
   * randomly.
   */
  public void testRetryWithFailureInjection() {
    log.info("\n--- Testing Retry with Failure Injection ---");

    // Simulate unreliable API (40% failure rate)
    Task apiCallTask =
        context -> {
          log.info("Making API call...");
          context.put("apiResponse", "Success");
        };

    Workflow unreliableAPI =
        ChaosWorkflow.builder()
            .name("UnreliableAPI")
            .workflow(new TaskWorkflow(apiCallTask))
            .strategy(
                FailureInjectionStrategy.builder()
                    .probability(0.4)
                    .errorMessage("API temporarily unavailable")
                    .build())
            .build();

    // Wrap with retry policy
    TaskDescriptor resilientAPI =
        TaskDescriptor.builder()
            .task(new ExecuteWorkflowTask(unreliableAPI))
            .retryPolicy(RetryPolicy.exponentialBackoff(5, 100))
            .build();

    WorkflowContext context = new WorkflowContext();
    Workflow workflow = new TaskWorkflow(resilientAPI);
    WorkflowResult result = workflow.execute(context);

    if (result.getStatus() == WorkflowStatus.SUCCESS) {
      log.info("✓ API call succeeded after retries");
    } else {
      log.warn("✗ API call failed after all retries");
    }
  }

  /**
   * Test timeout handling with latency injection.
   *
   * <p>This validates that timeouts are properly enforced when services become slow.
   */
  public void testTimeoutWithLatencyInjection() {
    log.info("\n--- Testing Timeout with Latency Injection ---");

    Task databaseQuery = context -> context.put("queryResult", "data");

    // Simulate slow database (random 100-2000ms delay)
    Workflow slowDatabase =
        ChaosWorkflow.builder()
            .name("SlowDatabase")
            .workflow(new TaskWorkflow(databaseQuery))
            .strategy(LatencyInjectionStrategy.withRandomDelay(100, 2000))
            .build();

    // Apply 500ms timeout
    Workflow timedQuery = TimeoutWorkflow.builder().workflow(slowDatabase).timeoutMs(500).build();

    WorkflowContext context = new WorkflowContext();
    Instant start = Instant.now();
    WorkflowResult result = timedQuery.execute(context);
    Duration duration = Duration.between(start, Instant.now());

    log.info("Query completed in {}ms with status: {}", duration.toMillis(), result.getStatus());

    if (result.getStatus() == WorkflowStatus.FAILED) {
      log.info("✓ Timeout enforced correctly (query was too slow)");
    } else {
      log.info("✓ Query completed within timeout");
    }
  }

  /**
   * Test fallback mechanism with unreliable primary service.
   *
   * <p>This validates that fallback services are used when primary services fail.
   */
  public void testFallbackWithUnreliableService() {
    log.info("\n--- Testing Fallback with Unreliable Service ---");

    Task primaryService = context -> context.put(SOURCE, "primary");
    Task backupService = context -> context.put(SOURCE, "backup");

    // Primary service with 70% failure rate
    Workflow unreliablePrimary =
        ChaosWorkflow.builder()
            .workflow(new TaskWorkflow(primaryService))
            .strategy(FailureInjectionStrategy.withProbability(0.7))
            .build();

    Workflow resilientService =
        FallbackWorkflow.builder()
            .name("ResilientService")
            .primary(unreliablePrimary)
            .fallback(new TaskWorkflow(backupService))
            .build();

    // Execute multiple times to see fallback in action
    int primaryUsed = 0;
    int fallbackUsed = 0;

    for (int i = 0; i < 20; i++) {
      WorkflowContext context = new WorkflowContext();
      WorkflowResult result = resilientService.execute(context);

      if (result.getStatus() == WorkflowStatus.SUCCESS) {
        String source = (String) context.get(SOURCE);
        if ("primary".equals(source)) {
          primaryUsed++;
        } else {
          fallbackUsed++;
        }
      }
    }

    log.info("✓ Primary used: {} times, Fallback used: {} times", primaryUsed, fallbackUsed);
    log.info("✓ System remained available despite {} failures", fallbackUsed);
  }

  /**
   * Test with multiple chaos strategies combined.
   *
   * <p>This demonstrates complex failure scenarios with multiple chaos types.
   */
  public void testMultipleChaosStrategies() {
    log.info("\n--- Testing Multiple Chaos Strategies ---");

    Task serviceTask = context -> context.put("processed", true);

    Workflow chaosService =
        ChaosWorkflow.builder()
            .name("MultiChaosService")
            .workflow(new TaskWorkflow(serviceTask))
            .strategy(LatencyInjectionStrategy.withFixedDelay(50)) // Always add 50ms delay
            .strategy(FailureInjectionStrategy.withProbability(0.3)) // 30% failure rate
            .strategy(
                ExceptionInjectionStrategy.builder()
                    .exceptionSupplier(() -> new IllegalStateException("State corrupted"))
                    .probability(0.1) // 10% exception rate
                    .build())
            .build();

    int successes = 0;
    int failures = 0;
    long totalDuration = 0;

    for (int i = 0; i < 30; i++) {
      WorkflowContext context = new WorkflowContext();
      Instant start = Instant.now();
      WorkflowResult result = chaosService.execute(context);
      Duration duration = Duration.between(start, Instant.now());
      totalDuration += duration.toMillis();

      if (result.getStatus() == WorkflowStatus.SUCCESS) {
        successes++;
      } else {
        failures++;
      }
    }

    log.info("✓ Results: {} successes, {} failures", successes, failures);
    log.info("✓ Average duration: {}ms (includes 50ms injected latency)", totalDuration / 30);
  }

  /**
   * Test performance degradation simulation.
   *
   * <p>This validates that systems can handle gradual performance degradation.
   */
  public void testPerformanceDegradation() {
    log.info("\n--- Testing Performance Degradation ---");

    Task workTask = context -> context.put("result", "completed");

    // Simulate heavily degraded service
    Workflow degradedService =
        ChaosWorkflow.builder()
            .name("DegradedService")
            .workflow(new TaskWorkflow(workTask))
            .strategy(
                LatencyInjectionStrategy.builder()
                    .minDelayMs(1000)
                    .maxDelayMs(3000)
                    .probability(1.0) // Always slow
                    .build())
            .build();

    WorkflowContext context = new WorkflowContext();
    Instant start = Instant.now();
    degradedService.execute(context);
    Duration duration = Duration.between(start, Instant.now());

    log.info("✓ Service completed in {}ms (degraded performance)", duration.toMillis());

    if (duration.toMillis() >= 1000) {
      log.info("✓ Performance degradation successfully simulated");
    }
  }

  /** Simple task that executes a workflow. */
  private static class ExecuteWorkflowTask implements Task {
    private final Workflow workflow;

    public ExecuteWorkflowTask(Workflow workflow) {
      this.workflow = workflow;
    }

    @Override
    public void execute(WorkflowContext context) {
      WorkflowResult result = workflow.execute(context);
      if (result.getStatus() != WorkflowStatus.SUCCESS) {
        throw new TaskExecutionException(
            "Workflow failed", result.getError() != null ? result.getError() : null);
      }
    }

    @Override
    public String getName() {
      return "ExecuteWorkflow[" + workflow.getName() + "]";
    }
  }
}
