package com.workflow.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when chaos engineering strategies inject failures into workflow execution.
 *
 * <p>This exception is used by {@link com.workflow.chaos.ChaosStrategy} implementations to simulate
 * failures and test resilience mechanisms. It includes metadata about the chaos injection for
 * debugging and test validation purposes.
 *
 * <p><b>Metadata:</b> Chaos exceptions can carry metadata about:
 *
 * <ul>
 *   <li>Strategy name that injected the failure
 *   <li>Failure type (timeout, resource exhaustion, random failure, etc.)
 *   <li>Probability or configuration details
 *   <li>Custom attributes for test validation
 * </ul>
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * ChaosException chaos = new ChaosException("Simulated network timeout");
 * chaos.addMetadata("strategy", "LatencyInjection");
 * chaos.addMetadata("delayMs", 5000);
 * chaos.addMetadata("probability", 0.3);
 * throw chaos;
 * }</pre>
 *
 * <p><b>Test Validation:</b>
 *
 * <pre>{@code
 * try {
 *     workflow.execute(context);
 * } catch (ChaosException e) {
 *     assertEquals("FailureInjection", e.getMetadata("strategy"));
 *     assertTrue(e.isChaosInjected());
 * }
 * }</pre>
 *
 * @see com.workflow.chaos.ChaosStrategy
 */
public class ChaosException extends TaskExecutionException {
  private final Map<String, Object> metadata = new HashMap<>();

  /**
   * Constructs a new chaos exception with the specified detail message.
   *
   * @param message the detail message describing the chaos injection
   */
  public ChaosException(String message) {
    super(message);
  }

  /**
   * Constructs a new chaos exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public ChaosException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Add metadata about the chaos injection.
   *
   * @param key metadata key
   * @param value metadata value
   * @return this exception for method chaining
   */
  public ChaosException addMetadata(String key, Object value) {
    metadata.put(key, value);
    return this;
  }

  /**
   * Get metadata value by key.
   *
   * @param key metadata key
   * @return metadata value, or null if not present
   */
  public Object getMetadata(String key) {
    return metadata.get(key);
  }

  /**
   * Get all metadata.
   *
   * @return unmodifiable view of metadata
   */
  public Map<String, Object> getAllMetadata() {
    return Map.copyOf(metadata);
  }

  /**
   * Check if this is a chaos-injected exception (always true for ChaosException).
   *
   * @return true
   */
  public boolean isChaosInjected() {
    return true;
  }

  @Override
  public String toString() {
    if (metadata.isEmpty()) {
      return super.toString();
    }
    return super.toString() + " [metadata=" + metadata + "]";
  }
}
