package com.workflow.helper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ValidationUtilsTest {

  @Test
  void requireNonNull_withNonNullObject_returnsObject() {
    String value = "test";
    String result = ValidationUtils.requireNonNull(value, "testParam");
    assertEquals(value, result);
  }

  @Test
  void requireNonNull_withNullObject_throwsNullPointerException() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class, () -> ValidationUtils.requireNonNull(null, "testParam"));
    assertTrue(exception.getMessage().contains("testParam must not be null"));
  }

  @Test
  void requireNonNull_withSupplier_nonNull_returnsObject() {
    String value = "test";
    String result = ValidationUtils.requireNonNull(value, () -> "Custom error message");
    assertEquals(value, result);
  }

  @Test
  void requireNonNull_withSupplier_null_throwsWithCustomMessage() {
    String customMessage = "Custom error message";
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> ValidationUtils.requireNonNull(null, () -> customMessage));
    assertEquals(customMessage, exception.getMessage());
  }

  @Test
  void requireNonEmpty_withNonEmptyCollection_returnsCollection() {
    List<String> list = Arrays.asList("a", "b", "c");
    List<String> result = ValidationUtils.requireNonEmpty(list, "listParam");
    assertEquals(list, result);
  }

  @Test
  void requireNonEmpty_withEmptyCollection_throwsIllegalArgumentException() {
    List<String> emptyList = Collections.emptyList();
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.requireNonEmpty(emptyList, "listParam"));
    assertTrue(exception.getMessage().contains("listParam must not be empty"));
  }

  @Test
  void requireNonEmpty_withNullCollection_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> ValidationUtils.requireNonEmpty((List<String>) null, "listParam"));
  }

  @Test
  void requireNonEmpty_withNonEmptyMap_returnsMap() {
    Map<String, String> map = Map.of("key", "value");
    Map<String, String> result = ValidationUtils.requireNonEmpty(map, "mapParam");
    assertEquals(map, result);
  }

  @Test
  void requireNonEmpty_withEmptyMap_throwsIllegalArgumentException() {
    Map<String, String> emptyMap = Collections.emptyMap();
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.requireNonEmpty(emptyMap, "mapParam"));
    assertTrue(exception.getMessage().contains("mapParam must not be empty"));
  }

  @Test
  void requireNonEmpty_withNullMap_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> ValidationUtils.requireNonEmpty((Map<String, String>) null, "mapParam"));
  }

  @Test
  void requireNonBlank_withNonBlankString_returnsString() {
    String value = "test";
    String result = ValidationUtils.requireNonBlank(value, "strParam");
    assertEquals(value, result);
  }

  @Test
  void requireNonBlank_withBlankString_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.requireNonBlank("   ", "strParam"));
    assertTrue(exception.getMessage().contains("strParam must not be blank"));
  }

  @Test
  void requireNonBlank_withEmptyString_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> ValidationUtils.requireNonBlank("", "strParam"));
    assertTrue(exception.getMessage().contains("strParam must not be blank"));
  }

  @Test
  void requireNonBlank_withNullString_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class, () -> ValidationUtils.requireNonBlank(null, "strParam"));
  }

  @Test
  void requirePositive_withPositiveValue_returnsValue() {
    long value = 10L;
    long result = ValidationUtils.requirePositive(value, "numParam");
    assertEquals(value, result);
  }

  @Test
  void requirePositive_withZero_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> ValidationUtils.requirePositive(0L, "numParam"));
    assertTrue(exception.getMessage().contains("numParam must be positive"));
  }

  @Test
  void requirePositive_withNegativeValue_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> ValidationUtils.requirePositive(-5L, "numParam"));
    assertTrue(exception.getMessage().contains("numParam must be positive"));
  }

  @Test
  void requireNonNegative_withPositiveValue_returnsValue() {
    long value = 10L;
    long result = ValidationUtils.requireNonNegative(value, "numParam");
    assertEquals(value, result);
  }

  @Test
  void requireNonNegative_withZero_returnsZero() {
    long result = ValidationUtils.requireNonNegative(0L, "numParam");
    assertEquals(0L, result);
  }

  @Test
  void requireNonNegative_withNegativeValue_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.requireNonNegative(-5L, "numParam"));
    assertTrue(exception.getMessage().contains("numParam must be non-negative"));
  }

  @Test
  void requireAtLeastOne_withAtLeastOneNonNull_succeeds() {
    assertDoesNotThrow(() -> ValidationUtils.requireAtLeastOne("Error", "value", null, null));
  }

  @Test
  void requireAtLeastOne_withAllNull_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.requireAtLeastOne("At least one value required", null, null));
    assertEquals("At least one value required", exception.getMessage());
  }

  @Test
  void require_withTrueCondition_succeeds() {
    assertDoesNotThrow(() -> ValidationUtils.require(true, "Error message"));
  }

  @Test
  void require_withFalseCondition_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.require(false, "Condition failed"));
    assertEquals("Condition failed", exception.getMessage());
  }

  @Test
  void require_withSupplier_trueCondition_succeeds() {
    assertDoesNotThrow(() -> ValidationUtils.require(true, () -> "Error message"));
  }

  @Test
  void require_withSupplier_falseCondition_throwsWithSuppliedMessage() {
    String message = "Condition not met";
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> ValidationUtils.require(false, () -> message));
    assertEquals(message, exception.getMessage());
  }

  @Test
  void requireInRange_long_atMinBoundary_returnsValue() {
    long value = 1L;
    long result = ValidationUtils.requireInRange(value, 1L, 10L, "numParam");
    assertEquals(value, result);
  }

  @Test
  void requireInRange_long_atMaxBoundary_returnsValue() {
    long value = 10L;
    long result = ValidationUtils.requireInRange(value, 1L, 10L, "numParam");
    assertEquals(value, result);
  }

  @Test
  void requireInRange_long_belowMin_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.requireInRange(0L, 1L, 10L, "numParam"));
    assertTrue(exception.getMessage().contains("must be between 1 and 10"));
  }

  @Test
  void requireInRange_long_aboveMax_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.requireInRange(11L, 1L, 10L, "numParam"));
    assertTrue(exception.getMessage().contains("must be between 1 and 10"));
  }

  @Test
  void requireInRange_double_atMinBoundary_returnsValue() {
    double value = 1.0;
    double result = ValidationUtils.requireInRange(value, 1.0, 10.0, "numParam");
    assertEquals(value, result);
  }

  @Test
  void requireInRange_double_atMaxBoundary_returnsValue() {
    double value = 10.0;
    double result = ValidationUtils.requireInRange(value, 1.0, 10.0, "numParam");
    assertEquals(value, result);
  }

  @Test
  void requireInRange_double_belowMin_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.requireInRange(0.5, 1.0, 10.0, "numParam"));
    assertTrue(exception.getMessage().contains("must be between"));
  }

  @Test
  void requireInRange_double_aboveMax_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.requireInRange(10.5, 1.0, 10.0, "numParam"));
    assertTrue(exception.getMessage().contains("must be between"));
  }
}
