package com.workflow.chaos;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.ChaosException;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Chaos strategy that randomly injects failures based on a configured probability.
 *
 * <p>This strategy simulates random failures to test error handling, retry mechanisms, and fallback
 * behavior. Each execution has a probability of failing with a {@link ChaosException}.
 *
 * <p><b>Configuration:</b>
 *
 * <ul>
 *   <li><b>probability</b>: Failure probability (0.0 to 1.0). Default: 0.5
 *   <li><b>errorMessage</b>: Custom error message. Default: "Chaos failure injected"
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe using {@link ThreadLocalRandom}.
 *
 * <p><b>Example usage - 30% Failure Rate:</b>
 *
 * <pre>{@code
 * ChaosStrategy strategy = FailureInjectionStrategy.builder()
 *     .probability(0.3)  // 30% chance of failure
 *     .errorMessage("Simulated service unavailable")
 *     .build();
 *
 * Workflow chaosWorkflow = ChaosWorkflow.builder()
 *     .name("UnreliableService")
 *     .workflow(apiWorkflow)
 *     .strategy(strategy)
 *     .build();
 *
 * // Test with retry mechanism
 * Workflow resilient = SequentialWorkflow.builder()
 *     .task(TaskDescriptor.builder()
 *         .task(new ExecuteWorkflowTask(chaosWorkflow))
 *         .retryPolicy(RetryPolicy.limitedRetries(3))
 *         .build())
 *     .build();
 * }</pre>
 *
 * <p><b>Example usage - Testing Fallback:</b>
 *
 * <pre>{@code
 * Workflow unreliablePrimary = ChaosWorkflow.builder()
 *     .workflow(primaryService)
 *     .strategy(FailureInjectionStrategy.builder().probability(0.7).build())
 *     .build();
 *
 * Workflow fallbackWorkflow = FallbackWorkflow.builder()
 *     .primary(unreliablePrimary)
 *     .fallback(backupService)
 *     .build();
 *
 * // Should frequently use fallback due to 70% failure rate
 * WorkflowResult result = fallbackWorkflow.execute(context);
 * }</pre>
 *
 * @see ChaosStrategy
 * @see ChaosException
 */
@Slf4j
@Builder
@Getter
public class FailureInjectionStrategy implements ChaosStrategy {
  /** Probability of injecting failure (0.0 to 1.0). Default: 0.5 */
  @Builder.Default private final double probability = 0.5;

  /** Custom error message for the injected failure. */
  @Builder.Default private final String errorMessage = "Chaos failure injected";

  @Override
  public void apply(WorkflowContext context) throws ChaosException {
    if (shouldApply()) {
      log.debug("FailureInjectionStrategy triggering failure (probability={})", probability);
      ChaosException exception = new ChaosException(errorMessage);
      exception.addMetadata("strategy", getStrategyName());
      exception.addMetadata("probability", probability);
      throw exception;
    }
  }

  @Override
  public String getStrategyName() {
    return "FailureInjection";
  }

  @Override
  public boolean shouldApply() {
    double random = ThreadLocalRandom.current().nextDouble();
    return random < probability;
  }

  /**
   * Create a strategy with specified failure probability.
   *
   * @param probability failure probability (0.0 to 1.0)
   * @return new strategy instance
   */
  public static FailureInjectionStrategy withProbability(double probability) {
    return builder().probability(probability).build();
  }

  /**
   * Create a strategy that always fails (probability = 1.0).
   *
   * @return new strategy instance that always fails
   */
  public static FailureInjectionStrategy alwaysFail() {
    return builder().probability(1.0).build();
  }

  /**
   * Create a strategy that never fails (probability = 0.0).
   *
   * @return new strategy instance that never fails
   */
  public static FailureInjectionStrategy neverFail() {
    return builder().probability(0.0).build();
  }
}
