package com.workflow.helper;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;

/**
 * Utility class for common validation operations across the workflow engine.
 *
 * <p>This class provides centralized validation helpers to reduce duplication and ensure consistent
 * error messages throughout the codebase.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Validate required parameters
 * ValidationUtils.requireNonNull(workflow, "workflow");
 * ValidationUtils.requireNonEmpty(workflows, "workflows list");
 *
 * // Validate with custom messages
 * ValidationUtils.requireNonNull(condition, () -> "Condition is required for " + getName());
 * }</pre>
 */
@UtilityClass
public class ValidationUtils {

  /**
   * Validates that an object is not null.
   *
   * @param obj the object to validate
   * @param paramName the parameter name for error message
   * @param <T> the object type
   * @return the validated object
   * @throws NullPointerException if obj is null
   */
  public static <T> T requireNonNull(T obj, String paramName) {
    return Objects.requireNonNull(obj, paramName + " must not be null");
  }

  /**
   * Validates that an object is not null with a custom message supplier.
   *
   * @param obj the object to validate
   * @param messageSupplier supplier for the error message
   * @param <T> the object type
   * @return the validated object
   * @throws NullPointerException if obj is null
   */
  public static <T> T requireNonNull(T obj, Supplier<String> messageSupplier) {
    return Objects.requireNonNull(obj, messageSupplier);
  }

  /**
   * Validates that a collection is not null and not empty.
   *
   * @param collection the collection to validate
   * @param paramName the parameter name for error message
   * @param <T> the collection type
   * @return the validated collection
   * @throws NullPointerException if collection is null
   * @throws IllegalArgumentException if collection is empty
   */
  public static <T extends Collection<?>> T requireNonEmpty(T collection, String paramName) {
    requireNonNull(collection, paramName);
    if (collection.isEmpty()) {
      throw new IllegalArgumentException(paramName + " must not be empty");
    }
    return collection;
  }

  /**
   * Validates that a map is not null and not empty.
   *
   * @param map the map to validate
   * @param paramName the parameter name for error message
   * @param <T> the map type
   * @return the validated map
   * @throws NullPointerException if map is null
   * @throws IllegalArgumentException if map is empty
   */
  public static <T extends Map<?, ?>> T requireNonEmpty(T map, String paramName) {
    requireNonNull(map, paramName);
    if (map.isEmpty()) {
      throw new IllegalArgumentException(paramName + " must not be empty");
    }
    return map;
  }

  /**
   * Validates that a string is not null and not blank.
   *
   * @param str the string to validate
   * @param paramName the parameter name for error message
   * @return the validated string
   * @throws NullPointerException if str is null
   * @throws IllegalArgumentException if str is blank
   */
  public static String requireNonBlank(String str, String paramName) {
    requireNonNull(str, paramName);
    if (str.isBlank()) {
      throw new IllegalArgumentException(paramName + " must not be blank");
    }
    return str;
  }

  /**
   * Validates that a number is positive.
   *
   * @param value the value to validate
   * @param paramName the parameter name for error message
   * @return the validated value
   * @throws IllegalArgumentException if value is not positive
   */
  public static long requirePositive(long value, String paramName) {
    if (value <= 0) {
      throw new IllegalArgumentException(paramName + " must be positive, got: " + value);
    }
    return value;
  }

  /**
   * Validates that a number is non-negative.
   *
   * @param value the value to validate
   * @param paramName the parameter name for error message
   * @return the validated value
   * @throws IllegalArgumentException if value is negative
   */
  public static long requireNonNegative(long value, String paramName) {
    if (value < 0) {
      throw new IllegalArgumentException(paramName + " must be non-negative, got: " + value);
    }
    return value;
  }

  /**
   * Validates that at least one of the provided objects is non-null.
   *
   * @param errorMessage the error message if all are null
   * @param objects the objects to check
   * @throws IllegalArgumentException if all objects are null
   */
  public static void requireAtLeastOne(String errorMessage, Object... objects) {
    for (Object obj : objects) {
      if (obj != null) {
        return;
      }
    }
    throw new IllegalArgumentException(errorMessage);
  }

  /**
   * Validates a condition is true.
   *
   * @param condition the condition to validate
   * @param errorMessage the error message if condition is false
   * @throws IllegalArgumentException if condition is false
   */
  public static void require(boolean condition, String errorMessage) {
    if (!condition) {
      throw new IllegalArgumentException(errorMessage);
    }
  }

  /**
   * Validates a condition is true with a message supplier.
   *
   * @param condition the condition to validate
   * @param messageSupplier supplier for the error message
   * @throws IllegalArgumentException if condition is false
   */
  public static void require(boolean condition, Supplier<String> messageSupplier) {
    if (!condition) {
      throw new IllegalArgumentException(messageSupplier.get());
    }
  }

  /**
   * Validates that a value is within a specified range (inclusive).
   *
   * @param value the value to validate
   * @param min minimum allowed value (inclusive)
   * @param max maximum allowed value (inclusive)
   * @param paramName the parameter name for error message
   * @return the validated value
   * @throws IllegalArgumentException if value is out of range
   */
  public static long requireInRange(long value, long min, long max, String paramName) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(
          String.format(
              "%s must be between %d and %d (inclusive), got: %d", paramName, min, max, value));
    }
    return value;
  }

  /**
   * Validates that a value is within a specified range (inclusive).
   *
   * @param value the value to validate
   * @param min minimum allowed value (inclusive)
   * @param max maximum allowed value (inclusive)
   * @param paramName the parameter name for error message
   * @return the validated value
   * @throws IllegalArgumentException if value is out of range
   */
  public static double requireInRange(double value, double min, double max, String paramName) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(
          String.format(
              "%s must be between %.2f and %.2f (inclusive), got: %.2f",
              paramName, min, max, value));
    }
    return value;
  }
}
