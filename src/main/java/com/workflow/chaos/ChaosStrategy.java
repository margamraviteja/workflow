package com.workflow.chaos;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.ChaosException;

/**
 * Strategy interface for injecting chaos into workflow execution.
 *
 * <p>Chaos strategies enable resilience testing by introducing controlled failures, latency, and
 * other anomalies into workflow execution. Implementations can inject various types of chaos:
 *
 * <ul>
 *   <li>Failure injection - randomly fail executions
 *   <li>Latency injection - introduce artificial delays
 *   <li>Exception injection - throw specific exceptions
 *   <li>Resource exhaustion - simulate resource constraints
 * </ul>
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe as they may be called concurrently
 * from multiple threads.
 *
 * <p><b>Example implementation:</b>
 *
 * <pre>{@code
 * public class CustomChaosStrategy implements ChaosStrategy {
 *     @Override
 *     public void apply(WorkflowContext context) {
 *         if (shouldInjectChaos()) {
 *             throw new ChaosException("Custom chaos injected");
 *         }
 *     }
 *
 *     @Override
 *     public String getStrategyName() {
 *         return "CustomChaos";
 *     }
 * }
 * }</pre>
 *
 * @see FailureInjectionStrategy
 */
public interface ChaosStrategy {
  /**
   * Apply chaos to the workflow execution.
   *
   * <p>This method is called before the wrapped workflow executes. Implementations should inject
   * chaos based on their configured behavior (e.g., throw exceptions, introduce delays).
   *
   * @param context the workflow context; can be used to make chaos decisions
   * @throws ChaosException if chaos should interrupt execution
   */
  void apply(WorkflowContext context) throws ChaosException;

  /**
   * Get the human-readable name of this chaos strategy.
   *
   * @return strategy name for logging and debugging
   */
  String getStrategyName();

  /**
   * Determine if this strategy should activate for the current execution.
   *
   * <p>Default implementation returns true. Override to implement probability-based or conditional
   * chaos injection.
   *
   * @return true if chaos should be applied, false otherwise
   */
  default boolean shouldApply() {
    return true;
  }
}
