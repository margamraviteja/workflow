package com.workflow.chaos;

import com.workflow.context.WorkflowContext;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Chaos strategy that injects artificial latency into workflow execution.
 *
 * <p>This strategy introduces delays to test timeout handling, performance degradation, and system
 * behavior under slow conditions. Useful for validating that timeouts are properly configured and
 * that systems can handle latency spikes gracefully.
 *
 * <p><b>Configuration:</b>
 *
 * <ul>
 *   <li><b>minDelayMs</b>: Minimum delay in milliseconds. Default: 100ms
 *   <li><b>maxDelayMs</b>: Maximum delay in milliseconds. Default: 1000ms
 *   <li><b>probability</b>: Probability of injecting latency (0.0 to 1.0). Default: 1.0 (always)
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe using {@link ThreadLocalRandom}.
 *
 * <p><b>Example usage - Fixed Latency:</b>
 *
 * <pre>{@code
 * // Always add 500ms delay
 * ChaosStrategy strategy = LatencyInjectionStrategy.builder()
 *     .minDelayMs(500)
 *     .maxDelayMs(500)
 *     .build();
 *
 * Workflow slowWorkflow = ChaosWorkflow.builder()
 *     .workflow(apiWorkflow)
 *     .strategy(strategy)
 *     .build();
 *
 * // Wrap with timeout to test timeout handling
 * Workflow timedWorkflow = TimeoutWorkflow.builder()
 *     .workflow(slowWorkflow)
 *     .timeoutMs(300)  // Should timeout
 *     .build();
 * }</pre>
 *
 * <p><b>Example usage - Variable Latency:</b>
 *
 * <pre>{@code
 * // Random delay between 100ms and 2000ms
 * ChaosStrategy strategy = LatencyInjectionStrategy.builder()
 *     .minDelayMs(100)
 *     .maxDelayMs(2000)
 *     .probability(0.5)  // 50% chance of latency
 *     .build();
 *
 * Workflow unreliableAPI = ChaosWorkflow.builder()
 *     .workflow(apiWorkflow)
 *     .strategy(strategy)
 *     .build();
 * }</pre>
 *
 * <p><b>Example usage - Testing Performance Degradation:</b>
 *
 * <pre>{@code
 * // Simulate gradual performance degradation
 * Workflow degradedService = ChaosWorkflow.builder()
 *     .workflow(serviceWorkflow)
 *     .strategy(LatencyInjectionStrategy.builder()
 *         .minDelayMs(1000)
 *         .maxDelayMs(5000)
 *         .probability(1.0)
 *         .build())
 *     .build();
 *
 * // Measure execution time
 * Instant start = Instant.now();
 * WorkflowResult result = degradedService.execute(context);
 * Duration duration = Duration.between(start, Instant.now());
 * assertTrue(duration.toMillis() >= 1000);
 * }</pre>
 *
 * @see ChaosStrategy
 */
@Slf4j
@Builder
@Getter
public class LatencyInjectionStrategy implements ChaosStrategy {
  /** Minimum delay in milliseconds. Default: 100ms */
  @Builder.Default private final long minDelayMs = 100;

  /** Maximum delay in milliseconds. Default: 1000ms */
  @Builder.Default private final long maxDelayMs = 1000;

  /** Probability of injecting latency (0.0 to 1.0). Default: 1.0 (always) */
  @Builder.Default private final double probability = 1.0;

  @Override
  public void apply(WorkflowContext context) {
    if (shouldApply()) {
      long delayMs = calculateDelay();
      log.debug(
          "LatencyInjectionStrategy injecting {}ms delay (range={}ms-{}ms, probability={})",
          delayMs,
          minDelayMs,
          maxDelayMs,
          probability);

      try {
        Thread.sleep(Duration.ofMillis(delayMs));
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public String getStrategyName() {
    return "LatencyInjection";
  }

  @Override
  public boolean shouldApply() {
    double random = ThreadLocalRandom.current().nextDouble();
    return random < probability;
  }

  /**
   * Calculate random delay within configured range.
   *
   * @return delay in milliseconds
   */
  private long calculateDelay() {
    if (minDelayMs == maxDelayMs) {
      return minDelayMs;
    }
    return ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1);
  }

  /**
   * Create a strategy with fixed delay.
   *
   * @param delayMs delay in milliseconds
   * @return new strategy instance
   */
  public static LatencyInjectionStrategy withFixedDelay(long delayMs) {
    return builder().minDelayMs(delayMs).maxDelayMs(delayMs).build();
  }

  /**
   * Create a strategy with random delay range.
   *
   * @param minDelayMs minimum delay in milliseconds
   * @param maxDelayMs maximum delay in milliseconds
   * @return new strategy instance
   */
  public static LatencyInjectionStrategy withRandomDelay(long minDelayMs, long maxDelayMs) {
    return builder().minDelayMs(minDelayMs).maxDelayMs(maxDelayMs).build();
  }
}
