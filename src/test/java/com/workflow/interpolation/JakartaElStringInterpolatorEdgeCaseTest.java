package com.workflow.interpolation;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.TestContexts;
import com.workflow.context.WorkflowContext;
import com.workflow.interpolation.exception.InterpolationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive edge case tests for JakartaElStringInterpolator string interpolation component.
 *
 * <p>Tests cover complex edge cases, error scenarios, performance considerations, and advanced
 * interpolation patterns not covered in the basic test suite.
 */
@DisplayName("JakartaElStringInterpolator Edge Case Tests")
class JakartaElStringInterpolatorEdgeCaseTest {

  private Map<String, Object> variables;

  @BeforeEach
  void setUp() {
    variables = new HashMap<>();
    variables.put("name", "Alice");
    variables.put("age", 30);
    variables.put("active", true);
    variables.put("items", List.of("apple", "banana", "orange"));
    variables.put(
        "user",
        Map.of(
            "firstName", "John",
            "lastName", "Doe",
            "email", "john.doe@example.com",
            "address",
                Map.of(
                    "street", "123 Main St",
                    "city", "Anytown")));
    variables.put("price", 19.99);
    variables.put("quantity", 3);
    variables.put("nullValue", null);

    WorkflowContext workflowContext = TestContexts.emptyContext();
    workflowContext.put("contextKey", "contextValue");
    workflowContext.put("number", 42);
  }

  @Nested
  @DisplayName("Complex Nested Access and Method Chaining")
  class ComplexAccessTests {

    @Test
    @DisplayName("Should handle deeply nested object access")
    void testDeepNestedObjectAccess() {
      // Given
      Map<String, Object> level3 = Map.of("value", "deepValue", "number", 42);
      Map<String, Object> level2 = Map.of("level3", level3, "name", "level2");
      Map<String, Object> level1 = Map.of("level2", level2, "name", "level1");
      Map<String, Object> root = Map.of("level1", level1);
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(root);

      // When
      String result = interpolator.interpolate("${level1.level2.level3.value}");

      // Then
      assertEquals("deepValue", result);
    }

    @Test
    @DisplayName("Should handle complex method chaining")
    void testComplexMethodChaining() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.forVariables(variables);

      // When
      String result =
          interpolator.interpolate("${user.email.toUpperCase().trim().substring(0, 4)}");

      // Then
      assertEquals("JOHN", result);
    }

    @Test
    @DisplayName("Should handle array/collection operations with method chaining")
    void testCollectionMethodChaining() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.forVariables(variables);

      // When
      String result =
          interpolator.interpolate(
              "${items.stream().map(item -> item.toUpperCase()).toList().toString()}");

      // Then
      assertNotNull(result);
      assertTrue(result.contains("APPLE"));
      assertTrue(result.contains("BANANA"));
      assertTrue(result.contains("ORANGE"));
    }
  }

  @Nested
  @DisplayName("Advanced Conditional Logic")
  class AdvancedConditionalTests {

    @Test
    @DisplayName("Should handle multiple nested ternary operators")
    void testMultipleNestedTernaries() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.builder().variables(variables).build();

      // When
      String result =
          interpolator.interpolate(
              "${age < 13 ? 'child' : (age < 18 ? 'teen' : (age < 65 ? 'adult' : 'senior'))}");

      // Then
      assertEquals("adult", result);
    }

    @Test
    @DisplayName("Should handle complex boolean expressions")
    void testComplexBooleanExpressions() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.forVariables(variables);

      // When
      String result =
          interpolator.interpolate(
              "${active && (age >= 18 && age <= 65) && user.email != null ? 'eligible' : 'ineligible'}");

      // Then
      assertEquals("eligible", result);
    }

    @Test
    @DisplayName("Should handle conditional arithmetic")
    void testConditionalArithmetic() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.forVariables(variables);

      // When
      // Formats to 3 decimal places
      String result =
          interpolator.interpolate("${String.format('%.3f', active ? price * 1.2 : price)}");

      // Then
      assertEquals("23.988", result);
    }
  }

  @Nested
  @DisplayName("Null and Edge Value Handling")
  class NullHandlingTests {

    @Test
    @DisplayName("Should handle null in conditional expressions")
    void testNullInConditional() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("nullValue", null);
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When
      String result = interpolator.interpolate("${nullValue == null ? 'is null' : 'not null'}");

      // Then
      assertEquals("is null", result);
    }

    @Test
    @DisplayName("Should handle empty string comparisons")
    void testEmptyStringComparisons() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("emptyStr", "");
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When
      String result1 = interpolator.interpolate("${empty emptyStr ? 'empty' : 'not empty'}");
      String result2 = interpolator.interpolate("${emptyStr == '' ? 'equal' : 'not equal'}");

      // Then
      assertEquals("empty", result1);
      assertEquals("equal", result2);
    }
  }

  @Nested
  @DisplayName("Performance and Stress Testing")
  class PerformanceTests {

    @Test
    @DisplayName("Should handle very long interpolation strings")
    void testVeryLongInterpolationStrings() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.forVariables(variables);

      StringBuilder longExpression = new StringBuilder();
      for (int i = 0; i < 100; i++) {
        if (i > 0) longExpression.append(" + ");
        longExpression.append("${name}").append(i);
      }

      // When
      String result = interpolator.interpolate(longExpression.toString());

      // Then
      assertNotNull(result);
      assertTrue(result.contains("Alice0"));
      assertTrue(result.contains("Alice99"));
    }

    @Test
    @DisplayName("Should handle large variable maps")
    void testLargeVariableMaps() {
      // Given
      Map<String, Object> largeVars = new HashMap<>();
      for (int i = 0; i < 1000; i++) {
        largeVars.put("key" + i, "value" + i);
      }
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.forVariables(largeVars);

      // When
      String result = interpolator.interpolate("${key0} and ${key999}");

      // Then
      assertEquals("value0 and value999", result);
    }

    @Test
    @DisplayName("Should handle repeated interpolation operations efficiently")
    void testRepeatedInterpolations() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.forVariables(variables);

      // When
      for (int i = 0; i < 1000; i++) {
        String result = interpolator.interpolate("Hello ${name}! You are ${age} years old.");
        assertEquals("Hello Alice! You are 30 years old.", result);
      }

      // Then - Should complete without issues
      assertTrue(true);
    }
  }

  @Nested
  @DisplayName("Special Characters and Encoding")
  class SpecialCharacterTests {

    @Test
    @DisplayName("Should handle Unicode characters in expressions")
    void testUnicodeCharacters() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("greeting", "こんにちは");
      vars.put("emoji", "🎉");
      vars.put("chinese", "世界");
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When
      String result = interpolator.interpolate("${greeting} ${emoji} ${chinese}");

      // Then
      assertEquals("こんにちは 🎉 世界", result);
    }

    @Test
    @DisplayName("Should handle special regex characters in literals")
    void testSpecialRegexCharacters() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("pattern", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When
      String result = interpolator.interpolate("Email pattern: ${pattern}");

      // Then
      assertEquals("Email pattern: [a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", result);
    }

    @Test
    @DisplayName("Should handle quotes and escape sequences")
    void testQuotesAndEscapeSequences() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("quote", "'");
      vars.put("doubleQuote", "\"");
      vars.put("backslash", "\\");
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When
      String result = interpolator.interpolate("${quote} ${doubleQuote} ${backslash}");

      // Then
      assertEquals("' \" \\", result);
    }
  }

  @Nested
  @DisplayName("Boundary Conditions and Edge Cases")
  class BoundaryConditionTests {

    @Test
    @DisplayName("Should handle numeric boundary values")
    void testNumericBoundaryValues() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("maxInt", Integer.MAX_VALUE);
      vars.put("minInt", Integer.MIN_VALUE);
      vars.put("maxLong", Long.MAX_VALUE);
      vars.put("minLong", Long.MIN_VALUE);
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When & Then
      assertDoesNotThrow(
          () -> {
            String result1 = interpolator.interpolate("${maxInt}");
            String result2 = interpolator.interpolate("${minInt}");
            String result3 = interpolator.interpolate("${maxLong}");
            String result4 = interpolator.interpolate("${minLong}");

            assertNotNull(result1);
            assertNotNull(result2);
            assertNotNull(result3);
            assertNotNull(result4);
          });
    }

    @Test
    @DisplayName("Should handle floating point edge cases")
    void testFloatingPointEdgeCases() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("infinity", Double.POSITIVE_INFINITY);
      vars.put("negInfinity", Double.NEGATIVE_INFINITY);
      vars.put("nan", Double.NaN);
      vars.put("maxDouble", Double.MAX_VALUE);
      vars.put("minDouble", Double.MIN_VALUE);
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When & Then
      assertDoesNotThrow(
          () -> {
            String result1 = interpolator.interpolate("${infinity}");
            String result2 = interpolator.interpolate("${negInfinity}");
            String result3 = interpolator.interpolate("${nan}");
            String result4 = interpolator.interpolate("${maxDouble}");
            String result5 = interpolator.interpolate("${minDouble}");

            assertNotNull(result1);
            assertNotNull(result2);
            assertNotNull(result3);
            assertNotNull(result4);
            assertNotNull(result5);
          });
    }

    @Test
    @DisplayName("Should handle very large and small numbers")
    void testExtremeNumberSizes() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("bigDecimal", new java.math.BigDecimal("12345678901234567890.123456789"));
      vars.put("smallDecimal", new java.math.BigDecimal("0.000000000000001"));
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When
      String result =
          interpolator.interpolate(
              "Big: ${bigDecimal.toPlainString()}, Small: ${smallDecimal.toPlainString()}");

      // Then
      assertTrue(result.contains("12345678901234567890.123456789"));
      assertTrue(result.contains("0.000000000000001"));
    }
  }

  @Nested
  @DisplayName("Error Recovery and Resilience")
  class ErrorRecoveryTests {

    @Test
    @DisplayName("Should handle malformed expressions gracefully in non-strict mode")
    void testMalformedExpressionRecovery() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.builder().variables(variables).strict(false).build();

      // When
      String result1 = interpolator.interpolate("${invalid syntax}");
      String result2 = interpolator.interpolate("${name + (unclosed}");

      // Then
      assertEquals("${invalid syntax}", result1);
      assertEquals("${name + (unclosed}", result2);
    }

    @Test
    @DisplayName("Should provide detailed error messages in strict mode")
    void testDetailedErrorMessages() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.builder().variables(variables).strict(true).build();

      // When & Then
      InterpolationException exception =
          assertThrows(
              InterpolationException.class,
              () -> interpolator.interpolate("${completely.invalid.property.name}"));

      assertNotNull(exception.getMessage());
      assertTrue(exception.getMessage().contains("Unable to resolve"));
    }
  }

  @Nested
  @DisplayName("Integration with Complex Data Structures")
  class ComplexDataStructureTests {

    @Test
    @DisplayName("Should handle nested collections and objects")
    void testNestedCollectionsAndObjects() {
      // Given
      Map<String, Object> order =
          Map.of(
              "id", "ORD-001",
              "items",
                  List.of(
                      Map.of("product", Map.of("name", "Widget", "price", 19.99), "quantity", 2),
                      Map.of("product", Map.of("name", "Gadget", "price", 29.99), "quantity", 1)),
              "customer",
                  Map.of(
                      "name",
                      "John Doe",
                      "addresses",
                      List.of(
                          Map.of("type", "shipping", "street", "123 Main St"),
                          Map.of("type", "billing", "street", "456 Oak Ave"))));
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(order);

      // When
      String result =
          interpolator.interpolate(
              "Order ${id}: ${items[0].product.name} x${items[0].quantity}, "
                  + "${customer.addresses.stream().filter(addr -> addr.type == 'shipping').findFirst().get().street}");
      // Then
      assertTrue(result.contains("ORD-001"));
      assertTrue(result.contains("Widget"));
      assertTrue(result.contains("2"));
      assertTrue(result.contains("123 Main St"));
    }

    @Test
    @DisplayName("Should handle array access with dynamic indices")
    void testDynamicArrayAccess() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("items", List.of("first", "second", "third", "fourth"));
      vars.put("index", 2);
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When
      String result = interpolator.interpolate("${items[index]}");

      // Then
      assertEquals("third", result);
    }
  }

  @Nested
  @DisplayName("Advanced String Operations")
  class AdvancedStringTests {

    @Test
    @DisplayName("Should handle complex string manipulation")
    void testComplexStringManipulation() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("text", "Hello World!");
      vars.put("prefix", "Result: ");
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When
      // Use += for string concatenation
      String result =
          interpolator.interpolate("${prefix += text.trim().replace('World', 'EL').toUpperCase()}");

      // Then
      assertEquals("Result: HELLO EL!", result);
    }

    @Test
    @DisplayName("Should handle regex-like operations")
    void testRegexLikeOperations() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("email", "john.doe@example.com");
      vars.put("phone", "123-456-7890");
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When
      String result =
          interpolator.interpolate(
              "Email valid: ${email.matches('[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+[.][a-zA-Z]{2,}')}, "
                  + "Phone valid: ${phone.matches('[0-9]{3}-[0-9]{3}-[0-9]{4}')}");

      // Then
      assertEquals("Email valid: true, Phone valid: true", result);
    }

    @Test
    @DisplayName("Should handle date/time operations")
    void testDateTimeOperations() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.forVariables(variables);

      // When
      String result = interpolator.interpolate("Current time: ${new java.util.Date().toString()}");

      // Then
      assertNotNull(result);
      assertTrue(result.contains("Current time:"));
    }
  }

  @Nested
  @DisplayName("Security and Safety Considerations")
  class SecurityTests {

    @Test
    @DisplayName("Should prevent access to restricted classes in strict mode")
    void testRestrictedClassAccess() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.builder().variables(variables).strict(true).build();

      // When & Then - Should throw or handle safely
      assertThrows(
          InterpolationException.class,
          () -> interpolator.interpolate("${new java.lang.Runtime().exec('rm -rf /')}"));
    }

    @Test
    @DisplayName("Should handle script injection attempts")
    void testScriptInjectionPrevention() {
      // Given
      Map<String, Object> vars = new HashMap<>();
      vars.put("userInput", "'); System.exit(0); //");
      JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(vars);

      // When
      String result = interpolator.interpolate("${userInput}");

      // Then
      // Should treat as literal string, not execute
      assertEquals("'); System.exit(0); //", result);
    }
  }

  @Nested
  @DisplayName("Memory and Resource Management")
  class ResourceManagementTests {

    @Test
    @DisplayName("Should handle large number of interpolations without memory leaks")
    void testMemoryLeakPrevention() {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.forVariables(variables);

      // When - Perform many interpolations
      for (int i = 0; i < 10000; i++) {
        String result = interpolator.interpolate("Item ${i}: ${name}");
        assertNotNull(result);
      }

      // Then - Should complete without OutOfMemoryError
      assertTrue(true);
    }

    @Test
    @DisplayName("Should handle thread-safe operations")
    void testThreadSafety() throws InterruptedException {
      // Given
      JakartaElStringInterpolator interpolator =
          JakartaElStringInterpolator.forVariables(variables);
      int threadCount = 10;
      Thread[] threads = new Thread[threadCount];
      AtomicInteger successCount = new AtomicInteger(0);

      // When
      for (int i = 0; i < threadCount; i++) {
        threads[i] =
            new Thread(
                () -> {
                  for (int j = 0; j < 100; j++) {
                    try {
                      String result = interpolator.interpolate("Hello ${name}!");
                      if ("Hello Alice!".equals(result)) {
                        successCount.incrementAndGet();
                      }
                    } catch (Exception _) {
                      // Log but don't fail the test
                    }
                  }
                });
        threads[i].start();
      }

      for (Thread thread : threads) {
        thread.join(5000);
      }

      // Then
      assertEquals(threadCount * 100, successCount.get());
    }
  }
}
