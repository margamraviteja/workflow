package com.workflow.chaos;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.ChaosException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Chaos strategy that simulates resource exhaustion scenarios.
 *
 * <p>This strategy simulates resource constraints like memory pressure, CPU exhaustion, or
 * connection pool depletion. Useful for testing how workflows behave under resource constraints and
 * validating resource cleanup and error handling.
 *
 * <p><b>Simulation Types:</b>
 *
 * <ul>
 *   <li><b>MEMORY</b>: Allocate large arrays to consume memory
 *   <li><b>CPU</b>: Perform intensive calculations to consume CPU
 *   <li><b>THREADS</b>: Create thread pressure (simulated via CPU work)
 * </ul>
 *
 * <p><b>Configuration:</b>
 *
 * <ul>
 *   <li><b>resourceType</b>: Type of resource to exhaust. Default: MEMORY
 *   <li><b>intensity</b>: Intensity level (LOW, MEDIUM, HIGH). Default: MEDIUM
 *   <li><b>probability</b>: Probability of applying (0.0 to 1.0). Default: 1.0
 *   <li><b>throwException</b>: Whether to throw exception after exhaustion. Default: true
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe.
 *
 * <p><b>Example usage - Memory Pressure:</b>
 *
 * <pre>{@code
 * ChaosStrategy strategy = ResourceExhaustionStrategy.builder()
 *     .resourceType(ResourceType.MEMORY)
 *     .intensity(Intensity.HIGH)
 *     .probability(0.3)
 *     .build();
 *
 * Workflow chaosWorkflow = ChaosWorkflow.builder()
 *     .workflow(memoryIntensiveWorkflow)
 *     .strategy(strategy)
 *     .build();
 *
 * // Test memory handling
 * try {
 *     chaosWorkflow.execute(context);
 * } catch (ChaosException e) {
 *     assertEquals("MEMORY", e.getMetadata("resourceType"));
 * }
 * }</pre>
 *
 * <p><b>Example usage - CPU Exhaustion:</b>
 *
 * <pre>{@code
 * ChaosStrategy strategy = ResourceExhaustionStrategy.builder()
 *     .resourceType(ResourceType.CPU)
 *     .intensity(Intensity.MEDIUM)
 *     .throwException(false)  // Just slow down, don't fail
 *     .build();
 *
 * Workflow slowWorkflow = ChaosWorkflow.builder()
 *     .workflow(computeWorkflow)
 *     .strategy(strategy)
 *     .build();
 * }</pre>
 *
 * <p><b>Warning:</b> HIGH intensity can significantly impact JVM performance. Use with caution in
 * production-like environments.
 *
 * @see ChaosStrategy
 * @see ChaosException
 */
@Slf4j
@Builder
@Getter
public class ResourceExhaustionStrategy implements ChaosStrategy {
  /** Type of resource to exhaust. */
  public enum ResourceType {
    MEMORY,
    CPU,
    THREADS
  }

  /** Intensity level of resource exhaustion. */
  public enum Intensity {
    LOW,
    MEDIUM,
    HIGH
  }

  /** Type of resource to exhaust. Default: MEMORY */
  @Builder.Default private final ResourceType resourceType = ResourceType.MEMORY;

  /** Intensity level. Default: MEDIUM */
  @Builder.Default private final Intensity intensity = Intensity.MEDIUM;

  /** Probability of applying (0.0 to 1.0). Default: 1.0 */
  @Builder.Default private final double probability = 1.0;

  /** Whether to throw exception after exhaustion. Default: true */
  @Builder.Default private final boolean throwException = true;

  @Override
  public void apply(WorkflowContext context) throws ChaosException {
    if (shouldApply()) {
      log.debug(
          "ResourceExhaustionStrategy simulating {} exhaustion at {} intensity",
          resourceType,
          intensity);

      switch (resourceType) {
        case MEMORY -> simulateMemoryPressure();
        case CPU -> simulateCpuExhaustion();
        case THREADS -> simulateThreadPressure();
      }

      if (throwException) {
        ChaosException exception =
            new ChaosException("Resource exhaustion simulated: " + resourceType);
        exception.addMetadata("strategy", getStrategyName());
        exception.addMetadata("resourceType", resourceType.name());
        exception.addMetadata("intensity", intensity.name());
        exception.addMetadata("probability", probability);
        throw exception;
      }
    }
  }

  @Override
  public String getStrategyName() {
    return "ResourceExhaustion";
  }

  @Override
  public boolean shouldApply() {
    double random = ThreadLocalRandom.current().nextDouble();
    return random < probability;
  }

  /** Simulate memory pressure by allocating temporary arrays. */
  private void simulateMemoryPressure() {
    int size =
        switch (intensity) {
          case LOW -> 1_000_000; // ~4MB
          case MEDIUM -> 10_000_000; // ~40MB
          case HIGH -> 50_000_000; // ~200MB
        };

    try {
      List<byte[]> garbage = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        garbage.add(new byte[size / 5]);
      }
      assert garbage.getFirst() != null;
      // Force some GC pressure by clearing
      garbage.clear();
    } catch (OutOfMemoryError e) {
      log.warn("Actual OOM during memory pressure simulation", e);
    }
  }

  /** Simulate CPU exhaustion by performing intensive calculations. */
  private void simulateCpuExhaustion() {
    int iterations =
        switch (intensity) {
          case LOW -> 1_000_000;
          case MEDIUM -> 10_000_000;
          case HIGH -> 100_000_000;
        };

    // Perform meaningless but CPU-intensive calculations
    double result = 0;
    for (int i = 0; i < iterations; i++) {
      result += Math.sqrt(i) * Math.sin(i);
    }

    // Prevent optimization
    if (result > Double.MAX_VALUE) {
      log.trace("CPU exhaustion result: {}", result);
    }
  }

  /** Simulate thread pressure (currently delegates to CPU exhaustion). */
  private void simulateThreadPressure() {
    // In a real scenario, this could create threads, but that's dangerous
    // Instead, we'll just simulate the CPU impact
    simulateCpuExhaustion();
  }

  /**
   * Create a strategy for memory pressure simulation.
   *
   * @param intensity intensity level
   * @return new strategy instance
   */
  public static ResourceExhaustionStrategy memoryPressure(Intensity intensity) {
    return builder().resourceType(ResourceType.MEMORY).intensity(intensity).build();
  }

  /**
   * Create a strategy for CPU exhaustion simulation.
   *
   * @param intensity intensity level
   * @return new strategy instance
   */
  public static ResourceExhaustionStrategy cpuExhaustion(Intensity intensity) {
    return builder().resourceType(ResourceType.CPU).intensity(intensity).build();
  }
}
