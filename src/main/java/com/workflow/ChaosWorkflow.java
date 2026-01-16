package com.workflow;

import com.workflow.chaos.ChaosStrategy;
import com.workflow.context.WorkflowContext;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Workflow wrapper that applies chaos engineering strategies for resilience testing.
 *
 * <p>This workflow wraps another workflow and injects controlled failures, latency, exceptions, or
 * resource constraints to test system resilience, error handling, timeouts, retries, and fallback
 * mechanisms. Essential for validating that production systems can handle real-world failure
 * scenarios.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li><b>Multiple Strategies:</b> Compose multiple chaos strategies (executed in order)
 *   <li><b>Probability-Based:</b> Control chaos injection with configurable probabilities
 *   <li><b>Transparent:</b> Wraps any workflow without modification
 *   <li><b>Observable:</b> Chaos events include metadata for test validation
 * </ul>
 *
 * <p><b>Thread Safety:</b> This workflow is thread-safe if the wrapped workflow and strategies are
 * thread-safe (all built-in strategies are thread-safe).
 *
 * <p><b>Common Use Cases:</b>
 *
 * <ul>
 *   <li>Testing retry policies under random failures
 *   <li>Validating timeout handling with latency injection
 *   <li>Testing fallback mechanisms with high failure rates
 *   <li>Simulating production anomalies in test environments
 *   <li>Chaos engineering experiments for resilience validation
 * </ul>
 *
 * <p><b>Example usage - Simple Failure Injection:</b>
 *
 * <pre>{@code
 * Workflow unreliableService = ChaosWorkflow.builder()
 *     .name("UnreliableAPI")
 *     .workflow(apiWorkflow)
 *     .strategy(FailureInjectionStrategy.builder()
 *         .probability(0.3)  // 30% failure rate
 *         .build())
 *     .build();
 *
 * // Test with retry policy
 * TaskDescriptor resilientTask = TaskDescriptor.builder()
 *     .task(new ExecuteWorkflowTask(unreliableService))
 *     .retryPolicy(RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100)))
 *     .build();
 * }</pre>
 *
 * <p><b>Example usage - Latency Testing:</b>
 *
 * <pre>{@code
 * Workflow slowService = ChaosWorkflow.builder()
 *     .workflow(databaseWorkflow)
 *     .strategy(LatencyInjectionStrategy.builder()
 *         .minDelayMs(500)
 *         .maxDelayMs(2000)
 *         .probability(0.5)
 *         .build())
 *     .build();
 *
 * // Wrap with timeout to test timeout handling
 * Workflow timedWorkflow = TimeoutWorkflow.builder()
 *     .workflow(slowService)
 *     .timeoutMs(1000)
 *     .build();
 *
 * WorkflowResult result = timedWorkflow.execute(context);
 * // Some executions will timeout due to chaos latency
 * }</pre>
 *
 * <p><b>Example usage - Multiple Strategies:</b>
 *
 * <pre>{@code
 * // Apply multiple chaos strategies in sequence
 * Workflow chaosWorkflow = ChaosWorkflow.builder()
 *     .name("MultiChaos")
 *     .workflow(serviceWorkflow)
 *     .strategy(LatencyInjectionStrategy.withFixedDelay(100))
 *     .strategy(FailureInjectionStrategy.withProbability(0.2))
 *     .strategy(ExceptionInjectionStrategy.builder()
 *         .exceptionSupplier(() -> new IllegalStateException("Chaos!"))
 *         .probability(0.1)
 *         .build())
 *     .build();
 * }</pre>
 *
 * <p><b>Example usage - Testing Fallback:</b>
 *
 * <pre>{@code
 * // Primary service with high chaos failure rate
 * Workflow unreliablePrimary = ChaosWorkflow.builder()
 *     .workflow(primaryService)
 *     .strategy(FailureInjectionStrategy.withProbability(0.7))
 *     .build();
 *
 * // Should frequently trigger fallback
 * Workflow resilient = FallbackWorkflow.builder()
 *     .primary(unreliablePrimary)
 *     .fallback(backupService)
 *     .build();
 *
 * // Verify fallback is used due to chaos
 * WorkflowResult result = resilient.execute(context);
 * assertTrue(result.getStatus() == WorkflowStatus.SUCCESS);
 * }</pre>
 *
 * <p><b>Example usage - Saga Compensation Testing:</b>
 *
 * <pre>{@code
 * // Test saga compensation by injecting failures
 * Workflow chaoticStep = ChaosWorkflow.builder()
 *     .workflow(paymentStep)
 *     .strategy(FailureInjectionStrategy.withProbability(0.5))
 *     .build();
 *
 * Workflow saga = SagaWorkflow.builder()
 *     .step(inventoryTask, releaseInventoryTask)
 *     .step(chaoticStep, refundTask)  // Will frequently fail and compensate
 *     .step(notificationTask, cancelNotificationTask)
 *     .build();
 * }</pre>
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li>Chaos strategies add minimal overhead when not triggered
 *   <li>Use probability to control chaos frequency
 *   <li>Resource exhaustion strategies can significantly impact performance
 *   <li>Always disable chaos in production unless intentionally testing
 * </ul>
 *
 * <p><b>Best Practices:</b>
 *
 * <ul>
 *   <li>Start with low probabilities and gradually increase
 *   <li>Use feature flags to enable/disable chaos in different environments
 *   <li>Monitor chaos metadata in test results for debugging
 *   <li>Combine with observability tools to validate error handling
 *   <li>Test each resilience mechanism (retry, timeout, fallback) separately
 * </ul>
 *
 * @see ChaosStrategy
 * @see com.workflow.chaos.FailureInjectionStrategy
 * @see com.workflow.chaos.LatencyInjectionStrategy
 * @see com.workflow.chaos.ExceptionInjectionStrategy
 * @see com.workflow.chaos.ResourceExhaustionStrategy
 */
@Slf4j
public class ChaosWorkflow extends AbstractWorkflow implements WorkflowContainer {
  private final String name;
  private final Workflow workflow;
  private final List<ChaosStrategy> strategies;

  /** Private constructor for builder. */
  private ChaosWorkflow(ChaosWorkflowBuilder builder) {
    this.name = builder.name;
    this.workflow = builder.workflow;
    this.strategies = List.copyOf(builder.strategies);
  }

  /**
   * Executes the wrapped workflow with chaos injection.
   *
   * <p>The implementation:
   *
   * <ol>
   *   <li>Applies each chaos strategy in order
   *   <li>If any strategy throws an exception, returns FAILED
   *   <li>If all strategies pass, executes the wrapped workflow
   *   <li>Returns the result from the wrapped workflow
   * </ol>
   *
   * @param context the workflow context passed to strategies and wrapped workflow
   * @param execContext execution context for building results
   * @return the result from the wrapped workflow, or FAILED if chaos is injected
   */
  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    Objects.requireNonNull(workflow, "workflow must not be null");

    // Apply chaos strategies in order
    for (ChaosStrategy strategy : strategies) {
      try {
        log.debug(
            "Applying chaos strategy: {} for workflow: {}", strategy.getStrategyName(), getName());
        strategy.apply(context);
      } catch (Exception e) {
        log.warn(
            "Chaos strategy {} injected failure for workflow {}: {}",
            strategy.getStrategyName(),
            getName(),
            e.getMessage());
        return execContext.failure(e);
      }
    }

    // If no chaos was injected, execute the wrapped workflow
    log.debug("No chaos injected, executing wrapped workflow: {}", workflow.getName());
    return workflow.execute(context);
  }

  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("Chaos");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    return List.of(workflow);
  }

  /**
   * Creates a new builder for {@link ChaosWorkflow}.
   *
   * @return a new builder instance
   */
  public static ChaosWorkflowBuilder builder() {
    return new ChaosWorkflowBuilder();
  }

  /**
   * Builder for {@link ChaosWorkflow}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * ChaosWorkflow workflow = ChaosWorkflow.builder()
   *     .name("ChaosTest")
   *     .workflow(innerWorkflow)
   *     .strategy(FailureInjectionStrategy.withProbability(0.3))
   *     .strategy(LatencyInjectionStrategy.withFixedDelay(100))
   *     .build();
   * }</pre>
   */
  public static class ChaosWorkflowBuilder {
    private String name;
    private Workflow workflow;
    private final List<ChaosStrategy> strategies = new ArrayList<>();

    /**
     * Set the name of the chaos workflow.
     *
     * @param name workflow name
     * @return this builder
     */
    public ChaosWorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Set the workflow to wrap with chaos injection.
     *
     * @param workflow the inner workflow to execute
     * @return this builder
     */
    public ChaosWorkflowBuilder workflow(Workflow workflow) {
      this.workflow = workflow;
      return this;
    }

    /**
     * Add a chaos strategy to apply before workflow execution.
     *
     * <p>Strategies are applied in the order they are added.
     *
     * @param strategy chaos strategy to add
     * @return this builder
     */
    public ChaosWorkflowBuilder strategy(ChaosStrategy strategy) {
      if (strategy != null) {
        this.strategies.add(strategy);
      }
      return this;
    }

    /**
     * Add multiple chaos strategies at once.
     *
     * @param strategies chaos strategies to add
     * @return this builder
     */
    public ChaosWorkflowBuilder strategies(List<ChaosStrategy> strategies) {
      if (strategies != null) {
        this.strategies.addAll(strategies);
      }
      return this;
    }

    /**
     * Build the ChaosWorkflow instance.
     *
     * @return new ChaosWorkflow
     * @throws IllegalArgumentException if workflow is null
     */
    public ChaosWorkflow build() {
      ValidationUtils.requireNonNull(workflow, "workflow");

      if (strategies.isEmpty()) {
        log.warn("ChaosWorkflow built with no strategies - no chaos will be injected");
      }

      return new ChaosWorkflow(this);
    }
  }
}
