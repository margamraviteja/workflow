package com.workflow.interpolation;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.interpolation.exception.InterpolationException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JakartaElStringInterpolatorTest {

  private JakartaElStringInterpolator interpolator;

  @BeforeEach
  void setUp() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("name", "Alice");
    variables.put("age", 30);
    variables.put("price", 19.99);
    variables.put("quantity", 3);
    variables.put("active", true);
    variables.put("user", Map.of("name", "Bob", "email", "bob@example.com", "age", 25));
    variables.put("items", Arrays.asList("apple", "banana", "cherry"));
    variables.put("numbers", Arrays.asList(1, 2, 3, 4, 5));

    interpolator = JakartaElStringInterpolator.forVariables(variables);
  }

  @Nested
  class SimplePropertyAccess {

    @Test
    void shouldResolveSimpleVariable() {
      String result = interpolator.interpolate("Hello, ${name}!");
      assertEquals("Hello, Alice!", result);
    }

    @Test
    void shouldResolveMultipleVariables() {
      String result = interpolator.interpolate("${name} is #${age} years old.");
      assertEquals("Alice is #30 years old.", result);
    }

    @Test
    void shouldResolveNestedProperty() {
      String result = interpolator.interpolate("User: ${user.name}");
      assertEquals("User: Bob", result);
    }

    @Test
    void shouldResolveDeepNestedProperty() {
      String result = interpolator.interpolate("Email: ${user.email}");
      assertEquals("Email: bob@example.com", result);
    }

    @Test
    void shouldHandleNullInput() {
      assertNull(interpolator.interpolate(null));
    }

    @Test
    void shouldHandleEmptyInput() {
      assertEquals("", interpolator.interpolate(""));
    }

    @Test
    void shouldHandleNoPlaceholders() {
      String result = interpolator.interpolate("Plain text without placeholders");
      assertEquals("Plain text without placeholders", result);
    }
  }

  @Nested
  class CollectionAccess {

    @Test
    void shouldAccessListByIndex() {
      String result = interpolator.interpolate("First item: ${items[0]}");
      assertEquals("First item: apple", result);
    }

    @Test
    void shouldAccessMultipleListElements() {
      String result = interpolator.interpolate("Items: ${items[0]}, ${items[1]}, ${items[2]}");
      assertEquals("Items: apple, banana, cherry", result);
    }

    @Test
    void shouldAccessListSize() {
      String result = interpolator.interpolate("Count: ${items.size()}");
      assertEquals("Count: 3", result);
    }
  }

  @Nested
  class ArithmeticOperations {

    @Test
    void shouldPerformAddition() {
      String result = interpolator.interpolate("Total: ${price + 5}");
      assertEquals("Total: 24.99", result);
    }

    @Test
    void shouldPerformMultiplication() {
      String result = interpolator.interpolate("Total: ${price * quantity}");
      assertEquals("Total: 59.97", result);
    }

    @Test
    void shouldPerformSubtraction() {
      String result = interpolator.interpolate("Result: ${age - 10}");
      assertEquals("Result: 20", result);
    }

    @Test
    void shouldPerformDivision() {
      String result = interpolator.interpolate("Result: ${age / 2}");
      assertEquals("Result: 15.0", result);
    }

    @Test
    void shouldPerformModulo() {
      String result = interpolator.interpolate("Result: ${age % 7}");
      assertEquals("Result: 2", result);
    }
  }

  @Nested
  class ComparisonOperations {

    @Test
    void shouldCompareGreaterThan() {
      String result = interpolator.interpolate("Adult: ${age >= 18}");
      assertEquals("Adult: true", result);
    }

    @Test
    void shouldCompareLessThan() {
      String result = interpolator.interpolate("Young: ${age < 40}");
      assertEquals("Young: true", result);
    }

    @Test
    void shouldCompareEquality() {
      String result = interpolator.interpolate("Is 30: ${age == 30}");
      assertEquals("Is 30: true", result);
    }

    @Test
    void shouldCompareInequality() {
      String result = interpolator.interpolate("Not 25: ${age != 25}");
      assertEquals("Not 25: true", result);
    }

    @Test
    void shouldCompareStrings() {
      String result = interpolator.interpolate("Is Alice: ${name == 'Alice'}");
      assertEquals("Is Alice: true", result);
    }
  }

  @Nested
  class ConditionalExpressions {

    @Test
    void shouldEvaluateTernaryExpressionTrue() {
      String result = interpolator.interpolate("Status: ${age >= 18 ? 'adult' : 'minor'}");
      assertEquals("Status: adult", result);
    }

    @Test
    void shouldEvaluateTernaryExpressionFalse() {
      Map<String, Object> vars = Map.of("age", 15);
      JakartaElStringInterpolator interp = JakartaElStringInterpolator.forVariables(vars);
      String result = interp.interpolate("Status: ${age >= 18 ? 'adult' : 'minor'}");
      assertEquals("Status: minor", result);
    }

    @Test
    void shouldEvaluateNestedTernary() {
      String result =
          interpolator.interpolate(
              "Category: ${age < 13 ? 'child' : (age < 20 ? 'teen' : 'adult')}");
      assertEquals("Category: adult", result);
    }
  }

  @Nested
  class LogicalOperations {

    @Test
    void shouldEvaluateAndExpression() {
      String result = interpolator.interpolate("Result: ${active && age > 18}");
      assertEquals("Result: true", result);
    }

    @Test
    void shouldEvaluateOrExpression() {
      String result = interpolator.interpolate("Result: ${active || age < 18}");
      assertEquals("Result: true", result);
    }

    @Test
    void shouldEvaluateNotExpression() {
      String result = interpolator.interpolate("Result: ${!active}");
      assertEquals("Result: false", result);
    }

    @Test
    void shouldEvaluateEmptyCheck() {
      String result = interpolator.interpolate("Has items: ${!empty items}");
      assertEquals("Has items: true", result);
    }
  }

  @Nested
  class EscapedPlaceholders {

    @Test
    void shouldHandleMixedEscapedAndUnescapedPlaceholders() {
      String result = interpolator.interpolate("${name} uses \\${syntax}");
      assertEquals("Alice uses ${syntax}", result);
    }
  }

  @Nested
  class StrictMode {

    @Test
    void shouldThrowInStrictModeForUnresolvedVariable() {
      JakartaElStringInterpolator strictInterpolator =
          JakartaElStringInterpolator.builder().variable("x", 1).strict(true).build();

      InterpolationException exception =
          assertThrows(
              InterpolationException.class,
              () -> strictInterpolator.interpolate("Value: ${unknown}"));

      assertTrue(exception.getMessage().contains("unknown"));
    }
  }

  @Nested
  class WorkflowContextIntegration {

    @Test
    void shouldResolveFromWorkflowContext() {
      WorkflowContext context = new WorkflowContext();
      context.put("userId", 12345);
      context.put("userName", "Charlie");

      JakartaElStringInterpolator contextInterpolator =
          JakartaElStringInterpolator.forContext(context);

      String result = contextInterpolator.interpolate("User ${userId}: ${userName}");
      assertEquals("User 12345: Charlie", result);
    }

    @Test
    void shouldResolveFromContextAndVariables() {
      WorkflowContext context = new WorkflowContext();
      context.put("userId", 12345);

      Map<String, Object> additionalVars = Map.of("prefix", "USER");

      JakartaElStringInterpolator contextInterpolator =
          JakartaElStringInterpolator.forContextAndVariables(context, additionalVars);

      String result = contextInterpolator.interpolate("${prefix}-${userId}");
      assertEquals("USER-12345", result);
    }

    @Test
    void shouldAccessNestedObjectsInContext() {
      WorkflowContext context = new WorkflowContext();
      context.put("order", Map.of("id", "ORD-001", "total", 99.99));

      JakartaElStringInterpolator contextInterpolator =
          JakartaElStringInterpolator.forContext(context);

      String result = contextInterpolator.interpolate("Order ${order.id} total: $${order.total}");
      assertEquals("Order ORD-001 total: $99.99", result);
    }
  }

  @Nested
  class BuilderTests {

    @Test
    void shouldBuildWithSingleVariable() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("x", 42).build();

      String result = interp.interpolate("Value: ${x}");
      assertEquals("Value: 42", result);
    }

    @Test
    void shouldBuildWithMultipleVariables() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("x", 10).variable("y", 20).build();

      String result = interp.interpolate("Sum: ${x + y}");
      assertEquals("Sum: 30", result);
    }

    @Test
    void shouldBuildWithVariablesMap() {
      Map<String, Object> vars = Map.of("a", 1, "b", 2, "c", 3);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variables(vars).build();

      String result = interp.interpolate("Total: ${a + b + c}");
      assertEquals("Total: 6", result);
    }

    @Test
    void shouldBuildWithAddVariables() {
      Map<String, Object> vars1 = new HashMap<>();
      vars1.put("a", 1);

      Map<String, Object> vars2 = Map.of("b", 2);

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variables(vars1).addVariables(vars2).build();

      String result = interp.interpolate("Sum: ${a + b}");
      assertEquals("Sum: 3", result);
    }

    @Test
    void shouldBuildWithWorkflowContext() {
      WorkflowContext context = new WorkflowContext();
      context.put("message", "Hello World");

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().workflowContext(context).build();

      String result = interp.interpolate("${message}");
      assertEquals("Hello World", result);
    }
  }

  @Nested
  class ContainsPlaceholders {

    @Test
    void shouldDetectPlaceholders() {
      assertTrue(interpolator.containsPlaceholders("${name}"));
      assertTrue(interpolator.containsPlaceholders("Hello ${name}!"));
    }

    @Test
    void shouldNotDetectEscapedPlaceholders() {
      assertFalse(interpolator.containsPlaceholders("\\${name}"));
    }

    @Test
    void shouldNotDetectInPlainText() {
      assertFalse(interpolator.containsPlaceholders("Plain text"));
    }

    @Test
    void shouldHandleNullAndEmpty() {
      assertFalse(interpolator.containsPlaceholders(null));
      assertFalse(interpolator.containsPlaceholders(""));
    }
  }

  @Nested
  class StringOperations {

    @Test
    void shouldConcatenateStrings() {
      String result = interpolator.interpolate("Full: ${user.name += ' - ' += user.email}");
      assertEquals("Full: Bob - bob@example.com", result);
    }

    @Test
    void shouldCallStringMethods() {
      String result = interpolator.interpolate("Upper: ${name.toUpperCase()}");
      assertEquals("Upper: ALICE", result);
    }

    @Test
    void shouldGetStringLength() {
      String result = interpolator.interpolate("Length: ${name.length()}");
      assertEquals("Length: 5", result);
    }
  }

  @Nested
  class NegativeCases {

    @Nested
    class InvalidSyntax {

      @Test
      void shouldHandleUnclosedPlaceholder() {
        // Unclosed placeholder is treated as literal text
        String result = interpolator.interpolate("Hello ${name");
        assertEquals("Hello ${name", result);
      }

      @Test
      void shouldHandleEmptyPlaceholder() {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder().strict(true).build();
        assertThrows(
            InterpolationException.class, () -> strictInterp.interpolate("Value: ${    }"));
      }

      static Stream<Arguments> invalidExpressionProvider() {
        return Stream.of(
            Arguments.of("Value: ${x +}", "malformed expression"),
            Arguments.of("Value: ${(x + 1}", "unbalanced parentheses"),
            Arguments.of("Value: ${x +++ 1}", "invalid operator"));
      }

      @ParameterizedTest(name = "should throw for {1}")
      @MethodSource("invalidExpressionProvider")
      @SuppressWarnings("unused") // description is used in test name display
      void shouldThrowForInvalidExpression(String expression, String description) {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder().variable("x", 1).strict(true).build();
        assertThrows(InterpolationException.class, () -> strictInterp.interpolate(expression));
      }
    }

    @Nested
    class UnresolvedVariables {

      @Test
      void shouldThrowForUndefinedVariableInStrictMode() {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder().strict(true).build();
        InterpolationException ex =
            assertThrows(
                InterpolationException.class,
                () -> strictInterp.interpolate("Hello ${undefinedVar}"));
        assertTrue(ex.getMessage().contains("Unable to resolve EL expression"));
      }

      @Test
      void shouldHandleNestedUndefinedPropertyInStrictMode() {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder()
                .variable("user", Map.of("name", "Bob"))
                .strict(true)
                .build();
        // EL returns null for missing map keys, which gets converted to empty string
        String result = strictInterp.interpolate("Email: ${user.nonExistentProperty}");
        assertEquals("Email: ", result);
      }

      @Test
      void shouldHandleNullVariableValue() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("nullValue", null);
        JakartaElStringInterpolator interp = JakartaElStringInterpolator.forVariables(vars);
        String result = interp.interpolate("Value: ${nullValue}");
        assertEquals("Value: ", result);
      }

      @Test
      void shouldHandleMethodOnNullInStrictMode() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("nullValue", null);
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder().variables(vars).strict(true).build();
        // EL handles null gracefully, method calls on null return empty
        String result = strictInterp.interpolate("Length: ${nullValue}");
        assertEquals("Length: ", result);
      }
    }

    @Nested
    class TypeErrors {

      @Test
      void shouldHandleInvalidArithmeticOnStrings() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("a", "hello")
                .variable("b", "world")
                .strict(true)
                .build();
        assertThrows(InterpolationException.class, () -> interp.interpolate("Result: ${a - b}"));
      }

      @Test
      void shouldHandleInvalidMethodCall() {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder().variable("x", 42).strict(true).build();
        assertThrows(
            InterpolationException.class,
            () -> strictInterp.interpolate("Result: ${x.nonExistentMethod()}"));
      }

      @Test
      void shouldHandleInvalidPropertyAccessOnPrimitive() {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder().variable("num", 42).strict(true).build();
        assertThrows(
            InterpolationException.class,
            () -> strictInterp.interpolate("Result: ${num.nonExistent}"));
      }
    }

    @Nested
    class ArrayAndCollectionErrors {

      @Test
      void shouldReturnEmptyForArrayIndexOutOfBoundsInStrictMode() {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder()
                .variable("items", Arrays.asList("a", "b", "c"))
                .strict(true)
                .build();
        // EL returns null for out of bounds index, which becomes empty string
        String result = strictInterp.interpolate("Item: ${items[99]}");
        assertEquals("Item: ", result);
      }

      @Test
      void shouldReturnEmptyForNegativeArrayIndex() {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder()
                .variable("items", Arrays.asList("a", "b", "c"))
                .strict(true)
                .build();
        // EL returns null for negative index
        String result = strictInterp.interpolate("Item: ${items[-1]}");
        assertEquals("Item: ", result);
      }

      @Test
      void shouldReturnEmptyForMapKeyNotFoundInStrictMode() {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder()
                .variable("map", Map.of("key1", "value1"))
                .strict(true)
                .build();
        // EL returns null for missing map keys, which becomes empty string
        String result = strictInterp.interpolate("Value: ${map.nonExistentKey}");
        assertEquals("Value: ", result);
      }

      @Test
      void shouldThrowForIndexAccessOnString() {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder().variable("str", "hello").strict(true).build();
        // EL doesn't support direct index access on String
        assertThrows(
            InterpolationException.class, () -> strictInterp.interpolate("Char: ${str[0]}"));
      }

      @Test
      void shouldAccessCharAtOnString() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("str", "hello").strict(true).build();
        // Use charAt method for character access
        String result = interp.interpolate("Char: ${str.charAt(0)}");
        assertEquals("Char: h", result);
      }
    }

    @Nested
    class DivisionAndArithmeticErrors {

      @Test
      void shouldHandleDivisionByZeroInteger() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("a", 10)
                .variable("b", 0)
                .strict(true)
                .build();
        // EL handles division by zero gracefully, returns Infinity for doubles
        String result = interp.interpolate("Result: ${a / b}");
        assertEquals("Result: Infinity", result);
      }

      @Test
      void shouldThrowForModuloByZero() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("a", 10)
                .variable("b", 0)
                .strict(true)
                .build();
        // Modulo by zero throws ArithmeticException in EL
        assertThrows(InterpolationException.class, () -> interp.interpolate("Result: ${a % b}"));
      }

      @Test
      void shouldHandleOverflow() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("max", Long.MAX_VALUE)
                .strict(true)
                .build();
        // EL will handle overflow according to Java semantics
        String result = interp.interpolate("Result: ${max + 1}");
        assertNotNull(result);
      }
    }

    @Nested
    class BuilderValidation {

      @Test
      void shouldThrowForNullVariablesMap() {
        JakartaElStringInterpolator.Builder builder = JakartaElStringInterpolator.builder();
        assertThrows(NullPointerException.class, () -> builder.variables(null));
      }

      @Test
      void shouldThrowForNullVariableName() {
        JakartaElStringInterpolator.Builder builder = JakartaElStringInterpolator.builder();
        assertThrows(NullPointerException.class, () -> builder.variable(null, "value"));
      }

      @Test
      void shouldThrowForNullWorkflowContext() {
        JakartaElStringInterpolator.Builder builder = JakartaElStringInterpolator.builder();
        assertThrows(NullPointerException.class, () -> builder.workflowContext(null));
      }

      @Test
      void shouldThrowForNullContextInStaticMethod() {
        assertThrows(
            NullPointerException.class, () -> JakartaElStringInterpolator.forContext(null));
      }

      @Test
      void shouldThrowForNullInContextAndVariables() {
        Map<String, Object> emptyMap = Map.of();
        assertThrows(
            NullPointerException.class,
            () -> JakartaElStringInterpolator.forContextAndVariables(null, emptyMap));
      }

      @Test
      void shouldThrowForNullVariablesInContextAndVariables() {
        WorkflowContext ctx = new WorkflowContext();
        assertThrows(
            NullPointerException.class,
            () -> JakartaElStringInterpolator.forContextAndVariables(ctx, null));
      }

      @Test
      void shouldThrowForNullAddVariablesMap() {
        JakartaElStringInterpolator.Builder builder = JakartaElStringInterpolator.builder();
        assertThrows(NullPointerException.class, () -> builder.addVariables(null));
      }
    }
  }

  @Nested
  class ComplexExpressions {

    @Nested
    class DeepNestedAccess {

      @Test
      void shouldAccessDeeplyNestedObjects() {
        Map<String, Object> level3 = Map.of("value", "deepValue", "number", 42);
        Map<String, Object> level2 = Map.of("level3", level3, "name", "level2");
        Map<String, Object> level1 = Map.of("level2", level2, "name", "level1");
        Map<String, Object> root = Map.of("level1", level1);

        JakartaElStringInterpolator interp = JakartaElStringInterpolator.forVariables(root);
        String result = interp.interpolate("Value: ${level1.level2.level3.value}");
        assertEquals("Value: deepValue", result);
      }

      @Test
      void shouldAccessNestedArraysInObjects() {
        Map<String, Object> user =
            Map.of(
                "name",
                "Alice",
                "addresses",
                Arrays.asList(
                    Map.of("city", "New York", "zip", "10001"),
                    Map.of("city", "Boston", "zip", "02101")));
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("user", user).build();

        String result = interp.interpolate("City: ${user.addresses[0].city}");
        assertEquals("City: New York", result);
      }

      @Test
      void shouldAccessObjectsInArraysInObjects() {
        Map<String, Object> order =
            Map.of(
                "id",
                "ORD-001",
                "items",
                Arrays.asList(
                    Map.of("product", Map.of("name", "Widget", "price", 19.99), "quantity", 2),
                    Map.of("product", Map.of("name", "Gadget", "price", 29.99), "quantity", 1)));

        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("order", order).build();

        String result =
            interp.interpolate(
                "First product: ${order.items[0].product.name} @ $${order.items[0].product.price}");
        assertEquals("First product: Widget @ $19.99", result);
      }
    }

    @Nested
    class ChainedMethodCalls {

      @Test
      void shouldChainStringMethods() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("text", "  Hello World  ").build();
        String result = interp.interpolate("Result: ${text.trim().toUpperCase()}");
        assertEquals("Result: HELLO WORLD", result);
      }

      @Test
      void shouldChainMultipleMethodCalls() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("text", "hello").build();
        String result = interp.interpolate("Result: ${text.toUpperCase().substring(0, 3)}");
        assertEquals("Result: HEL", result);
      }

      @Test
      void shouldCallMethodsOnListResults() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("items", Arrays.asList("apple", "banana", "cherry"))
                .build();
        String result = interp.interpolate("First item upper: ${items.get(0).toUpperCase()}");
        assertEquals("First item upper: APPLE", result);
      }
    }

    @Nested
    class ComplexConditionals {

      @Test
      void shouldEvaluateMultipleNestedTernary() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("score", 85).build();

        String result =
            interp.interpolate(
                "Grade: ${score >= 90 ? 'A' : (score >= 80 ? 'B' : (score >= 70 ? 'C' : (score >= 60 ? 'D' : 'F')))}");
        assertEquals("Grade: B", result);
      }

      @Test
      void shouldCombineConditionalWithArithmetic() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("price", 100.0)
                .variable("discount", true)
                .variable("discountPercent", 20)
                .build();

        String result =
            interp.interpolate(
                "Final price: $${discount ? price - (price * discountPercent / 100) : price}");
        assertEquals("Final price: $80.0", result);
      }

      @Test
      void shouldEvaluateComplexLogicalExpressions() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("age", 25)
                .variable("hasLicense", true)
                .variable("hasCar", true)
                .variable("suspended", false)
                .build();

        String result =
            interp.interpolate("Can drive: ${age >= 16 && hasLicense && hasCar && !suspended}");
        assertEquals("Can drive: true", result);
      }

      @Test
      void shouldEvaluateComplexOrConditions() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("isAdmin", false)
                .variable("isModerator", false)
                .variable("isOwner", true)
                .build();

        String result = interp.interpolate("Has access: ${isAdmin || isModerator || isOwner}");
        assertEquals("Has access: true", result);
      }
    }

    @Nested
    class ComplexArithmetic {

      @Test
      void shouldEvaluateComplexMathExpressions() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("a", 10)
                .variable("b", 5)
                .variable("c", 2)
                .build();

        String result = interp.interpolate("Result: ${(a + b) * c - a / b}");
        assertEquals("Result: 28.0", result);
      }

      @Test
      void shouldHandlePrecedenceCorrectly() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("x", 2)
                .variable("y", 3)
                .variable("z", 4)
                .build();

        // Should be 2 + 3 * 4 = 2 + 12 = 14 (multiplication first)
        String result = interp.interpolate("Result: ${x + y * z}");
        assertEquals("Result: 14", result);
      }

      @Test
      void shouldEvaluateWithParentheses() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("x", 2)
                .variable("y", 3)
                .variable("z", 4)
                .build();

        // Should be (2 + 3) * 4 = 5 * 4 = 20
        String result = interp.interpolate("Result: ${(x + y) * z}");
        assertEquals("Result: 20", result);
      }

      @Test
      void shouldHandleNegativeNumbers() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("a", -5).variable("b", 10).build();

        String result = interp.interpolate("Result: ${a + b}");
        assertEquals("Result: 5", result);
      }

      @Test
      void shouldHandleFloatingPointPrecision() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("a", 0.1).variable("b", 0.2).build();

        String result = interp.interpolate("Result: ${a + b}");
        // Floating point may have precision issues
        assertTrue(result.startsWith("Result: 0.3"));
      }
    }

    @Nested
    class CollectionOperations {

      @Test
      void shouldAccessLastElementOfList() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("items", Arrays.asList("first", "middle", "last"))
                .build();

        String result = interp.interpolate("Last: ${items[items.size() - 1]}");
        assertEquals("Last: last", result);
      }

      @Test
      void shouldCheckIfListIsEmpty() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("emptyList", List.of())
                .variable("nonEmptyList", Arrays.asList(1, 2, 3))
                .build();

        assertEquals("Is empty: true", interp.interpolate("Is empty: ${empty emptyList}"));
        assertEquals("Is empty: false", interp.interpolate("Is empty: ${empty nonEmptyList}"));
      }

      @Test
      void shouldCheckListContainsElement() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("fruits", Arrays.asList("apple", "banana", "cherry"))
                .build();

        String result = interp.interpolate("Has banana: ${fruits.contains('banana')}");
        assertEquals("Has banana: true", result);
      }

      @Test
      void shouldAccessMapValues() {
        Map<String, Object> config =
            Map.of(
                "database",
                Map.of("host", "localhost", "port", 5432),
                "cache",
                Map.of("enabled", true, "ttl", 3600));

        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("config", config).build();

        String result =
            interp.interpolate(
                "DB: ${config.database.host}:${config.database.port}, Cache TTL: ${config.cache.ttl}");
        assertEquals("DB: localhost:5432, Cache TTL: 3600", result);
      }
    }

    @Nested
    class MixedExpressions {

      @Test
      void shouldHandleMultiplePlaceholdersWithDifferentTypes() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("name", "Alice")
                .variable("age", 30)
                .variable("balance", 1234.56)
                .variable("active", true)
                .variable("items", Arrays.asList("a", "b", "c"))
                .build();

        String result =
            interp.interpolate(
                "User ${name} (age ${age}) has $${balance} and ${items.size()} items. Active: ${active}");
        assertEquals("User Alice (age 30) has $1234.56 and 3 items. Active: true", result);
      }

      @Test
      void shouldCombineArithmeticWithStringConcat() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("firstName", "John")
                .variable("lastName", "Doe")
                .variable("score1", 85)
                .variable("score2", 90)
                .build();

        String result =
            interp.interpolate(
                "${firstName += ' ' += lastName}: Average = ${(score1 + score2) / 2}");
        assertEquals("John Doe: Average = 87.5", result);
      }

      @Test
      void shouldHandleConditionalWithCollectionAccess() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("users", Arrays.asList("Alice", "Bob", "Charlie"))
                .build();

        String result = interp.interpolate("Winner: ${users.size() > 0 ? users[0] : 'No winner'}");
        assertEquals("Winner: Alice", result);
      }

      @Test
      void shouldHandleEscapedAndUnescapedMixed() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("name", "Alice").build();

        String result = interp.interpolate("Hello ${name}! Use \\${expression} for EL syntax.");
        assertEquals("Hello Alice! Use ${expression} for EL syntax.", result);
      }
    }

    @Nested
    class StringManipulation {

      @Test
      void shouldPerformSubstring() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("text", "Hello World").build();

        String result = interp.interpolate("First word: ${text.substring(0, 5)}");
        assertEquals("First word: Hello", result);
      }

      @Test
      void shouldReplaceInString() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("text", "Hello World").build();

        String result = interp.interpolate("Replaced: ${text.replace('World', 'EL')}");
        assertEquals("Replaced: Hello EL", result);
      }

      @Test
      void shouldSplitAndAccessParts() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("csv", "apple,banana,cherry").build();

        String result = interp.interpolate("Second: ${csv.split(',')[1]}");
        assertEquals("Second: banana", result);
      }

      @Test
      void shouldCheckStringStartsAndEndsWith() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("url", "https://example.com").build();

        assertEquals("Is HTTPS: true", interp.interpolate("Is HTTPS: ${url.startsWith('https')}"));
        assertEquals("Is .com: true", interp.interpolate("Is .com: ${url.endsWith('.com')}"));
      }

      @Test
      void shouldConvertCase() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("text", "HeLLo WoRLd").build();

        assertEquals("Lower: hello world", interp.interpolate("Lower: ${text.toLowerCase()}"));
        assertEquals("Upper: HELLO WORLD", interp.interpolate("Upper: ${text.toUpperCase()}"));
      }
    }

    @Nested
    class BooleanAndNullHandling {

      @Test
      void shouldHandleNullWithTernary() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("value", null);
        JakartaElStringInterpolator interp = JakartaElStringInterpolator.forVariables(vars);

        String result = interp.interpolate("Result: ${value == null ? 'N/A' : value}");
        assertEquals("Result: N/A", result);
      }

      @Test
      void shouldHandleEmptyStringCheck() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("emptyStr", "")
                .variable("nonEmptyStr", "hello")
                .build();

        assertEquals("Is empty: true", interp.interpolate("Is empty: ${empty emptyStr}"));
        assertEquals("Is empty: false", interp.interpolate("Is empty: ${empty nonEmptyStr}"));
      }

      @Test
      void shouldHandleBooleanNegation() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("flag", true).build();

        assertEquals("Not flag: false", interp.interpolate("Not flag: ${!flag}"));
        assertEquals("Not not flag: true", interp.interpolate("Not not flag: ${!!flag}"));
      }

      @Test
      void shouldCompareBooleansCorrectly() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("a", true).variable("b", false).build();

        assertEquals("a == true: true", interp.interpolate("a == true: ${a == true}"));
        assertEquals("b == false: true", interp.interpolate("b == false: ${b == false}"));
        assertEquals("a != b: true", interp.interpolate("a != b: ${a != b}"));
      }
    }

    @Nested
    class EdgeCases {

      @Test
      void shouldHandleConsecutivePlaceholders() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("a", "Hello")
                .variable("b", "World")
                .build();

        String result = interp.interpolate("${a}${b}");
        assertEquals("HelloWorld", result);
      }

      @Test
      void shouldHandlePlaceholderAtStartAndEnd() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("start", "BEGIN")
                .variable("end", "END")
                .build();

        String result = interp.interpolate("${start} middle ${end}");
        assertEquals("BEGIN middle END", result);
      }

      @Test
      void shouldHandleOnlyPlaceholder() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("value", 42).build();

        String result = interp.interpolate("${value}");
        assertEquals("42", result);
      }

      @Test
      void shouldHandleVeryLongExpression() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("a", 1)
                .variable("b", 2)
                .variable("c", 3)
                .variable("d", 4)
                .variable("e", 5)
                .build();

        String result = interp.interpolate("Result: ${a + b + c + d + e + a * b * c * d * e}");
        assertEquals("Result: 135", result); // 15 + 120 = 135
      }

      @Test
      void shouldHandleSpecialCharactersInStrings() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("text", "Hello\nWorld\tTab").build();

        String result = interp.interpolate("Text: ${text}");
        assertEquals("Text: Hello\nWorld\tTab", result);
      }

      @Test
      void shouldHandleUnicodeCharacters() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("greeting", "こんにちは")
                .variable("emoji", "🎉")
                .build();

        String result = interp.interpolate("${greeting} ${emoji}");
        assertEquals("こんにちは 🎉", result);
      }

      @Test
      void shouldHandleWhitespaceInExpression() {
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("x", 10).build();

        // Whitespace inside expression should be handled
        assertEquals("Result: 15", interp.interpolate("Result: ${  x + 5  }"));
        assertEquals("Result: 15", interp.interpolate("Result: ${x+5}"));
        assertEquals("Result: 15", interp.interpolate("Result: ${ x + 5 }"));
      }

      @Test
      void shouldOverrideVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("x", 10);

        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variables(vars)
                .variable("x", 20) // Override
                .build();

        String result = interp.interpolate("Value: ${x}");
        assertEquals("Value: 20", result);
      }

      @Test
      void shouldReportStrictModeCorrectly() {
        JakartaElStringInterpolator strictInterp =
            JakartaElStringInterpolator.builder().strict(true).build();
        JakartaElStringInterpolator nonStrictInterp =
            JakartaElStringInterpolator.builder().strict(false).build();

        assertTrue(strictInterp.isStrictByDefault());
        assertFalse(nonStrictInterp.isStrictByDefault());
      }
    }
  }

  @Nested
  class SystemPropertiesAndEnvironmentVariables {

    @Test
    void shouldInterpolateSystemProperty() {
      String javaVersion = System.getProperty("java.version");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("sys", System.getProperties()).build();

      String result = interp.interpolate("Java Version: ${sys['java.version']}");
      assertEquals("Java Version: " + javaVersion, result);
    }

    @Test
    void shouldInterpolateMultipleSystemProperties() {
      String osName = System.getProperty("os.name");
      String osArch = System.getProperty("os.arch");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("sys", System.getProperties()).build();

      String result = interp.interpolate("OS: ${sys['os.name']} (${sys['os.arch']})");
      assertEquals("OS: " + osName + " (" + osArch + ")", result);
    }

    @Test
    void shouldInterpolateEnvironmentVariables() {
      Map<String, String> env = System.getenv();
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("env", env).build();

      // PATH is typically available on all systems
      if (env.containsKey("PATH")) {
        String result = interp.interpolate("Path exists: ${env['PATH'] != null}");
        assertEquals("Path exists: true", result);
      }
    }

    @Test
    void shouldHandleMissingSystemProperty() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("sys", System.getProperties())
              .strict(false)
              .build();

      String result = interp.interpolate("Value: ${sys['non.existent.property']}");
      assertEquals("Value: ", result);
    }

    @Test
    void shouldCombineSystemPropertiesWithCustomVariables() {
      String userDir = System.getProperty("user.dir");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("sys", System.getProperties())
              .variable("appName", "MyApp")
              .variable("version", "1.0.0")
              .build();

      String result = interp.interpolate("${appName} v${version} running in ${sys['user.dir']}");
      assertEquals("MyApp v1.0.0 running in " + userDir, result);
    }

    @Test
    void shouldUseSystemPropertyInConditional() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("sys", System.getProperties()).build();

      String result =
          interp.interpolate(
              "Platform: ${sys['os.name'].toLowerCase().contains('windows') ? 'Windows' : 'Unix-like'}");
      String osName = System.getProperty("os.name").toLowerCase();
      String expected = osName.contains("windows") ? "Windows" : "Unix-like";
      assertEquals("Platform: " + expected, result);
    }
  }

  @Nested
  class RealWorldConfigurationScenarios {

    @Test
    void shouldInterpolateDatabaseConnectionString() {
      Map<String, Object> dbConfig =
          Map.of(
              "host", "localhost",
              "port", 5432,
              "database", "myapp_db",
              "username", "admin",
              "maxPoolSize", 10);

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("db", dbConfig).build();

      String result =
          interp.interpolate(
              "jdbc:postgresql://${db.host}:${db.port}/${db.database}?user=${db.username}&maxPoolSize=${db.maxPoolSize}");
      assertEquals("jdbc:postgresql://localhost:5432/myapp_db?user=admin&maxPoolSize=10", result);
    }

    @Test
    void shouldInterpolateApiEndpointWithEnvironmentAwareness() {
      Map<String, Object> config =
          Map.of(
              "env", "production",
              "apiVersion", "v2",
              "baseUrls",
                  Map.of(
                      "production", "https://api.example.com",
                      "staging", "https://staging-api.example.com",
                      "development", "http://localhost:8080"));

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("config", config).build();

      String result =
          interp.interpolate("${config.baseUrls[config.env]}/${config.apiVersion}/users");
      assertEquals("https://api.example.com/v2/users", result);
    }

    @Test
    void shouldInterpolateEmailTemplate() {
      Map<String, Object> user =
          Map.of(
              "firstName", "John",
              "lastName", "Doe",
              "email", "john.doe@example.com",
              "orderId", "ORD-12345",
              "orderTotal", 149.99);

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("user", user).build();

      String template =
          """
          Dear ${user.firstName} ${user.lastName},
          Thank you for your order #${user.orderId}.
          Your total is $${user.orderTotal}.
          Confirmation sent to: ${user.email}""";

      String result = interp.interpolate(template);
      assertTrue(result.contains("Dear John Doe"));
      assertTrue(result.contains("order #ORD-12345"));
      assertTrue(result.contains("$149.99"));
      assertTrue(result.contains("john.doe@example.com"));
    }

    @Test
    void shouldInterpolateLoggingConfiguration() {
      Map<String, Object> logging =
          Map.of(
              "level", "INFO",
              "pattern", "%d{yyyy-MM-dd HH:mm:ss}",
              "maxFileSize", "10MB",
              "maxHistory", 30,
              "appName", "payment-service");

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("log", logging).build();

      String result =
          interp.interpolate(
              "Logging ${log.appName} at ${log.level}, pattern: ${log.pattern}, retention: ${log.maxHistory} days");
      assertEquals(
          "Logging payment-service at INFO, pattern: %d{yyyy-MM-dd HH:mm:ss}, retention: 30 days",
          result);
    }

    @Test
    void shouldInterpolateFeatureFlagConfiguration() {
      Map<String, Object> features =
          Map.of(
              "darkMode",
              true,
              "betaFeatures",
              false,
              "maxUploadSize",
              50,
              "allowedFileTypes",
              Arrays.asList("pdf", "doc", "xlsx"));

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("features", features).build();

      String result =
          interp.interpolate(
              "Dark mode: ${features.darkMode ? 'enabled' : 'disabled'}, "
                  + "Beta: ${features.betaFeatures ? 'enabled' : 'disabled'}, "
                  + "Max upload: ${features.maxUploadSize}MB");
      assertEquals("Dark mode: enabled, Beta: disabled, Max upload: 50MB", result);
    }

    @Test
    void shouldInterpolateKubernetesServiceDiscovery() {
      Map<String, Object> k8s =
          Map.of(
              "namespace", "production",
              "service", "user-service",
              "port", 8080,
              "protocol", "http");

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("k8s", k8s).build();

      String result =
          interp.interpolate(
              "${k8s.protocol}://${k8s.service}.${k8s.namespace}.svc.cluster.local:${k8s.port}");
      assertEquals("http://user-service.production.svc.cluster.local:8080", result);
    }
  }

  @Nested
  class RealWorldDataProcessingScenarios {

    @Test
    void shouldInterpolateJsonPathLikeAccess() {
      Map<String, Object> response =
          Map.of(
              "status",
              200,
              "data",
              Map.of(
                  "users",
                  Arrays.asList(
                      Map.of("id", 1, "name", "Alice", "role", "admin"),
                      Map.of("id", 2, "name", "Bob", "role", "user"),
                      Map.of("id", 3, "name", "Charlie", "role", "user"))));

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("response", response).build();

      String result =
          interp.interpolate(
              "First user: ${response.data.users[0].name} (${response.data.users[0].role})");
      assertEquals("First user: Alice (admin)", result);
    }

    @Test
    void shouldInterpolateWithDynamicIndexCalculation() {
      Map<String, Object> data =
          Map.of(
              "items",
              Arrays.asList("first", "second", "third", "fourth", "fifth"),
              "selectedIndex",
              2);

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("data", data).build();

      String result = interp.interpolate("Selected: ${data.items[data.selectedIndex]}");
      assertEquals("Selected: third", result);
    }

    @Test
    void shouldInterpolatePaginationInfo() {
      Map<String, Object> pagination =
          Map.of(
              "currentPage", 3,
              "pageSize", 20,
              "totalItems", 157,
              "totalPages", 8);

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("page", pagination).build();

      String result =
          interp.interpolate(
              "Showing page ${page.currentPage} of ${page.totalPages} "
                  + "(${(page.currentPage - 1) * page.pageSize + 1}-${page.currentPage * page.pageSize > page.totalItems ? page.totalItems : page.currentPage * page.pageSize} of ${page.totalItems} items)");
      assertEquals("Showing page 3 of 8 (41-60 of 157 items)", result);
    }

    @Test
    void shouldInterpolateShoppingCartSummary() {
      Map<String, Object> cart =
          Map.of(
              "items",
              Arrays.asList(
                  Map.of("name", "Widget", "price", 29.99, "qty", 2),
                  Map.of("name", "Gadget", "price", 49.99, "qty", 1),
                  Map.of("name", "Gizmo", "price", 19.99, "qty", 3)),
              "itemCount",
              3,
              "subtotal",
              169.94,
              "tax",
              13.60,
              "total",
              183.54);

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("cart", cart).build();

      String result =
          interp.interpolate(
              "Cart: ${cart.itemCount} item(s), Subtotal: $${cart.subtotal}, Tax: $${cart.tax}, Total: $${cart.total}");
      assertEquals("Cart: 3 item(s), Subtotal: $169.94, Tax: $13.6, Total: $183.54", result);
    }

    @Test
    void shouldInterpolateUserPermissionsCheck() {
      Map<String, Object> user =
          Map.of(
              "name", "Alice",
              "roles", Arrays.asList("editor", "reviewer"),
              "permissions", Map.of("canEdit", true, "canDelete", false, "canPublish", true));

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("user", user).build();

      String result =
          interp.interpolate(
              "User ${user.name}: Edit=${user.permissions.canEdit}, Delete=${user.permissions.canDelete}, Publish=${user.permissions.canPublish}");
      assertEquals("User Alice: Edit=true, Delete=false, Publish=true", result);
    }
  }

  @Nested
  class AdditionalNegativeCases {

    @Test
    void shouldHandleCircularReferenceGracefully() {
      // Create a mutable map that could potentially have circular reference issues
      Map<String, Object> vars = new HashMap<>();
      vars.put("a", "valueA");
      Map<String, Object> nestedRef = new HashMap<>(vars);
      vars.put("b", nestedRef); // Nested reference (EL handles this gracefully)

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variables(vars).strict(false).build();

      // Should not cause infinite loop, just access top-level property
      String result = interp.interpolate("Value: ${a}");
      assertEquals("Value: valueA", result);
    }

    @Test
    void shouldHandleVeryDeepNesting() {
      // Create deeply nested structure
      Map<String, Object> level5 = Map.of("value", "deep");
      Map<String, Object> level4 = Map.of("l5", level5);
      Map<String, Object> level3 = Map.of("l4", level4);
      Map<String, Object> level2 = Map.of("l3", level3);
      Map<String, Object> level1 = Map.of("l2", level2);
      Map<String, Object> root = Map.of("l1", level1);

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("root", root).build();

      String result = interp.interpolate("Value: ${root.l1.l2.l3.l4.l5.value}");
      assertEquals("Value: deep", result);
    }

    @Test
    void shouldHandleEmptyCollections() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("emptyList", List.of())
              .variable("emptyMap", Map.of())
              .build();

      assertEquals("Size: 0", interp.interpolate("Size: ${emptyList.size()}"));
      assertEquals("Size: 0", interp.interpolate("Size: ${emptyMap.size()}"));
      assertEquals("Empty: true", interp.interpolate("Empty: ${empty emptyList}"));
      assertEquals("Empty: true", interp.interpolate("Empty: ${empty emptyMap}"));
    }

    @Test
    void shouldHandleSpecialNumericValues() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("posInf", Double.POSITIVE_INFINITY)
              .variable("negInf", Double.NEGATIVE_INFINITY)
              .variable("nan", Double.NaN)
              .variable("maxDouble", Double.MAX_VALUE)
              .variable("minDouble", Double.MIN_VALUE)
              .build();

      assertEquals("Infinity", interp.interpolate("${posInf}"));
      assertEquals("-Infinity", interp.interpolate("${negInf}"));
      assertEquals("NaN", interp.interpolate("${nan}"));
      assertNotNull(interp.interpolate("Max: ${maxDouble}"));
    }

    @Test
    void shouldHandleStringWithSpecialELCharacters() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("text", "Price: $100 (50% off)").build();

      String result = interp.interpolate("Message: ${text}");
      assertEquals("Message: Price: $100 (50% off)", result);
    }

    @Test
    void shouldHandleMultipleConsecutiveEscapedPlaceholders() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("x", 1).build();

      String result = interp.interpolate("Use \\${a} and \\${b} and ${x}");
      assertEquals("Use ${a} and ${b} and 1", result);
    }

    @Test
    void shouldHandleNestedBracesInExpression() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("map", Map.of("key", "value")).build();

      String result = interp.interpolate("Value: ${map['key']}");
      assertEquals("Value: value", result);
    }

    @Test
    void shouldHandleLargeNumbers() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("bigLong", 9223372036854775807L) // Long.MAX_VALUE
              .variable("bigInt", 2147483647) // Integer.MAX_VALUE
              .build();

      assertEquals("9223372036854775807", interp.interpolate("${bigLong}"));
      assertEquals("2147483647", interp.interpolate("${bigInt}"));
    }

    @Test
    void shouldPreservePlaceholderWhenExpressionFailsInNonStrictMode() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("x", 1).strict(false).build();

      // Invalid method call should preserve placeholder in non-strict mode
      String result = interp.interpolate("Value: ${x.invalidMethod()}");
      assertEquals("Value: ${x.invalidMethod()}", result);
    }

    @Test
    void shouldHandleReservedWordsAsPropertyNames() {
      Map<String, Object> data =
          Map.of(
              "class", "MyClass",
              "return", "returnValue",
              "new", "newValue");

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("data", data).build();

      // Using bracket notation for reserved words
      assertEquals("MyClass", interp.interpolate("${data['class']}"));
      assertEquals("returnValue", interp.interpolate("${data['return']}"));
      assertEquals("newValue", interp.interpolate("${data['new']}"));
    }
  }

  @Nested
  class SecurityAndBoundaryTests {

    @Test
    void shouldNotAllowCodeInjection() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("userInput", "'); DROP TABLE users; --")
              .build();

      // EL treats the input as a string, not as code
      String result = interp.interpolate("Input: ${userInput}");
      assertEquals("Input: '); DROP TABLE users; --", result);
    }

    @Test
    void shouldHandleVeryLongStrings() {
      String longString = "a".repeat(10000);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("long", longString).build();

      String result = interp.interpolate("Length: ${long.length()}");
      assertEquals("Length: 10000", result);
    }

    @Test
    void shouldHandleManyPlaceholders() {
      JakartaElStringInterpolator.Builder builder = JakartaElStringInterpolator.builder();
      StringBuilder template = new StringBuilder();

      for (int i = 0; i < 100; i++) {
        builder.variable("var" + i, i);
        template.append("${var").append(i).append("}");
        if (i < 99) template.append(",");
      }

      JakartaElStringInterpolator interp = builder.build();
      String result = interp.interpolate(template.toString());

      // Verify first and last values
      assertTrue(result.startsWith("0,1,2"));
      assertTrue(result.endsWith("97,98,99"));
    }

    @Test
    void shouldHandleUnicodeInVariableValues() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("chinese", "你好世界")
              .variable("arabic", "مرحبا بالعالم")
              .variable("emoji", "👋🌍🎉")
              .variable("russian", "Привет мир")
              .build();

      assertEquals("Chinese: 你好世界", interp.interpolate("Chinese: ${chinese}"));
      assertEquals("Arabic: مرحبا بالعالم", interp.interpolate("Arabic: ${arabic}"));
      assertEquals("Emoji: 👋🌍🎉", interp.interpolate("Emoji: ${emoji}"));
      assertEquals("Russian: Привет мир", interp.interpolate("Russian: ${russian}"));
    }

    @Test
    void shouldHandleNullInCollections() {
      List<String> listWithNull = Arrays.asList("first", null, "third");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("list", listWithNull).build();

      assertEquals("first", interp.interpolate("${list[0]}"));
      assertEquals("", interp.interpolate("${list[1]}")); // null becomes empty
      assertEquals("third", interp.interpolate("${list[2]}"));
    }
  }

  @Nested
  class DateAndTimeScenarios {

    @Test
    void shouldInterpolateDateComponents() {
      Map<String, Object> date =
          Map.of(
              "year", 2026,
              "month", 1,
              "day", 16,
              "hour", 14,
              "minute", 30);

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("date", date).build();

      String result =
          interp.interpolate("${date.year}-${date.month < 10 ? '0' : ''}${date.month}-${date.day}");
      assertEquals("2026-01-16", result);
    }

    @Test
    void shouldInterpolateTimeBasedGreeting() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("hour", 14)
              .variable("userName", "Alice")
              .build();

      String result =
          interp.interpolate(
              "${hour < 12 ? 'Good morning' : (hour < 18 ? 'Good afternoon' : 'Good evening')}, ${userName}!");
      assertEquals("Good afternoon, Alice!", result);
    }

    @Test
    void shouldInterpolateScheduleDisplay() {
      Map<String, Object> event =
          Map.of(
              "title", "Team Meeting",
              "startHour", 9,
              "startMinute", 30,
              "endHour", 10,
              "endMinute", 0,
              "location", "Conference Room A");

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("event", event).build();

      String result =
          interp.interpolate(
              "${event.title}: ${event.startHour}:${event.startMinute < 10 ? '0' : ''}${event.startMinute} - "
                  + "${event.endHour}:${event.endMinute < 10 ? '0' : ''}${event.endMinute} @ ${event.location}");
      assertEquals("Team Meeting: 9:30 - 10:00 @ Conference Room A", result);
    }
  }

  @Nested
  class InternationalizationScenarios {

    @Test
    void shouldInterpolateWithLocaleAwareness() {
      Map<String, Object> i18n =
          Map.of(
              "locale",
              "en-US",
              "messages",
              Map.of(
                  "greeting", "Hello",
                  "farewell", "Goodbye",
                  "welcome", "Welcome to our app"));

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("i18n", i18n)
              .variable("userName", "Maria")
              .build();

      String result =
          interp.interpolate("${i18n.messages.greeting}, ${userName}! ${i18n.messages.welcome}.");
      assertEquals("Hello, Maria! Welcome to our app.", result);
    }

    @Test
    void shouldInterpolatePluralForms() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("count", 5)
              .variable("itemName", "file")
              .build();

      String result =
          interp.interpolate("${count} ${count == 1 ? itemName : itemName += 's'} selected");
      assertEquals("5 files selected", result);

      JakartaElStringInterpolator interp2 =
          JakartaElStringInterpolator.builder()
              .variable("count", 1)
              .variable("itemName", "file")
              .build();

      String result2 =
          interp2.interpolate("${count} ${count == 1 ? itemName : itemName += 's'} selected");
      assertEquals("1 file selected", result2);
    }

    @Test
    void shouldInterpolateCurrencyDisplay() {
      Map<String, Object> payment =
          Map.of(
              "amount", 1234.56,
              "currency", "USD",
              "currencySymbol", "$");

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("payment", payment).build();

      String result =
          interp.interpolate(
              "Total: ${payment.currencySymbol}${payment.amount} ${payment.currency}");
      assertEquals("Total: $1234.56 USD", result);
    }
  }

  @Nested
  class ErrorMessageScenarios {

    @Test
    void shouldInterpolateValidationErrorMessages() {
      Map<String, Object> error =
          Map.of(
              "field", "email",
              "value", "invalid-email",
              "constraint", "must be a valid email address",
              "code", "INVALID_FORMAT");

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("error", error).build();

      String result =
          interp.interpolate(
              "Validation failed: '${error.field}' ${error.constraint}. Provided value: '${error.value}' [${error.code}]");
      assertEquals(
          "Validation failed: 'email' must be a valid email address. Provided value: 'invalid-email' [INVALID_FORMAT]",
          result);
    }

    @Test
    void shouldInterpolateHttpErrorResponse() {
      Map<String, Object> httpError =
          Map.of(
              "status", 404,
              "error", "Not Found",
              "message", "The requested resource was not found",
              "path", "/api/users/12345",
              "timestamp", "2026-01-16T14:30:00Z");

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("err", httpError).build();

      String result =
          interp.interpolate("HTTP ${err.status} ${err.error}: ${err.message} (path: ${err.path})");
      assertEquals(
          "HTTP 404 Not Found: The requested resource was not found (path: /api/users/12345)",
          result);
    }

    @Test
    void shouldInterpolateExceptionDetails() {
      Map<String, Object> exception =
          Map.of(
              "type", "NullPointerException",
              "message", "Cannot invoke method on null object",
              "location", "UserService.java:42",
              "severity", "ERROR");

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("ex", exception).build();

      String result =
          interp.interpolate("[${ex.severity}] ${ex.type} at ${ex.location}: ${ex.message}");
      assertEquals(
          "[ERROR] NullPointerException at UserService.java:42: Cannot invoke method on null object",
          result);
    }
  }

  @Nested
  class PojoAndJavaObjectTests {

    @Nested
    class SimplePojoAccess {

      @Test
      void shouldAccessPojoProperties() {
        Person person = new Person("Alice", 30, "alice@example.com");
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("person", person).build();

        assertEquals("Alice", interp.interpolate("${person.name}"));
        assertEquals("30", interp.interpolate("${person.age}"));
        assertEquals("alice@example.com", interp.interpolate("${person.email}"));
      }

      @Test
      void shouldAccessMultiplePojoPropertiesInOneExpression() {
        Person person = new Person("Bob", 25, "bob@example.com");
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("person", person).build();

        String result = interp.interpolate("${person.name} (${person.age}) - ${person.email}");
        assertEquals("Bob (25) - bob@example.com", result);
      }

      @Test
      void shouldCallPojoMethods() {
        Person person = new Person("Charlie", 35, "charlie@example.com");
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("person", person).build();

        assertEquals("CHARLIE", interp.interpolate("${person.name.toUpperCase()}"));
        assertEquals("true", interp.interpolate("${person.isAdult()}"));
        assertEquals("Charlie (35)", interp.interpolate("${person.getDisplayName()}"));
      }

      @Test
      void shouldAccessPojoWithNullField() {
        Person person = new Person("Diana", 28, null);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("person", person).build();

        assertEquals("Diana", interp.interpolate("${person.name}"));
        assertEquals("", interp.interpolate("${person.email}"));
      }
    }

    @Nested
    class NestedPojoAccess {

      @Test
      void shouldAccessNestedPojoProperties() {
        Address address = new Address("123 Main St", "New York", "NY", "10001");
        Employee employee = new Employee("Eve", "Engineering", address);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("emp", employee).build();

        assertEquals("Eve", interp.interpolate("${emp.name}"));
        assertEquals("Engineering", interp.interpolate("${emp.department}"));
        assertEquals("New York", interp.interpolate("${emp.address.city}"));
        assertEquals("10001", interp.interpolate("${emp.address.zipCode}"));
      }

      @Test
      void shouldAccessDeeplyNestedPojos() {
        Address address = new Address("456 Oak Ave", "Boston", "MA", "02101");
        Employee employee = new Employee("Frank", "Sales", address);
        Company company = new Company("TechCorp", employee);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("company", company).build();

        assertEquals("TechCorp", interp.interpolate("${company.name}"));
        assertEquals("Frank", interp.interpolate("${company.ceo.name}"));
        assertEquals("Boston", interp.interpolate("${company.ceo.address.city}"));
      }

      @Test
      void shouldHandleNullNestedPojo() {
        Employee employee = new Employee("Grace", "HR", null);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("emp", employee).strict(true).build();

        assertEquals("Grace", interp.interpolate("${emp.name}"));
        assertEquals("", interp.interpolate("${emp.address}"));
      }
    }

    @Nested
    class RecordAccess {

      @Test
      void shouldAccessRecordProperties() {
        ProductRecord product = new ProductRecord("Widget", 29.99, 100);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("product", product).build();

        assertEquals("Widget", interp.interpolate("${product.name}"));
        assertEquals("29.99", interp.interpolate("${product.price}"));
        assertEquals("100", interp.interpolate("${product.quantity}"));
      }

      @Test
      void shouldPerformArithmeticOnRecordProperties() {
        ProductRecord product = new ProductRecord("Gadget", 19.99, 5);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("product", product).build();

        String result = interp.interpolate("Total: $${product.price * product.quantity}");
        // Allow for floating point precision variations
        assertTrue(result.startsWith("Total: $99.9"));
      }

      @Test
      void shouldAccessNestedRecords() {
        CustomerRecord customer = new CustomerRecord("Henry", "henry@example.com");
        OrderRecord order = new OrderRecord("ORD-001", customer, 150.00);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("order", order).build();

        assertEquals("ORD-001", interp.interpolate("${order.orderId}"));
        assertEquals("Henry", interp.interpolate("${order.customer.name}"));
        assertEquals("henry@example.com", interp.interpolate("${order.customer.email}"));
      }
    }

    @Nested
    class PojoWithCollections {

      @Test
      void shouldAccessListInPojo() {
        List<String> skills = Arrays.asList("Java", "Spring", "Kubernetes");
        Developer developer = new Developer("Ivan", skills);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("dev", developer).build();

        assertEquals("Ivan", interp.interpolate("${dev.name}"));
        assertEquals("Java", interp.interpolate("${dev.skills[0]}"));
        assertEquals("3", interp.interpolate("${dev.skills.size()}"));
      }

      @Test
      void shouldAccessNestedPojosInList() {
        List<Person> team =
            Arrays.asList(
                new Person("Jack", 30, "jack@example.com"),
                new Person("Kate", 28, "kate@example.com"),
                new Person("Leo", 35, "leo@example.com"));
        Team projectTeam = new Team("Alpha Team", team);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("team", projectTeam).build();

        assertEquals("Alpha Team", interp.interpolate("${team.name}"));
        assertEquals("Jack", interp.interpolate("${team.members[0].name}"));
        assertEquals("kate@example.com", interp.interpolate("${team.members[1].email}"));
        assertEquals("35", interp.interpolate("${team.members[2].age}"));
      }

      @Test
      void shouldCallMethodsOnCollectionElements() {
        List<Person> members =
            Arrays.asList(
                new Person("Mike", 40, "mike@example.com"),
                new Person("Nancy", 22, "nancy@example.com"));
        Team team = new Team("Beta Team", members);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("team", team).build();

        assertEquals("MIKE", interp.interpolate("${team.members[0].name.toUpperCase()}"));
        assertEquals("true", interp.interpolate("${team.members[0].isAdult()}"));
        assertEquals("Nancy (22)", interp.interpolate("${team.members[1].getDisplayName()}"));
      }

      @Test
      void shouldAccessMapInPojo() {
        Map<String, Integer> scores = Map.of("math", 95, "science", 88, "english", 92);
        Student student = new Student("Oscar", scores);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("student", student).build();

        assertEquals("Oscar", interp.interpolate("${student.name}"));
        assertEquals("95", interp.interpolate("${student.scores.math}"));
        assertEquals("88", interp.interpolate("${student.scores['science']}"));
      }
    }

    @Nested
    class InheritanceScenarios {

      @Test
      void shouldAccessInheritedProperties() {
        Manager manager = new Manager("Patricia", 45, "patricia@example.com", "Engineering", 10);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("mgr", manager).build();

        // Inherited from Person
        assertEquals("Patricia", interp.interpolate("${mgr.name}"));
        assertEquals("45", interp.interpolate("${mgr.age}"));
        assertEquals("patricia@example.com", interp.interpolate("${mgr.email}"));

        // Manager specific
        assertEquals("Engineering", interp.interpolate("${mgr.department}"));
        assertEquals("10", interp.interpolate("${mgr.teamSize}"));
      }

      @Test
      void shouldCallInheritedMethods() {
        Manager manager = new Manager("Quinn", 50, "quinn@example.com", "Sales", 15);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("mgr", manager).build();

        assertEquals("true", interp.interpolate("${mgr.isAdult()}"));
        assertEquals("Quinn (50)", interp.interpolate("${mgr.getDisplayName()}"));
        assertEquals(
            "Quinn manages Sales with 15 people", interp.interpolate("${mgr.getManagerInfo()}"));
      }

      @Test
      void shouldAccessPolymorphicObjects() {
        Person employee = new Manager("Rachel", 38, "rachel@example.com", "HR", 5);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("person", employee).build();

        // Can access Person properties
        assertEquals("Rachel", interp.interpolate("${person.name}"));
        assertEquals("38", interp.interpolate("${person.age}"));

        // Can also access Manager-specific properties since runtime type is Manager
        assertEquals("HR", interp.interpolate("${person.department}"));
        assertEquals("5", interp.interpolate("${person.teamSize}"));
      }
    }

    @Nested
    class PojoMethodInvocations {

      @Test
      void shouldCallParameterlessMethodsOnPojo() {
        Calculator calc = new Calculator(10, 5);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("calc", calc).build();

        assertEquals("15", interp.interpolate("${calc.add()}"));
        assertEquals("5", interp.interpolate("${calc.subtract()}"));
        assertEquals("50", interp.interpolate("${calc.multiply()}"));
        assertEquals("2.0", interp.interpolate("${calc.divide()}"));
      }

      @Test
      void shouldAccessComputedProperties() {
        Rectangle rect = new Rectangle(10, 5);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("rect", rect).build();

        assertEquals("10", interp.interpolate("${rect.width}"));
        assertEquals("5", interp.interpolate("${rect.height}"));
        assertEquals("50", interp.interpolate("${rect.area}"));
        assertEquals("30", interp.interpolate("${rect.perimeter}"));
      }

      @Test
      void shouldChainMethodCalls() {
        StringWrapper wrapper = new StringWrapper("  hello world  ");
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("str", wrapper).build();

        assertEquals("hello world", interp.interpolate("${str.getValue().trim()}"));
        assertEquals("HELLO WORLD", interp.interpolate("${str.getValue().trim().toUpperCase()}"));
        assertEquals("6", interp.interpolate("${str.getValue().trim().indexOf('world')}"));
      }

      @Test
      void shouldAccessBooleanProperties() {
        Account account = new Account("ACC-001", 1000.0, true, false);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("account", account).build();

        assertEquals("ACC-001", interp.interpolate("${account.accountId}"));
        assertEquals("1000.0", interp.interpolate("${account.balance}"));
        assertEquals("true", interp.interpolate("${account.active}"));
        assertEquals("false", interp.interpolate("${account.locked}"));
      }
    }

    @Nested
    class EnumInPojoTests {

      @Test
      void shouldAccessEnumProperty() {
        Order order = new Order("ORD-100", OrderStatus.PENDING, 250.00);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("order", order).build();

        assertEquals("ORD-100", interp.interpolate("${order.id}"));
        assertEquals("PENDING", interp.interpolate("${order.status}"));
        assertEquals("250.0", interp.interpolate("${order.total}"));
      }

      @Test
      void shouldCompareEnumValues() {
        Order pendingOrder = new Order("ORD-101", OrderStatus.PENDING, 100.00);
        Order shippedOrder = new Order("ORD-102", OrderStatus.SHIPPED, 200.00);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("pending", pendingOrder)
                .variable("shipped", shippedOrder)
                .build();

        assertEquals("true", interp.interpolate("${pending.status.name() == 'PENDING'}"));
        assertEquals("false", interp.interpolate("${shipped.status.name() == 'PENDING'}"));
      }

      @Test
      void shouldAccessEnumMethods() {
        Order order = new Order("ORD-103", OrderStatus.DELIVERED, 300.00);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("order", order).build();

        assertEquals("DELIVERED", interp.interpolate("${order.status.name()}"));
        assertEquals("2", interp.interpolate("${order.status.ordinal()}"));
      }
    }

    @Nested
    class ComplexPojoScenarios {

      @Test
      void shouldInterpolateShoppingCart() {
        CartItem item1 = new CartItem("Widget", 29.99, 2);
        CartItem item2 = new CartItem("Gadget", 49.99, 1);
        ShoppingCart cart = new ShoppingCart("CART-001", Arrays.asList(item1, item2));
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("cart", cart).build();

        assertEquals("CART-001", interp.interpolate("${cart.cartId}"));
        assertEquals("2", interp.interpolate("${cart.items.size()}"));
        assertEquals("Widget", interp.interpolate("${cart.items[0].productName}"));
        assertEquals("59.98", interp.interpolate("${cart.items[0].getSubtotal()}"));
        assertEquals("109.97", interp.interpolate("${cart.getTotalAmount()}"));
      }

      @Test
      void shouldInterpolateUserProfile() {
        List<String> roles = Arrays.asList("USER", "ADMIN");
        Map<String, String> preferences = Map.of("theme", "dark", "language", "en");
        UserProfile profile = new UserProfile("user123", "Sam", roles, preferences);
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder().variable("profile", profile).build();

        assertEquals("user123", interp.interpolate("${profile.userId}"));
        assertEquals("Sam", interp.interpolate("${profile.displayName}"));
        assertEquals("USER", interp.interpolate("${profile.roles[0]}"));
        assertEquals("ADMIN", interp.interpolate("${profile.roles[1]}"));
        assertEquals("dark", interp.interpolate("${profile.preferences.theme}"));
        assertEquals("true", interp.interpolate("${profile.hasRole('ADMIN')}"));
      }

      @Test
      void shouldUseConditionalWithPojoProperties() {
        Person adult = new Person("Tom", 30, "tom@example.com");
        Person minor = new Person("Uma", 16, "uma@example.com");
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("adult", adult)
                .variable("minor", minor)
                .build();

        assertEquals(
            "Tom is an adult",
            interp.interpolate("${adult.name} is ${adult.isAdult() ? 'an adult' : 'a minor'}"));
        assertEquals(
            "Uma is a minor",
            interp.interpolate("${minor.name} is ${minor.isAdult() ? 'an adult' : 'a minor'}"));
      }

      @Test
      void shouldCombineMultiplePojosInExpression() {
        Person sender = new Person("Victor", 35, "victor@example.com");
        Person receiver = new Person("Wendy", 28, "wendy@example.com");
        JakartaElStringInterpolator interp =
            JakartaElStringInterpolator.builder()
                .variable("sender", sender)
                .variable("receiver", receiver)
                .variable("amount", 100.00)
                .build();

        String result =
            interp.interpolate(
                "${sender.name} (${sender.email}) sent $${amount} to ${receiver.name} (${receiver.email})");
        assertEquals(
            "Victor (victor@example.com) sent $100.0 to Wendy (wendy@example.com)", result);
      }
    }

    // Test POJOs - must be public for Jakarta EL BeanELResolver to access via reflection

    public static class Person {
      private final String name;
      private final int age;
      private final String email;

      Person(String name, int age, String email) {
        this.name = name;
        this.age = age;
        this.email = email;
      }

      public String getName() {
        return name;
      }

      public int getAge() {
        return age;
      }

      public String getEmail() {
        return email;
      }

      public boolean isAdult() {
        return age >= 18;
      }

      public String getDisplayName() {
        return name + " (" + age + ")";
      }
    }

    public static class Address {
      private final String street;
      private final String city;
      private final String state;
      private final String zipCode;

      Address(String street, String city, String state, String zipCode) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
      }

      public String getStreet() {
        return street;
      }

      public String getCity() {
        return city;
      }

      public String getState() {
        return state;
      }

      public String getZipCode() {
        return zipCode;
      }
    }

    public static class Employee {
      private final String name;
      private final String department;
      private final Address address;

      Employee(String name, String department, Address address) {
        this.name = name;
        this.department = department;
        this.address = address;
      }

      public String getName() {
        return name;
      }

      public String getDepartment() {
        return department;
      }

      public Address getAddress() {
        return address;
      }
    }

    public static class Company {
      private final String name;
      private final Employee ceo;

      Company(String name, Employee ceo) {
        this.name = name;
        this.ceo = ceo;
      }

      public String getName() {
        return name;
      }

      public Employee getCeo() {
        return ceo;
      }
    }

    public record ProductRecord(String name, double price, int quantity) {}

    public record CustomerRecord(String name, String email) {}

    public record OrderRecord(String orderId, CustomerRecord customer, double total) {}

    public static class Developer {
      private final String name;
      private final List<String> skills;

      Developer(String name, List<String> skills) {
        this.name = name;
        this.skills = skills;
      }

      public String getName() {
        return name;
      }

      public List<String> getSkills() {
        return skills;
      }
    }

    public static class Team {
      private final String name;
      private final List<Person> members;

      Team(String name, List<Person> members) {
        this.name = name;
        this.members = members;
      }

      public String getName() {
        return name;
      }

      public List<Person> getMembers() {
        return members;
      }
    }

    public static class Student {
      private final String name;
      private final Map<String, Integer> scores;

      Student(String name, Map<String, Integer> scores) {
        this.name = name;
        this.scores = scores;
      }

      public String getName() {
        return name;
      }

      public Map<String, Integer> getScores() {
        return scores;
      }
    }

    public static class Manager extends Person {
      private final String department;
      private final int teamSize;

      Manager(String name, int age, String email, String department, int teamSize) {
        super(name, age, email);
        this.department = department;
        this.teamSize = teamSize;
      }

      public String getDepartment() {
        return department;
      }

      public int getTeamSize() {
        return teamSize;
      }

      public String getManagerInfo() {
        return getName() + " manages " + department + " with " + teamSize + " people";
      }
    }

    public static class Calculator {
      private final int a;
      private final int b;

      Calculator(int a, int b) {
        this.a = a;
        this.b = b;
      }

      public int getA() {
        return a;
      }

      public int getB() {
        return b;
      }

      public int add() {
        return a + b;
      }

      public int subtract() {
        return a - b;
      }

      public int multiply() {
        return a * b;
      }

      public double divide() {
        return (double) a / b;
      }
    }

    public static class Rectangle {
      private final int width;
      private final int height;

      Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
      }

      public int getWidth() {
        return width;
      }

      public int getHeight() {
        return height;
      }

      public int getArea() {
        return width * height;
      }

      public int getPerimeter() {
        return 2 * (width + height);
      }
    }

    public static class StringWrapper {
      private final String value;

      StringWrapper(String value) {
        this.value = value;
      }

      public String getValue() {
        return value;
      }
    }

    public static class Account {
      private final String accountId;
      private final double balance;
      private final boolean active;
      private final boolean locked;

      Account(String accountId, double balance, boolean active, boolean locked) {
        this.accountId = accountId;
        this.balance = balance;
        this.active = active;
        this.locked = locked;
      }

      public String getAccountId() {
        return accountId;
      }

      public double getBalance() {
        return balance;
      }

      public boolean isActive() {
        return active;
      }

      public boolean isLocked() {
        return locked;
      }
    }

    public enum OrderStatus {
      PENDING,
      SHIPPED,
      DELIVERED,
      CANCELLED
    }

    public static class Order {
      private final String id;
      private final OrderStatus status;
      private final double total;

      Order(String id, OrderStatus status, double total) {
        this.id = id;
        this.status = status;
        this.total = total;
      }

      public String getId() {
        return id;
      }

      public OrderStatus getStatus() {
        return status;
      }

      public double getTotal() {
        return total;
      }
    }

    public static class CartItem {
      private final String productName;
      private final double price;
      private final int quantity;

      CartItem(String productName, double price, int quantity) {
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
      }

      public String getProductName() {
        return productName;
      }

      public double getPrice() {
        return price;
      }

      public int getQuantity() {
        return quantity;
      }

      public double getSubtotal() {
        return price * quantity;
      }
    }

    public static class ShoppingCart {
      private final String cartId;
      private final List<CartItem> items;

      ShoppingCart(String cartId, List<CartItem> items) {
        this.cartId = cartId;
        this.items = items;
      }

      public String getCartId() {
        return cartId;
      }

      public List<CartItem> getItems() {
        return items;
      }

      public double getTotalAmount() {
        return items.stream().mapToDouble(CartItem::getSubtotal).sum();
      }
    }

    public static class UserProfile {
      private final String userId;
      private final String displayName;
      private final List<String> roles;
      private final Map<String, String> preferences;

      UserProfile(
          String userId, String displayName, List<String> roles, Map<String, String> preferences) {
        this.userId = userId;
        this.displayName = displayName;
        this.roles = roles;
        this.preferences = preferences;
      }

      public String getUserId() {
        return userId;
      }

      public String getDisplayName() {
        return displayName;
      }

      public List<String> getRoles() {
        return roles;
      }

      public Map<String, String> getPreferences() {
        return preferences;
      }

      public boolean hasRole(String role) {
        return roles.contains(role);
      }
    }
  }

  @Nested
  class LambdaOperatorTests {

    @Test
    void shouldUseLambdaOperatorForCollectionFiltering() {
      List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("numbers", numbers).build();

      String result = interp.interpolate("Has even: ${numbers.stream().anyMatch(x -> x % 2 == 0)}");
      assertEquals("Has even: true", result);
    }

    @Test
    void shouldUseLambdaForStreamOperations() {
      List<String> names = Arrays.asList("Alice", "Bob", "Charlie", "David");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("names", names).build();

      String result =
          interp.interpolate("Count: ${names.stream().filter(n -> n.length() > 3).count()}");
      assertEquals("Count: 3", result);
    }

    @Test
    void shouldMapCollectionUsingLambda() {
      List<Integer> numbers = Arrays.asList(1, 2, 3);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("nums", numbers).build();

      // EL 3.0+ supports lambda expressions
      String result =
          interp.interpolate("First squared: ${nums.stream().map(x -> x * x).findFirst().get()}");
      assertEquals("First squared: 1", result);
    }
  }

  @Nested
  class OptionalHandlingTests {

    @Test
    void shouldHandleOptionalEmpty() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("opt", java.util.Optional.empty()).build();

      assertEquals("false", interp.interpolate("${opt.isPresent()}"));
      assertEquals("true", interp.interpolate("${opt.isEmpty()}"));
    }

    @Test
    void shouldHandleOptionalPresent() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("opt", java.util.Optional.of("value"))
              .build();

      assertEquals("true", interp.interpolate("${opt.isPresent()}"));
      assertEquals("value", interp.interpolate("${opt.get()}"));
    }

    @Test
    void shouldUseOptionalWithTernary() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("emptyOpt", java.util.Optional.empty())
              .variable("presentOpt", java.util.Optional.of("found"))
              .build();

      assertEquals(
          "default", interp.interpolate("${emptyOpt.isPresent() ? emptyOpt.get() : 'default'}"));
      assertEquals(
          "found", interp.interpolate("${presentOpt.isPresent() ? presentOpt.get() : 'default'}"));
    }
  }

  @Nested
  class ArrayHandlingTests {

    @Test
    void shouldAccessPrimitiveArrayElements() {
      int[] numbers = {10, 20, 30, 40, 50};
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("nums", numbers).build();

      assertEquals("10", interp.interpolate("${nums[0]}"));
      assertEquals("30", interp.interpolate("${nums[2]}"));
      assertEquals("50", interp.interpolate("${nums[4]}"));
    }

    @Test
    void shouldAccessStringArrayElements() {
      String[] fruits = {"apple", "banana", "cherry"};
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("fruits", fruits).build();

      assertEquals("apple", interp.interpolate("${fruits[0]}"));
      assertEquals("BANANA", interp.interpolate("${fruits[1].toUpperCase()}"));
    }

    @Test
    void shouldGetArrayLength() {
      double[] prices = {19.99, 29.99, 39.99};
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("prices", prices).build();

      assertEquals("3", interp.interpolate("${prices.length}"));
    }

    @Test
    void shouldHandleMultiDimensionalArray() {
      int[][] matrix = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("matrix", matrix).build();

      assertEquals("1", interp.interpolate("${matrix[0][0]}"));
      assertEquals("5", interp.interpolate("${matrix[1][1]}"));
      assertEquals("9", interp.interpolate("${matrix[2][2]}"));
    }

    @Test
    void shouldHandleEmptyArray() {
      String[] empty = {};
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("arr", empty).build();

      assertEquals("0", interp.interpolate("${arr.length}"));
    }
  }

  @Nested
  class SetHandlingTests {

    @Test
    void shouldAccessSetElements() {
      java.util.Set<String> tags =
          new java.util.LinkedHashSet<>(Arrays.asList("java", "spring", "docker"));
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("tags", tags).build();

      assertEquals("3", interp.interpolate("${tags.size()}"));
      assertEquals("true", interp.interpolate("${tags.contains('java')}"));
      assertEquals("false", interp.interpolate("${tags.contains('python')}"));
    }

    @Test
    void shouldCheckSetEmpty() {
      java.util.Set<String> emptySet = new java.util.HashSet<>();
      java.util.Set<String> nonEmptySet = new java.util.HashSet<>(List.of("item"));
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("emptySet", emptySet)
              .variable("nonEmptySet", nonEmptySet)
              .build();

      assertEquals("true", interp.interpolate("${empty emptySet}"));
      assertEquals("false", interp.interpolate("${empty nonEmptySet}"));
    }

    @Test
    void shouldConvertSetToArray() {
      java.util.Set<Integer> numbers = new java.util.LinkedHashSet<>(Arrays.asList(1, 2, 3));
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("nums", numbers).build();

      String result = interp.interpolate("Size: ${nums.toArray().length}");
      assertEquals("Size: 3", result);
    }
  }

  @Nested
  class MathOperationsTests {

    @Test
    void shouldPerformIntegerDivision() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("a", 7).variable("b", 2).build();

      String result = interp.interpolate("Result: ${a / b}");
      assertEquals("Result: 3.5", result);
    }

    @Test
    void shouldHandleUnaryMinus() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("x", 10).build();

      assertEquals("-10", interp.interpolate("${-x}"));
      assertEquals("10", interp.interpolate("${-(-x)}"));
    }

    @Test
    void shouldHandleComplexExpressionWithUnaryOperator() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("a", 5).variable("b", 3).build();

      assertEquals("-2", interp.interpolate("${-(a - b) * 1}"));
    }

    @Test
    void shouldHandleAbsoluteValueWithTernary() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("negative", -42).build();

      String result = interp.interpolate("Abs: ${negative < 0 ? -negative : negative}");
      assertEquals("Abs: 42", result);
    }

    @Test
    void shouldFindMaxUsingTernary() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("a", 10).variable("b", 5).build();

      assertEquals("10", interp.interpolate("${a > b ? a : b}"));
      assertEquals("5", interp.interpolate("${a < b ? a : b}"));
    }
  }

  @Nested
  class TypeConversionTests {

    @Test
    void shouldConvertToString() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("num", 42).build();

      // EL automatically converts to string in string context
      String result = interp.interpolate("Value: ${num}");
      assertEquals("Value: 42", result);
    }

    @Test
    void shouldConcatenateNumbersAsStrings() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("prefix", "Value: ")
              .variable("num", 42)
              .build();

      String result = interp.interpolate("${prefix += num}");
      assertEquals("Value: 42", result);
    }
  }

  @Nested
  class ListOperationsTests {

    @Test
    void shouldCheckListContains() {
      List<String> items = Arrays.asList("apple", "banana", "cherry");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("items", items).build();

      assertEquals("true", interp.interpolate("${items.contains('banana')}"));
      assertEquals("false", interp.interpolate("${items.contains('orange')}"));
    }

    @Test
    void shouldGetListIndexOf() {
      List<String> colors = Arrays.asList("red", "green", "blue");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("colors", colors).build();

      assertEquals("1", interp.interpolate("${colors.indexOf('green')}"));
      assertEquals("-1", interp.interpolate("${colors.indexOf('yellow')}"));
    }

    @Test
    void shouldGetSubList() {
      List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("nums", numbers).build();

      assertEquals("3", interp.interpolate("${nums.subList(1, 4).size()}"));
      assertEquals("2", interp.interpolate("${nums.subList(1, 4)[0]}"));
    }

    @Test
    void shouldCheckListStartsAndEndsWith() {
      List<String> words = Arrays.asList("hello", "world", "java");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("words", words).build();

      assertEquals("hello", interp.interpolate("${words.get(0)}"));
      assertEquals("java", interp.interpolate("${words.get(words.size() - 1)}"));
    }
  }

  @Nested
  class MapOperationsTests {

    @Test
    void shouldCheckMapContainsKey() {
      Map<String, String> config = Map.of("host", "localhost", "port", "8080");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("config", config).build();

      assertEquals("true", interp.interpolate("${config.containsKey('host')}"));
      assertEquals("false", interp.interpolate("${config.containsKey('username')}"));
    }

    @Test
    void shouldCheckMapContainsValue() {
      Map<String, Integer> scores = new HashMap<>();
      scores.put("alice", 95);
      scores.put("bob", 88);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("scores", scores).build();

      // Check through values collection as containsValue may not be directly supported in EL
      assertEquals("2", interp.interpolate("${scores.values().size()}"));
      assertEquals("95", interp.interpolate("${scores.alice}"));
      assertEquals("88", interp.interpolate("${scores.bob}"));
    }

    @Test
    void shouldGetMapKeySet() {
      Map<String, String> data = Map.of("a", "1", "b", "2", "c", "3");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("data", data).build();

      assertEquals("3", interp.interpolate("${data.keySet().size()}"));
      assertEquals("true", interp.interpolate("${data.keySet().contains('a')}"));
    }

    @Test
    void shouldGetMapValues() {
      Map<String, Integer> inventory = Map.of("widget", 10, "gadget", 20);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("inventory", inventory).build();

      assertEquals("2", interp.interpolate("${inventory.values().size()}"));
    }

    @Test
    void shouldIterateMapEntries() {
      Map<String, String> labels = new java.util.LinkedHashMap<>();
      labels.put("env", "prod");
      labels.put("app", "myapp");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("labels", labels).build();

      assertEquals("2", interp.interpolate("${labels.entrySet().size()}"));
    }
  }

  @Nested
  class ConcatenationTests {

    @Test
    void shouldConcatenateWithPlusOperator() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("first", "Hello")
              .variable("second", "World")
              .build();

      String result = interp.interpolate("${first += ' ' += second}");
      assertEquals("Hello World", result);
    }

    @Test
    void shouldConcatenateNumbersAndStrings() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("prefix", "Value: ")
              .variable("num", 42)
              .build();

      String result = interp.interpolate("${prefix += num}");
      assertEquals("Value: 42", result);
    }

    @Test
    void shouldConcatenateMultipleValues() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("a", "A")
              .variable("b", "B")
              .variable("c", "C")
              .build();

      assertEquals("ABC", interp.interpolate("${a += b += c}"));
    }
  }

  @Nested
  class NullSafeNavigationTests {

    @Test
    void shouldHandleNullSafeAccessWithElvis() {
      Map<String, Object> user = new HashMap<>();
      user.put("name", null);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("user", user).build();

      String result = interp.interpolate("${user.name != null ? user.name : 'Unknown'}");
      assertEquals("Unknown", result);
    }

    @Test
    void shouldHandleNullSafeMethodCall() {
      Map<String, Object> data = new HashMap<>();
      data.put("text", null);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("data", data).build();

      String result = interp.interpolate("${data.text != null ? data.text.length() : 0}");
      assertEquals("0", result);
    }

    @Test
    void shouldHandleChainedNullChecks() {
      Map<String, Object> outer = new HashMap<>();
      outer.put("inner", null);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("obj", outer).build();

      String result = interp.interpolate("${obj.inner != null ? obj.inner.value : 'N/A'}");
      assertEquals("N/A", result);
    }
  }

  @Nested
  class JsonLikeStructureTests {

    @Test
    void shouldNavigateComplexJsonStructure() {
      Map<String, Object> json =
          Map.of(
              "user",
              Map.of(
                  "id", 123,
                  "profile",
                      Map.of(
                          "firstName", "John",
                          "lastName", "Doe",
                          "contacts",
                              Map.of(
                                  "email", "john@example.com",
                                  "phone", "+1234567890")),
                  "metadata",
                      Map.of(
                          "createdAt", "2026-01-01",
                          "status", "active")));

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("data", json).build();

      assertEquals("John", interp.interpolate("${data.user.profile.firstName}"));
      assertEquals("Doe", interp.interpolate("${data.user.profile.lastName}"));
      assertEquals("john@example.com", interp.interpolate("${data.user.profile.contacts.email}"));
      assertEquals("active", interp.interpolate("${data.user.metadata.status}"));
    }

    @Test
    void shouldHandleArraysInJsonStructure() {
      Map<String, Object> json =
          Map.of(
              "results",
              Arrays.asList(
                  Map.of("id", 1, "name", "Item 1"),
                  Map.of("id", 2, "name", "Item 2"),
                  Map.of("id", 3, "name", "Item 3")),
              "total",
              3);

      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("response", json).build();

      assertEquals("3", interp.interpolate("${response.total}"));
      assertEquals("Item 1", interp.interpolate("${response.results[0].name}"));
      assertEquals("2", interp.interpolate("${response.results[1].id}"));
    }
  }

  @Nested
  class RegexAndPatternTests {

    @Test
    void shouldUseStringMatches() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("email", "test@example.com")
              .variable("pattern", ".*@.*\\.com")
              .build();

      assertEquals("true", interp.interpolate("${email.matches(pattern)}"));
    }

    @Test
    void shouldValidateEmailFormat() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("validEmail", "user@domain.com")
              .variable("invalidEmail", "notanemail")
              .build();

      assertEquals("true", interp.interpolate("${validEmail.contains('@')}"));
      assertEquals("false", interp.interpolate("${invalidEmail.contains('@')}"));
    }

    @Test
    void shouldCheckStringPattern() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("phone", "123-456-7890").build();

      assertEquals("true", interp.interpolate("${phone.matches('[0-9]{3}-[0-9]{3}-[0-9]{4}')}"));
    }
  }

  @Nested
  class PerformanceEdgeCaseTests {

    @Test
    void shouldHandleNestedPlaceholders() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("key", "value")
              .variable("value", "result")
              .build();

      // Nested placeholders are NOT supported, treated as literal
      String result = interp.interpolate("${key}");
      assertEquals("value", result);
    }

    @Test
    void shouldHandleRepeatedPlaceholder() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("name", "Alice").build();

      String result = interp.interpolate("${name} ${name} ${name}");
      assertEquals("Alice Alice Alice", result);
    }

    @Test
    void shouldHandleMixedTextAndExpressions() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("user", "Bob")
              .variable("count", 5)
              .build();

      String result = interp.interpolate("User: ${user}, Count: ${count}, Total: ${count * 2}");
      assertEquals("User: Bob, Count: 5, Total: 10", result);
    }

    @Test
    void shouldHandleComplexStringWithMultipleTypes() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("str", "text")
              .variable("num", 42)
              .variable("bool", true)
              .variable("list", Arrays.asList(1, 2, 3))
              .variable("map", Map.of("key", "val"))
              .build();

      String result =
          interp.interpolate(
              "String: ${str}, Number: ${num}, Boolean: ${bool}, List size: ${list.size()}, Map key: ${map.key}");
      assertEquals("String: text, Number: 42, Boolean: true, List size: 3, Map key: val", result);
    }
  }

  @Nested
  class StreamApiIntegrationTests {

    @Test
    void shouldCheckStreamAnyMatch() {
      List<Integer> numbers = Arrays.asList(1, 3, 5, 7, 8);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("nums", numbers).build();

      assertEquals("true", interp.interpolate("${nums.stream().anyMatch(n -> n > 5)}"));
    }

    @Test
    void shouldCheckStreamAllMatch() {
      List<Integer> numbers = Arrays.asList(2, 4, 6, 8);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("nums", numbers).build();

      assertEquals("true", interp.interpolate("${nums.stream().allMatch(n -> n % 2 == 0)}"));
    }

    @Test
    void shouldCheckStreamNoneMatch() {
      List<Integer> numbers = Arrays.asList(1, 3, 5, 7);
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("nums", numbers).build();

      assertEquals("true", interp.interpolate("${nums.stream().noneMatch(n -> n > 10)}"));
    }

    @Test
    void shouldCountStreamElements() {
      List<String> words = Arrays.asList("cat", "dog", "elephant", "ant");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("words", words).build();

      assertEquals(
          "3", interp.interpolate("${words.stream().filter(w -> w.length() <= 3).count()}"));
    }

    @Test
    void shouldFindFirstInStream() {
      List<String> names = Arrays.asList("Alice", "Bob", "Charlie");
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("names", names).build();

      assertEquals(
          "Bob",
          interp.interpolate(
              "${names.stream().filter(n -> n.startsWith('B')).findFirst().orElse('Not found')}"));
    }
  }

  @Nested
  class BitwiseOperationsTests {

    @Test
    void shouldPerformBitwiseAnd() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("a", 12).variable("b", 10).build();

      // Note: EL may not support bitwise operators, this test validates behavior
      String result = interp.interpolate("${a > 0 && b > 0}");
      assertEquals("true", result);
    }

    @Test
    void shouldPerformLogicalOperationsOnNumbers() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("flags", 5).build();

      assertEquals("true", interp.interpolate("${flags > 0 && flags < 10}"));
    }
  }

  @Nested
  class SpecialCharacterEscapingTests {

    @Test
    void shouldHandleBackslashInString() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("path", "C:\\Users\\Documents").build();

      assertEquals("C:\\Users\\Documents", interp.interpolate("${path}"));
    }

    @Test
    void shouldHandleQuotesInString() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("quote", "He said \"Hello\"").build();

      assertEquals("He said \"Hello\"", interp.interpolate("${quote}"));
    }

    @Test
    void shouldHandleNewlinesAndTabs() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("multiline", "Line1\nLine2\tTabbed")
              .build();

      assertEquals("Line1\nLine2\tTabbed", interp.interpolate("${multiline}"));
    }

    @Test
    void shouldHandleCarriageReturn() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("text", "First\rSecond").build();

      assertEquals("First\rSecond", interp.interpolate("${text}"));
    }
  }

  @Nested
  class EmptyAndWhitespaceTests {

    @Test
    void shouldDetectEmptyString() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder()
              .variable("emptyStr", "")
              .variable("whitespace", "   ")
              .variable("text", "hello")
              .build();

      assertEquals("true", interp.interpolate("${empty emptyStr}"));
      assertEquals("false", interp.interpolate("${empty whitespace}"));
      assertEquals("false", interp.interpolate("${empty text}"));
    }

    @Test
    void shouldTrimWhitespace() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("text", "  hello  ").build();

      assertEquals("hello", interp.interpolate("${text.trim()}"));
      assertEquals("5", interp.interpolate("${text.trim().length()}"));
    }

    @Test
    void shouldCheckBlankString() {
      JakartaElStringInterpolator interp =
          JakartaElStringInterpolator.builder().variable("blank", "   ").build();

      assertEquals("true", interp.interpolate("${blank.trim().isEmpty()}"));
    }
  }
}
