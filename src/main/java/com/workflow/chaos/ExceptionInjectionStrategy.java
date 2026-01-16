package com.workflow.chaos;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.ChaosException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Chaos strategy that injects specific exceptions into workflow execution.
 *
 * <p>This strategy allows testing how workflows handle specific exception types (e.g.,
 * NullPointerException, IllegalStateException, custom exceptions). Useful for validating exception
 * handling, error recovery, and logging behavior.
 *
 * <p><b>Configuration:</b>
 *
 * <ul>
 *   <li><b>exceptionSupplier</b>: Supplier that creates the exception to throw
 *   <li><b>probability</b>: Probability of throwing the exception (0.0 to 1.0). Default: 1.0
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe if the exception supplier is thread-safe.
 *
 * <p><b>Example usage - NullPointerException:</b>
 *
 * <pre>{@code
 * ChaosStrategy strategy = ExceptionInjectionStrategy.builder()
 *     .exceptionSupplier(() -> new NullPointerException("Simulated NPE"))
 *     .probability(0.2)
 *     .build();
 *
 * Workflow chaosWorkflow = ChaosWorkflow.builder()
 *     .workflow(dataProcessor)
 *     .strategy(strategy)
 *     .build();
 *
 * // Test NPE handling
 * try {
 *     chaosWorkflow.execute(context);
 * } catch (NullPointerException e) {
 *     log.error("Handled NPE: {}", e.getMessage());
 * }
 * }</pre>
 *
 * <p><b>Example usage - Custom Business Exception:</b>
 *
 * <pre>{@code
 * ChaosStrategy strategy = ExceptionInjectionStrategy.builder()
 *     .exceptionSupplier(() -> new InsufficientFundsException("Account overdrawn"))
 *     .probability(0.1)
 *     .build();
 *
 * Workflow paymentWorkflow = ChaosWorkflow.builder()
 *     .name("PaymentWithChaos")
 *     .workflow(processPayment)
 *     .strategy(strategy)
 *     .build();
 * }</pre>
 *
 * <p><b>Example usage - Multiple Exception Types:</b>
 *
 * <pre>{@code
 * // Randomly throw one of several exception types
 * ChaosStrategy strategy = ExceptionInjectionStrategy.builder()
 *     .exceptionSupplier(() -> {
 *         int type = ThreadLocalRandom.current().nextInt(3);
 *         return switch (type) {
 *             case 0 -> new TimeoutException("Connection timeout");
 *             case 1 -> new IOException("Network error");
 *             default -> new SQLException("Database unavailable");
 *         };
 *     })
 *     .probability(0.3)
 *     .build();
 * }</pre>
 *
 * @see ChaosStrategy
 * @see ChaosException
 */
@Slf4j
@Builder
@Getter
public class ExceptionInjectionStrategy implements ChaosStrategy {
  /** Supplier that creates the exception to throw. Required. */
  private final Supplier<Exception> exceptionSupplier;

  /** Probability of throwing the exception (0.0 to 1.0). Default: 1.0 (always) */
  @Builder.Default private final double probability = 1.0;

  @Override
  public void apply(WorkflowContext context) throws ChaosException {
    if (shouldApply()) {
      Exception exception = exceptionSupplier.get();
      log.debug(
          "ExceptionInjectionStrategy throwing {}: {}",
          exception.getClass().getSimpleName(),
          exception.getMessage());

      // Wrap in ChaosException if it's not already one
      if (exception instanceof ChaosException chaosException) {
        chaosException.addMetadata("strategy", getStrategyName());
        chaosException.addMetadata("probability", probability);
        throw chaosException;
      } else {
        ChaosException wrapper = new ChaosException("Chaos exception injection", exception);
        wrapper.addMetadata("strategy", getStrategyName());
        wrapper.addMetadata("probability", probability);
        wrapper.addMetadata("exceptionType", exception.getClass().getName());
        throw wrapper;
      }
    }
  }

  @Override
  public String getStrategyName() {
    return "ExceptionInjection";
  }

  @Override
  public boolean shouldApply() {
    double random = ThreadLocalRandom.current().nextDouble();
    return random < probability;
  }

  /**
   * Create a strategy that always throws the specified exception.
   *
   * @param exception the exception to throw
   * @return new strategy instance
   */
  public static ExceptionInjectionStrategy alwaysThrow(Exception exception) {
    return builder().exceptionSupplier(() -> exception).probability(1.0).build();
  }

  /**
   * Create a strategy that throws the specified exception with given probability.
   *
   * @param exception the exception to throw
   * @param probability probability of throwing (0.0 to 1.0)
   * @return new strategy instance
   */
  public static ExceptionInjectionStrategy throwWithProbability(
      Exception exception, double probability) {
    return builder().exceptionSupplier(() -> exception).probability(probability).build();
  }
}
