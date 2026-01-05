package com.workflow.examples;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.task.Task;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Example demonstrating the Circuit Breaker pattern for fault-tolerant workflows.
 *
 * <p>The Circuit Breaker pattern prevents cascading failures by monitoring errors and temporarily
 * stopping calls to a failing service, giving it time to recover.
 *
 * <p><b>Circuit States:</b>
 *
 * <ul>
 *   <li><b>CLOSED:</b> Normal operation, requests pass through
 *   <li><b>OPEN:</b> Too many failures, requests are rejected immediately
 *   <li><b>HALF_OPEN:</b> Testing if service has recovered
 * </ul>
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Protecting external API calls
 *   <li>Database connection management
 *   <li>Microservice communication
 *   <li>Preventing cascade failures
 * </ul>
 */
@Slf4j
public class CircuitBreakerWorkflowExample {

  enum CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  static class CircuitBreakerTask implements Task {
    private final Task delegate;
    private final int failureThreshold;
    private final long resetTimeoutMs;

    @Getter private CircuitState state = CircuitState.CLOSED;
    private int failureCount = 0;
    private long lastFailureTime = 0;

    public CircuitBreakerTask(Task delegate, int failureThreshold, long resetTimeoutMs) {
      this.delegate = delegate;
      this.failureThreshold = failureThreshold;
      this.resetTimeoutMs = resetTimeoutMs;
    }

    @Override
    public void execute(WorkflowContext context) {
      if (state == CircuitState.OPEN) {
        if (System.currentTimeMillis() - lastFailureTime > resetTimeoutMs) {
          log.info("Circuit breaker transitioning to HALF_OPEN");
          state = CircuitState.HALF_OPEN;
        } else {
          throw new TaskExecutionException("Circuit breaker is OPEN - rejecting request");
        }
      }

      try {
        delegate.execute(context);
        onSuccess();
      } catch (Exception e) {
        onFailure();
        throw e;
      }
    }

    private void onSuccess() {
      if (state == CircuitState.HALF_OPEN) {
        log.info("Circuit breaker transitioning to CLOSED");
        state = CircuitState.CLOSED;
      }
      failureCount = 0;
    }

    private void onFailure() {
      failureCount++;
      lastFailureTime = System.currentTimeMillis();

      if (failureCount >= failureThreshold) {
        log.warn("Circuit breaker transitioning to OPEN after {} failures", failureCount);
        state = CircuitState.OPEN;
      }
    }
  }

  public static void main(String[] args) {
    log.info("=== Circuit Breaker Pattern Example ===");

    // Simulate an unreliable external service
    Task unreliableService =
        context -> {
          double random = Math.random();
          if (random < 0.5) {
            throw new TaskExecutionException("Service temporarily unavailable");
          }
          log.info("Service call succeeded");
          context.put("serviceResponse", "Success");
        };

    // Wrap with circuit breaker (opens after 3 failures, resets after 5 seconds)
    CircuitBreakerTask circuitBreakerTask = new CircuitBreakerTask(unreliableService, 3, 5000);

    Workflow workflow =
        SequentialWorkflow.builder()
            .name("CircuitBreakerWorkflow")
            .task(circuitBreakerTask)
            .build();

    // Simulate multiple requests
    for (int i = 0; i < 10; i++) {
      log.info("\n--- Request {} ---", i + 1);
      WorkflowContext context = new WorkflowContext();

      WorkflowResult result = workflow.execute(context);

      log.info("Result: {} | Circuit State: {}", result.getStatus(), circuitBreakerTask.getState());

      // Wait briefly between requests
      try {
        Thread.sleep(500);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
    }

    log.info("\n=== Circuit Breaker Example Complete ===");
  }
}
