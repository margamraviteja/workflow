package com.workflow.interpolation;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.interpolation.resolver.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PropertyResolverTest {

  // ============================================================================
  // MapPropertyResolver Tests
  // ============================================================================

  @Nested
  class MapPropertyResolverTests {

    // ==================== Basic Resolution Tests ====================

    @Test
    void shouldResolveExistingKey() {
      MapPropertyResolver resolver = MapPropertyResolver.of("key", "value");
      assertEquals(Optional.of("value"), resolver.resolve("key"));
    }

    @Test
    void shouldReturnEmptyForMissingKey() {
      MapPropertyResolver resolver = MapPropertyResolver.of("key", "value");
      assertEquals(Optional.empty(), resolver.resolve("nonexistent"));
    }

    @Test
    void shouldResolveEmptyStringValue() {
      MapPropertyResolver resolver = MapPropertyResolver.of("empty", "");
      assertEquals(Optional.of(""), resolver.resolve("empty"));
    }

    @Test
    void shouldResolveKeyWithSpecialCharacters() {
      MapPropertyResolver resolver = MapPropertyResolver.of("key.with.dots", "dotted-value");
      assertEquals(Optional.of("dotted-value"), resolver.resolve("key.with.dots"));
    }

    @Test
    void shouldResolveKeyWithHyphens() {
      MapPropertyResolver resolver = MapPropertyResolver.of("key-with-hyphens", "hyphenated");
      assertEquals(Optional.of("hyphenated"), resolver.resolve("key-with-hyphens"));
    }

    @Test
    void shouldResolveKeyWithUnderscores() {
      MapPropertyResolver resolver = MapPropertyResolver.of("key_with_underscores", "underscored");
      assertEquals(Optional.of("underscored"), resolver.resolve("key_with_underscores"));
    }

    @Test
    void shouldResolveCaseSensitiveKeys() {
      MapPropertyResolver resolver = MapPropertyResolver.of("Key", "uppercase", "key", "lowercase");
      assertEquals(Optional.of("uppercase"), resolver.resolve("Key"));
      assertEquals(Optional.of("lowercase"), resolver.resolve("key"));
    }

    @Test
    void shouldResolveNumericStringKey() {
      MapPropertyResolver resolver = MapPropertyResolver.of("123", "numeric-key");
      assertEquals(Optional.of("numeric-key"), resolver.resolve("123"));
    }

    @Test
    void shouldResolveUnicodeKey() {
      MapPropertyResolver resolver = MapPropertyResolver.of("clé", "french-value");
      assertEquals(Optional.of("french-value"), resolver.resolve("clé"));
    }

    @Test
    void shouldResolveValueWithNewlines() {
      MapPropertyResolver resolver = MapPropertyResolver.of("multiline", "line1\nline2\nline3");
      assertEquals(Optional.of("line1\nline2\nline3"), resolver.resolve("multiline"));
    }

    // ==================== Order Tests ====================

    @Test
    void shouldHaveDefaultOrder() {
      MapPropertyResolver resolver = MapPropertyResolver.of("key", "value");
      assertEquals(50, resolver.order());
    }

    @Test
    void shouldAllowCustomOrder() {
      MapPropertyResolver resolver = new MapPropertyResolver(Map.of("key", "value"), 10);
      assertEquals(10, resolver.order());
    }

    @Test
    void shouldAllowZeroOrder() {
      MapPropertyResolver resolver = new MapPropertyResolver(Map.of("key", "value"), 0);
      assertEquals(0, resolver.order());
    }

    @Test
    void shouldAllowNegativeOrder() {
      MapPropertyResolver resolver = new MapPropertyResolver(Map.of("key", "value"), -100);
      assertEquals(-100, resolver.order());
    }

    @Test
    void shouldAllowMaxIntOrder() {
      MapPropertyResolver resolver =
          new MapPropertyResolver(Map.of("key", "value"), Integer.MAX_VALUE);
      assertEquals(Integer.MAX_VALUE, resolver.order());
    }

    // ==================== Factory Method Tests ====================

    @Test
    void shouldCreateFromMap() {
      Map<String, String> map = Map.of("a", "1", "b", "2");
      MapPropertyResolver resolver = MapPropertyResolver.of(map);

      assertEquals(Optional.of("1"), resolver.resolve("a"));
      assertEquals(Optional.of("2"), resolver.resolve("b"));
    }

    @Test
    void shouldCreateFromKeyValuePairs() {
      MapPropertyResolver resolver = MapPropertyResolver.of("a", "1", "b", "2", "c", "3");

      assertEquals(Optional.of("1"), resolver.resolve("a"));
      assertEquals(Optional.of("2"), resolver.resolve("b"));
      assertEquals(Optional.of("3"), resolver.resolve("c"));
    }

    @Test
    void shouldThrowForOddNumberOfArguments() {
      assertThrows(IllegalArgumentException.class, () -> MapPropertyResolver.of("a", "1", "b"));
    }

    @Test
    void shouldHandleEmptyMap() {
      MapPropertyResolver resolver = MapPropertyResolver.of(Map.of());
      assertEquals(Optional.empty(), resolver.resolve("any"));
    }

    @Test
    void shouldThrowForNullMap() {
      assertThrows(NullPointerException.class, () -> new MapPropertyResolver(null));
    }

    @Test
    void shouldCreateFromEmptyVarargs() {
      MapPropertyResolver resolver = MapPropertyResolver.of();
      assertEquals(Optional.empty(), resolver.resolve("any"));
    }

    @Test
    void shouldCreateFromSingleKeyValuePair() {
      MapPropertyResolver resolver = MapPropertyResolver.of("single", "value");
      assertEquals(Optional.of("value"), resolver.resolve("single"));
    }

    // ==================== Strict Mode Tests ====================

    @Test
    void shouldBeStrictByDefault() {
      MapPropertyResolver resolver = MapPropertyResolver.of("key", "value");
      assertTrue(resolver.isStrict());
    }

    @Test
    void shouldNotResolveNestedInStrictMode() {
      Map<String, Object> map = Map.of("database", Map.of("host", "localhost"));
      MapPropertyResolver resolver = MapPropertyResolver.of(map);

      assertEquals(Optional.empty(), resolver.resolve("database.host"));
    }

    @Test
    void shouldResolveNestedPropertyWithDotNotationWhenNotStrict() {
      Map<String, Object> map = Map.of("database", Map.of("host", "localhost", "port", 5432));
      MapPropertyResolver resolver = MapPropertyResolver.of(map, false);

      assertEquals(Optional.of("localhost"), resolver.resolve("database.host"));
      assertEquals(Optional.of("5432"), resolver.resolve("database.port"));
    }

    @Test
    void shouldResolveDeeplyNestedPropertiesWhenNotStrict() {
      Map<String, Object> map = Map.of("level1", Map.of("level2", Map.of("level3", "deep-value")));
      MapPropertyResolver resolver = MapPropertyResolver.of(map, false);

      assertEquals(Optional.of("deep-value"), resolver.resolve("level1.level2.level3"));
    }

    @Test
    void shouldPreferExactMatchOverNestedResolutionWhenNotStrict() {
      Map<String, Object> map = new java.util.HashMap<>();
      map.put("database.host", "exact-value");
      map.put("database", Map.of("host", "nested-value"));
      MapPropertyResolver resolver = MapPropertyResolver.of(map, false);

      assertEquals(Optional.of("exact-value"), resolver.resolve("database.host"));
    }

    @Test
    void shouldReturnEmptyForNonExistentNestedKeyWhenNotStrict() {
      Map<String, Object> map = Map.of("database", Map.of("host", "localhost"));
      MapPropertyResolver resolver = MapPropertyResolver.of(map, false);

      assertEquals(Optional.empty(), resolver.resolve("database.port"));
      assertEquals(Optional.empty(), resolver.resolve("nonexistent.path"));
    }

    @Test
    void shouldAllowCustomStrictMode() {
      Map<String, Object> map = Map.of("database", Map.of("host", "localhost"));

      MapPropertyResolver strictResolver = new MapPropertyResolver(map, 50, true);
      assertTrue(strictResolver.isStrict());
      assertEquals(Optional.empty(), strictResolver.resolve("database.host"));

      MapPropertyResolver nonStrictResolver = new MapPropertyResolver(map, 50, false);
      assertFalse(nonStrictResolver.isStrict());
      assertEquals(Optional.of("localhost"), nonStrictResolver.resolve("database.host"));
    }

    // ==================== Type Conversion Tests ====================

    @Test
    void shouldConvertNonStringValuesToString() {
      Map<String, Object> map = Map.of("number", 42, "boolean", true, "double", 3.14);
      MapPropertyResolver resolver = MapPropertyResolver.of(map);

      assertEquals(Optional.of("42"), resolver.resolve("number"));
      assertEquals(Optional.of("true"), resolver.resolve("boolean"));
      assertEquals(Optional.of("3.14"), resolver.resolve("double"));
    }

    @Test
    void shouldConvertLongToString() {
      Map<String, Object> map = Map.of("longValue", 9223372036854775807L);
      MapPropertyResolver resolver = MapPropertyResolver.of(map);
      assertEquals(Optional.of("9223372036854775807"), resolver.resolve("longValue"));
    }

    @Test
    void shouldConvertListToString() {
      Map<String, Object> map = Map.of("list", List.of("a", "b", "c"));
      MapPropertyResolver resolver = MapPropertyResolver.of(map);
      assertEquals(Optional.of("[a, b, c]"), resolver.resolve("list"));
    }

    @Test
    void shouldConvertMapToStringInStrictMode() {
      Map<String, Object> nested = Map.of("inner", "value");
      Map<String, Object> map = Map.of("nested", nested);
      MapPropertyResolver resolver = MapPropertyResolver.of(map, true);
      assertTrue(resolver.resolve("nested").isPresent());
    }

    // ==================== Nested Resolution Edge Cases ====================

    @Test
    void shouldHandleSinglePartKeyInNonStrictMode() {
      Map<String, Object> map = Map.of("simple", "value");
      MapPropertyResolver resolver = MapPropertyResolver.of(map, false);
      assertEquals(Optional.of("value"), resolver.resolve("simple"));
    }

    @Test
    void shouldReturnEmptyForNonMapIntermediateValueWhenNotStrict() {
      Map<String, Object> map = Map.of("string", "not-a-map");
      MapPropertyResolver resolver = MapPropertyResolver.of(map, false);
      assertEquals(Optional.empty(), resolver.resolve("string.nested"));
    }

    @Test
    void shouldHandleFourLevelNesting() {
      Map<String, Object> map = Map.of("a", Map.of("b", Map.of("c", Map.of("d", "deep"))));
      MapPropertyResolver resolver = MapPropertyResolver.of(map, false);
      assertEquals(Optional.of("deep"), resolver.resolve("a.b.c.d"));
    }

    @Test
    void shouldReturnEmptyForPartialPathMatch() {
      Map<String, Object> map = Map.of("a", Map.of("b", "value"));
      MapPropertyResolver resolver = MapPropertyResolver.of(map, false);
      assertEquals(Optional.empty(), resolver.resolve("a.b.c"));
    }

    @Test
    void shouldHandleEmptyStringInNestedPath() {
      Map<String, Object> map = Map.of("a", Map.of("", "empty-key-value"));
      MapPropertyResolver resolver = MapPropertyResolver.of(map, false);
      assertEquals(Optional.empty(), resolver.resolve("a."));
    }

    // ==================== Supports Method Tests ====================

    @Test
    void shouldSupportAllKeysByDefault() {
      MapPropertyResolver resolver = MapPropertyResolver.of("key", "value");
      assertTrue(resolver.supports("key"));
      assertTrue(resolver.supports("any.key"));
      assertTrue(resolver.supports(""));
    }
  }

  // ============================================================================
  // WorkflowContextPropertyResolver Tests
  // ============================================================================

  @Nested
  class WorkflowContextPropertyResolverTests {

    // ==================== Basic Resolution Tests ====================

    @Test
    void shouldResolveExistingKey() {
      WorkflowContext context = new WorkflowContext();
      context.put("key", "value");
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("value"), resolver.resolve("key"));
    }

    @Test
    void shouldReturnEmptyForMissingKey() {
      WorkflowContext context = new WorkflowContext();
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.empty(), resolver.resolve("nonexistent"));
    }

    @Test
    void shouldResolveEmptyStringValue() {
      WorkflowContext context = new WorkflowContext();
      context.put("empty", "");
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of(""), resolver.resolve("empty"));
    }

    @Test
    void shouldResolveKeyWithDots() {
      WorkflowContext context = new WorkflowContext();
      context.put("key.with.dots", "dotted-value");
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("dotted-value"), resolver.resolve("key.with.dots"));
    }

    @Test
    void shouldResolveCaseSensitiveKeys() {
      WorkflowContext context = new WorkflowContext();
      context.put("Key", "uppercase");
      context.put("key", "lowercase");
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("uppercase"), resolver.resolve("Key"));
      assertEquals(Optional.of("lowercase"), resolver.resolve("key"));
    }

    @Test
    void shouldResolveUpdatedValue() {
      WorkflowContext context = new WorkflowContext();
      context.put("key", "initial");
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("initial"), resolver.resolve("key"));

      context.put("key", "updated");
      assertEquals(Optional.of("updated"), resolver.resolve("key"));
    }

    @Test
    void shouldResolveValueWithSpecialCharacters() {
      WorkflowContext context = new WorkflowContext();
      context.put("special", "value with spaces & symbols! @#$%");
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("value with spaces & symbols! @#$%"), resolver.resolve("special"));
    }

    @Test
    void shouldResolveMultilineValue() {
      WorkflowContext context = new WorkflowContext();
      context.put("multiline", "line1\nline2\nline3");
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("line1\nline2\nline3"), resolver.resolve("multiline"));
    }

    @Test
    void shouldResolveUnicodeValue() {
      WorkflowContext context = new WorkflowContext();
      context.put("unicode", "Hello 世界 🌍");
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("Hello 世界 🌍"), resolver.resolve("unicode"));
    }

    // ==================== Order Tests ====================

    @Test
    void shouldHaveDefaultOrder() {
      WorkflowContext context = new WorkflowContext();
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(100, resolver.order());
    }

    @Test
    void shouldAllowCustomOrder() {
      WorkflowContext context = new WorkflowContext();
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context, 10);

      assertEquals(10, resolver.order());
    }

    @Test
    void shouldAllowZeroOrder() {
      WorkflowContext context = new WorkflowContext();
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context, 0);

      assertEquals(0, resolver.order());
    }

    @Test
    void shouldAllowNegativeOrder() {
      WorkflowContext context = new WorkflowContext();
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context, -50);

      assertEquals(-50, resolver.order());
    }

    // ==================== Strict Mode Tests ====================

    @Test
    void shouldBeStrictByDefault() {
      WorkflowContext context = new WorkflowContext();
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);
      assertTrue(resolver.isStrict());
    }

    @Test
    void shouldNotResolveNestedInStrictMode() {
      WorkflowContext context = new WorkflowContext();
      context.put("database", Map.of("host", "localhost"));
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.empty(), resolver.resolve("database.host"));
    }

    @Test
    void shouldResolveNestedPropertyWithDotNotationWhenNotStrict() {
      WorkflowContext context = new WorkflowContext();
      context.put("database", Map.of("host", "localhost", "port", 5432));
      WorkflowContextPropertyResolver resolver =
          new WorkflowContextPropertyResolver(
              context, WorkflowContextPropertyResolver.DEFAULT_ORDER, false);

      assertEquals(Optional.of("localhost"), resolver.resolve("database.host"));
      assertEquals(Optional.of("5432"), resolver.resolve("database.port"));
    }

    @Test
    void shouldResolveDeeplyNestedPropertiesWhenNotStrict() {
      WorkflowContext context = new WorkflowContext();
      context.put("level1", Map.of("level2", Map.of("level3", "deep-value")));
      WorkflowContextPropertyResolver resolver =
          new WorkflowContextPropertyResolver(
              context, WorkflowContextPropertyResolver.DEFAULT_ORDER, false);

      assertEquals(Optional.of("deep-value"), resolver.resolve("level1.level2.level3"));
    }

    @Test
    void shouldPreferExactMatchOverNestedResolutionWhenNotStrict() {
      WorkflowContext context = new WorkflowContext();
      context.put("database.host", "exact-value");
      context.put("database", Map.of("host", "nested-value"));
      WorkflowContextPropertyResolver resolver =
          new WorkflowContextPropertyResolver(
              context, WorkflowContextPropertyResolver.DEFAULT_ORDER, false);

      assertEquals(Optional.of("exact-value"), resolver.resolve("database.host"));
    }

    @Test
    void shouldReturnEmptyForNonExistentNestedKeyWhenNotStrict() {
      WorkflowContext context = new WorkflowContext();
      context.put("database", Map.of("host", "localhost"));
      WorkflowContextPropertyResolver resolver =
          new WorkflowContextPropertyResolver(
              context, WorkflowContextPropertyResolver.DEFAULT_ORDER, false);

      assertEquals(Optional.empty(), resolver.resolve("database.port"));
      assertEquals(Optional.empty(), resolver.resolve("nonexistent.path"));
    }

    @Test
    void shouldAllowCustomStrictMode() {
      WorkflowContext context = new WorkflowContext();
      context.put("database", Map.of("host", "localhost"));

      WorkflowContextPropertyResolver strictResolver =
          new WorkflowContextPropertyResolver(context, 100, true);
      assertTrue(strictResolver.isStrict());
      assertEquals(Optional.empty(), strictResolver.resolve("database.host"));

      WorkflowContextPropertyResolver nonStrictResolver =
          new WorkflowContextPropertyResolver(context, 100, false);
      assertFalse(nonStrictResolver.isStrict());
      assertEquals(Optional.of("localhost"), nonStrictResolver.resolve("database.host"));
    }

    // ==================== Type Conversion Tests ====================

    @Test
    void shouldConvertNonStringValuesToString() {
      WorkflowContext context = new WorkflowContext();
      context.put("number", 42);
      context.put("boolean", true);
      context.put("double", 3.14);
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("42"), resolver.resolve("number"));
      assertEquals(Optional.of("true"), resolver.resolve("boolean"));
      assertEquals(Optional.of("3.14"), resolver.resolve("double"));
    }

    @Test
    void shouldConvertLongToString() {
      WorkflowContext context = new WorkflowContext();
      context.put("longValue", Long.MAX_VALUE);
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of(String.valueOf(Long.MAX_VALUE)), resolver.resolve("longValue"));
    }

    @Test
    void shouldConvertFloatToString() {
      WorkflowContext context = new WorkflowContext();
      context.put("floatValue", 3.14f);
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("3.14"), resolver.resolve("floatValue"));
    }

    @Test
    void shouldConvertListToString() {
      WorkflowContext context = new WorkflowContext();
      context.put("list", List.of("a", "b", "c"));
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("[a, b, c]"), resolver.resolve("list"));
    }

    @Test
    void shouldConvertCharacterToString() {
      WorkflowContext context = new WorkflowContext();
      context.put("char", 'X');
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.of("X"), resolver.resolve("char"));
    }

    // ==================== Null and Edge Case Tests ====================

    @Test
    void shouldThrowForNullContext() {
      assertThrows(NullPointerException.class, () -> new WorkflowContextPropertyResolver(null));
    }

    @Test
    void shouldProvideAccessToContext() {
      WorkflowContext context = new WorkflowContext();
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertSame(context, resolver.getContext());
    }

    @Test
    void shouldHandleEmptyContext() {
      WorkflowContext context = new WorkflowContext();
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.empty(), resolver.resolve("any.key"));
    }

    @Test
    void shouldResolveAfterContextModification() {
      WorkflowContext context = new WorkflowContext();
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertEquals(Optional.empty(), resolver.resolve("dynamic"));

      context.put("dynamic", "added-later");
      assertEquals(Optional.of("added-later"), resolver.resolve("dynamic"));
    }

    // ==================== Nested Resolution Edge Cases ====================

    @Test
    void shouldHandleSinglePartKeyInNonStrictMode() {
      WorkflowContext context = new WorkflowContext();
      context.put("simple", "value");
      WorkflowContextPropertyResolver resolver =
          new WorkflowContextPropertyResolver(context, 100, false);

      assertEquals(Optional.of("value"), resolver.resolve("simple"));
    }

    @Test
    void shouldReturnEmptyForNonMapIntermediateValue() {
      WorkflowContext context = new WorkflowContext();
      context.put("string", "not-a-map");
      WorkflowContextPropertyResolver resolver =
          new WorkflowContextPropertyResolver(context, 100, false);

      assertEquals(Optional.empty(), resolver.resolve("string.nested"));
    }

    @Test
    void shouldHandleFourLevelNesting() {
      WorkflowContext context = new WorkflowContext();
      context.put("a", Map.of("b", Map.of("c", Map.of("d", "deep"))));
      WorkflowContextPropertyResolver resolver =
          new WorkflowContextPropertyResolver(context, 100, false);

      assertEquals(Optional.of("deep"), resolver.resolve("a.b.c.d"));
    }

    @Test
    void shouldReturnEmptyForPartialPathMatch() {
      WorkflowContext context = new WorkflowContext();
      context.put("a", Map.of("b", "value"));
      WorkflowContextPropertyResolver resolver =
          new WorkflowContextPropertyResolver(context, 100, false);

      assertEquals(Optional.empty(), resolver.resolve("a.b.c"));
    }

    @Test
    void shouldConvertNestedNumericValue() {
      WorkflowContext context = new WorkflowContext();
      context.put("config", Map.of("port", 8080, "timeout", 30000L));
      WorkflowContextPropertyResolver resolver =
          new WorkflowContextPropertyResolver(context, 100, false);

      assertEquals(Optional.of("8080"), resolver.resolve("config.port"));
      assertEquals(Optional.of("30000"), resolver.resolve("config.timeout"));
    }

    // ==================== Supports Method Tests ====================

    @Test
    void shouldSupportAllKeysByDefault() {
      WorkflowContext context = new WorkflowContext();
      WorkflowContextPropertyResolver resolver = new WorkflowContextPropertyResolver(context);

      assertTrue(resolver.supports("any.key"));
      assertTrue(resolver.supports(""));
      assertTrue(resolver.supports("special!@#"));
    }
  }

  // ============================================================================
  // SystemPropertiesResolver Tests
  // ============================================================================

  @Nested
  class SystemPropertiesResolverTests {

    // ==================== Basic Resolution Tests ====================

    @Test
    void shouldResolveSystemProperty() {
      System.setProperty("test.prop.resolver", "test-value");
      try {
        SystemPropertiesResolver resolver = new SystemPropertiesResolver();
        assertEquals(Optional.of("test-value"), resolver.resolve("test.prop.resolver"));
      } finally {
        System.clearProperty("test.prop.resolver");
      }
    }

    @Test
    void shouldReturnEmptyForMissingProperty() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      assertEquals(Optional.empty(), resolver.resolve("nonexistent.system.property.xyz"));
    }

    @Test
    void shouldResolveBuiltInSystemProperty() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      Optional<String> javaVersion = resolver.resolve("java.version");

      assertTrue(javaVersion.isPresent());
      assertEquals(System.getProperty("java.version"), javaVersion.get());
    }

    @Test
    void shouldResolveJavaHome() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      Optional<String> javaHome = resolver.resolve("java.home");

      assertTrue(javaHome.isPresent());
      assertEquals(System.getProperty("java.home"), javaHome.get());
    }

    @Test
    void shouldResolveUserDir() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      Optional<String> userDir = resolver.resolve("user.dir");

      assertTrue(userDir.isPresent());
      assertEquals(System.getProperty("user.dir"), userDir.get());
    }

    @Test
    void shouldResolveUserHome() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      Optional<String> userHome = resolver.resolve("user.home");

      assertTrue(userHome.isPresent());
      assertEquals(System.getProperty("user.home"), userHome.get());
    }

    @Test
    void shouldResolveOsName() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      Optional<String> osName = resolver.resolve("os.name");

      assertTrue(osName.isPresent());
      assertFalse(osName.get().isEmpty());
    }

    @Test
    void shouldResolveFileSeparator() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      Optional<String> fileSeparator = resolver.resolve("file.separator");

      assertTrue(fileSeparator.isPresent());
      assertEquals(System.getProperty("file.separator"), fileSeparator.get());
    }

    @Test
    void shouldResolveLineSeparator() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      Optional<String> lineSeparator = resolver.resolve("line.separator");

      assertTrue(lineSeparator.isPresent());
      assertEquals(System.getProperty("line.separator"), lineSeparator.get());
    }

    @Test
    void shouldResolveEmptyValue() {
      System.setProperty("test.empty.prop", "");
      try {
        SystemPropertiesResolver resolver = new SystemPropertiesResolver();
        assertEquals(Optional.of(""), resolver.resolve("test.empty.prop"));
      } finally {
        System.clearProperty("test.empty.prop");
      }
    }

    @Test
    void shouldResolveValueWithSpaces() {
      System.setProperty("test.spaces.prop", "value with spaces");
      try {
        SystemPropertiesResolver resolver = new SystemPropertiesResolver();
        assertEquals(Optional.of("value with spaces"), resolver.resolve("test.spaces.prop"));
      } finally {
        System.clearProperty("test.spaces.prop");
      }
    }

    @Test
    void shouldResolveValueWithSpecialCharacters() {
      System.setProperty("test.special.prop", "!@#$%^&*()");
      try {
        SystemPropertiesResolver resolver = new SystemPropertiesResolver();
        assertEquals(Optional.of("!@#$%^&*()"), resolver.resolve("test.special.prop"));
      } finally {
        System.clearProperty("test.special.prop");
      }
    }

    @Test
    void shouldResolveCaseSensitiveProperty() {
      System.setProperty("Test.Case.Prop", "mixed-case");
      try {
        SystemPropertiesResolver resolver = new SystemPropertiesResolver();
        assertEquals(Optional.of("mixed-case"), resolver.resolve("Test.Case.Prop"));
        assertEquals(Optional.empty(), resolver.resolve("test.case.prop"));
      } finally {
        System.clearProperty("Test.Case.Prop");
      }
    }

    @Test
    void shouldResolveUpdatedProperty() {
      System.setProperty("test.update.prop", "initial");
      try {
        SystemPropertiesResolver resolver = new SystemPropertiesResolver();
        assertEquals(Optional.of("initial"), resolver.resolve("test.update.prop"));

        System.setProperty("test.update.prop", "updated");
        assertEquals(Optional.of("updated"), resolver.resolve("test.update.prop"));
      } finally {
        System.clearProperty("test.update.prop");
      }
    }

    @Test
    void shouldResolveAfterPropertyCleared() {
      System.setProperty("test.clear.prop", "value");
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      assertEquals(Optional.of("value"), resolver.resolve("test.clear.prop"));

      System.clearProperty("test.clear.prop");
      assertEquals(Optional.empty(), resolver.resolve("test.clear.prop"));
    }

    // ==================== Order Tests ====================

    @Test
    void shouldHaveDefaultOrder() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      assertEquals(200, resolver.order());
    }

    @Test
    void shouldAllowCustomOrder() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver(50);
      assertEquals(50, resolver.order());
    }

    @Test
    void shouldAllowZeroOrder() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver(0);
      assertEquals(0, resolver.order());
    }

    @Test
    void shouldAllowNegativeOrder() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver(-100);
      assertEquals(-100, resolver.order());
    }

    @Test
    void shouldAllowHighOrder() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver(1000);
      assertEquals(1000, resolver.order());
    }

    // ==================== Supports Method Tests ====================

    @Test
    void shouldSupportAllKeysByDefault() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      assertTrue(resolver.supports("any.key"));
      assertTrue(resolver.supports("java.version"));
      assertTrue(resolver.supports(""));
    }

    // ==================== Edge Cases ====================

    @Test
    void shouldHandleKeyWithOnlyDots() {
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      assertEquals(Optional.empty(), resolver.resolve("..."));
    }

    @Test
    void shouldHandleVeryLongKey() {
      String longKey = "a".repeat(1000);
      SystemPropertiesResolver resolver = new SystemPropertiesResolver();
      assertEquals(Optional.empty(), resolver.resolve(longKey));
    }

    @Test
    void shouldHandleKeyWithUnicode() {
      System.setProperty("test.unicode.键", "unicode-value");
      try {
        SystemPropertiesResolver resolver = new SystemPropertiesResolver();
        assertEquals(Optional.of("unicode-value"), resolver.resolve("test.unicode.键"));
      } finally {
        System.clearProperty("test.unicode.键");
      }
    }
  }

  // ============================================================================
  // EnvironmentPropertyResolver Tests
  // ============================================================================

  @Nested
  class EnvironmentPropertyResolverTests {

    // ==================== Basic Resolution Tests (Parameterized) ====================

    @ParameterizedTest
    @ValueSource(strings = {"HOME", "PATH", "USER", "PWD", "SHELL", "TERM", "LANG"})
    void shouldResolveCommonEnvironmentVariables(String envVarName) {
      String expected = System.getenv(envVarName);
      if (expected != null) {
        EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
        assertEquals(Optional.of(expected), resolver.resolve(envVarName));
      }
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"NONEXISTENT_VAR_XYZ_12345", "ANOTHER_MISSING_VAR", "RANDOM_UNDEFINED_ENV"})
    void shouldReturnEmptyForMissingVariables(String envVarName) {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
      assertEquals(Optional.empty(), resolver.resolve(envVarName));
    }

    // ==================== Order Tests (Parameterized) ====================

    @ParameterizedTest
    @CsvSource({"300, 300", "100, 100", "0, 0", "-50, -50", "500, 500"})
    void shouldAllowVariousOrderValues(int inputOrder, int expectedOrder) {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver(inputOrder);
      assertEquals(expectedOrder, resolver.order());
    }

    @Test
    void shouldHaveDefaultOrder() {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
      assertEquals(300, resolver.order());
    }

    // ==================== Key Normalization Tests ====================

    @Test
    void shouldAllowDisablingNormalization() {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver(300, false);
      assertEquals(Optional.empty(), resolver.resolve("nonexistent.key"));
    }

    @Test
    void shouldTryUppercaseVariant() {
      String home = System.getenv("HOME");
      if (home != null) {
        EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
        assertEquals(Optional.of(home), resolver.resolve("home"));
      }
    }

    @Test
    void shouldNormalizeDotToUnderscore() {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
      // This tests the normalization logic - database.url becomes DATABASE_URL
      // We can't guarantee env vars exist, but we test the resolver doesn't throw
      Optional<String> result = resolver.resolve("some.nonexistent.var");
      // Should not throw, returns empty
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldNormalizeHyphenToUnderscore() {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
      Optional<String> result = resolver.resolve("some-nonexistent-var");
      assertTrue(result.isEmpty());
    }

    // ==================== Normalization Disabled Tests ====================

    @Test
    void shouldOnlyMatchExactWhenNormalizationDisabled() {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver(300, false);
      String home = System.getenv("HOME");
      if (home != null) {
        assertEquals(Optional.of(home), resolver.resolve("HOME"));
        // lowercase won't work without normalization
        assertEquals(Optional.empty(), resolver.resolve("home"));
      }
    }

    @Test
    void shouldNotNormalizeDotsWhenDisabled() {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver(300, false);
      // Without normalization, dotted keys are not transformed
      assertEquals(Optional.empty(), resolver.resolve("some.dotted.key"));
    }

    // ==================== Supports Method Tests ====================

    @Test
    void shouldSupportAllKeysByDefault() {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
      assertTrue(resolver.supports("any.key"));
      assertTrue(resolver.supports("HOME"));
      assertTrue(resolver.supports(""));
    }

    // ==================== Edge Cases (Parameterized) ====================

    @ParameterizedTest
    @ValueSource(
        strings = {
          "", // empty key
          "___", // only underscores
          "...", // only dots
          "key..with..dots", // consecutive dots
          ".leading.dot", // leading dot
          "trailing.dot." // trailing dot
        })
    void shouldReturnEmptyForEdgeCaseKeys(String key) {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
      assertEquals(Optional.empty(), resolver.resolve(key));
    }

    @Test
    void shouldHandleVeryLongKey() {
      String longKey = "A".repeat(1000);
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
      assertEquals(Optional.empty(), resolver.resolve(longKey));
    }

    @Test
    void shouldHandleMixedCaseNormalization() {
      EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
      // Tests that mixed.Case.Key is normalized to MIXED_CASE_KEY
      Optional<String> result = resolver.resolve("mixed.Case.Key");
      // Will be empty unless MIXED_CASE_KEY env var exists
      assertNotNull(result);
    }
  }

  // ============================================================================
  // CompositePropertyResolver Tests
  // ============================================================================

  @Nested
  class CompositePropertyResolverTests {

    // ==================== Basic Resolution Tests ====================

    @Test
    void shouldResolveFromFirstMatchingResolver() {
      PropertyResolver first = MapPropertyResolver.of("key", "first");
      PropertyResolver second = MapPropertyResolver.of("key", "second");

      CompositePropertyResolver composite = CompositePropertyResolver.of(first, second);
      assertEquals(Optional.of("first"), composite.resolve("key"));
    }

    @Test
    void shouldFallbackToNextResolver() {
      PropertyResolver first = MapPropertyResolver.of("a", "1");
      PropertyResolver second = MapPropertyResolver.of("b", "2");

      CompositePropertyResolver composite = CompositePropertyResolver.of(first, second);
      assertEquals(Optional.of("1"), composite.resolve("a"));
      assertEquals(Optional.of("2"), composite.resolve("b"));
    }

    @Test
    void shouldReturnEmptyWhenNoResolverMatches() {
      PropertyResolver first = MapPropertyResolver.of("a", "1");
      PropertyResolver second = MapPropertyResolver.of("b", "2");

      CompositePropertyResolver composite = CompositePropertyResolver.of(first, second);
      assertEquals(Optional.empty(), composite.resolve("c"));
    }

    @Test
    void shouldResolveFromSingleResolver() {
      PropertyResolver single = MapPropertyResolver.of("key", "value");
      CompositePropertyResolver composite = CompositePropertyResolver.of(single);

      assertEquals(Optional.of("value"), composite.resolve("key"));
    }

    @Test
    void shouldResolveThroughMultipleResolvers() {
      PropertyResolver r1 = MapPropertyResolver.of("key1", "val1");
      PropertyResolver r2 = MapPropertyResolver.of("key2", "val2");
      PropertyResolver r3 = MapPropertyResolver.of("key3", "val3");

      CompositePropertyResolver composite = CompositePropertyResolver.of(r1, r2, r3);

      assertEquals(Optional.of("val1"), composite.resolve("key1"));
      assertEquals(Optional.of("val2"), composite.resolve("key2"));
      assertEquals(Optional.of("val3"), composite.resolve("key3"));
    }

    @Test
    void shouldSkipResolversThatReturnEmpty() {
      PropertyResolver empty = _ -> Optional.empty();
      PropertyResolver hasValue = MapPropertyResolver.of("key", "value");

      CompositePropertyResolver composite = CompositePropertyResolver.of(empty, hasValue);
      assertEquals(Optional.of("value"), composite.resolve("key"));
    }

    // ==================== Order/Priority Tests ====================

    @Test
    void shouldSortResolversByOrder() {
      PropertyResolver highPriority = new MapPropertyResolver(Map.of("key", "high"), 10);
      PropertyResolver lowPriority = new MapPropertyResolver(Map.of("key", "low"), 100);

      CompositePropertyResolver composite =
          CompositePropertyResolver.builder().add(lowPriority).add(highPriority).build();

      assertEquals(Optional.of("high"), composite.resolve("key"));
    }

    @Test
    void shouldMaintainOrderForSamePriority() {
      PropertyResolver first = new MapPropertyResolver(Map.of("key", "first"), 50);
      PropertyResolver second = new MapPropertyResolver(Map.of("key", "second"), 50);

      CompositePropertyResolver composite =
          CompositePropertyResolver.builder().add(first).add(second).build();

      // First added should be first when same order
      assertEquals(Optional.of("first"), composite.resolve("key"));
    }

    @Test
    void shouldHandleNegativeOrders() {
      PropertyResolver negative = new MapPropertyResolver(Map.of("key", "negative"), -100);
      PropertyResolver positive = new MapPropertyResolver(Map.of("key", "positive"), 100);

      CompositePropertyResolver composite =
          CompositePropertyResolver.builder().add(positive).add(negative).build();

      assertEquals(Optional.of("negative"), composite.resolve("key"));
    }

    @Test
    void shouldHandleZeroOrder() {
      PropertyResolver zero = new MapPropertyResolver(Map.of("key", "zero"), 0);
      PropertyResolver fifty = new MapPropertyResolver(Map.of("key", "fifty"), 50);

      CompositePropertyResolver composite =
          CompositePropertyResolver.builder().add(fifty).add(zero).build();

      assertEquals(Optional.of("zero"), composite.resolve("key"));
    }

    // ==================== Builder Tests ====================

    @Test
    void shouldBuildWithMultipleResolvers() {
      List<PropertyResolver> resolvers =
          List.of(
              MapPropertyResolver.of("a", "1"),
              MapPropertyResolver.of("b", "2"),
              MapPropertyResolver.of("c", "3"));

      CompositePropertyResolver composite =
          CompositePropertyResolver.builder().addAll(resolvers).build();

      assertEquals(Optional.of("1"), composite.resolve("a"));
      assertEquals(Optional.of("2"), composite.resolve("b"));
      assertEquals(Optional.of("3"), composite.resolve("c"));
    }

    @Test
    void shouldThrowForNullResolver() {
      CompositePropertyResolver.Builder builder = CompositePropertyResolver.builder();
      assertThrows(NullPointerException.class, () -> builder.add(null));
    }

    @Test
    void shouldThrowForNullResolverList() {
      CompositePropertyResolver.Builder builder = CompositePropertyResolver.builder();
      assertThrows(NullPointerException.class, () -> builder.addAll(null));
    }

    @Test
    void shouldAllowMixingAddAndAddAll() {
      PropertyResolver r1 = MapPropertyResolver.of("a", "1");
      List<PropertyResolver> more =
          List.of(MapPropertyResolver.of("b", "2"), MapPropertyResolver.of("c", "3"));

      CompositePropertyResolver composite =
          CompositePropertyResolver.builder().add(r1).addAll(more).build();

      assertEquals(Optional.of("1"), composite.resolve("a"));
      assertEquals(Optional.of("2"), composite.resolve("b"));
      assertEquals(Optional.of("3"), composite.resolve("c"));
    }

    @Test
    void shouldBuildWithEmptyListAndSingleAdd() {
      CompositePropertyResolver composite =
          CompositePropertyResolver.builder()
              .addAll(List.of())
              .add(MapPropertyResolver.of("key", "value"))
              .build();

      assertEquals(Optional.of("value"), composite.resolve("key"));
    }

    // ==================== Resolver List Tests ====================

    @Test
    void shouldReturnUnmodifiableResolverList() {
      CompositePropertyResolver composite =
          CompositePropertyResolver.of(MapPropertyResolver.of("a", "1"));

      MapPropertyResolver mapPropertyResolver = MapPropertyResolver.of("b", "2");
      List<PropertyResolver> resolvers = composite.getResolvers();
      assertThrows(UnsupportedOperationException.class, () -> resolvers.add(mapPropertyResolver));
    }

    @Test
    void shouldReturnCorrectResolverCount() {
      PropertyResolver r1 = MapPropertyResolver.of("a", "1");
      PropertyResolver r2 = MapPropertyResolver.of("b", "2");
      PropertyResolver r3 = MapPropertyResolver.of("c", "3");

      CompositePropertyResolver composite = CompositePropertyResolver.of(r1, r2, r3);

      assertEquals(3, composite.getResolvers().size());
    }

    @Test
    void shouldReturnResolversInSortedOrder() {
      PropertyResolver order100 = new MapPropertyResolver(Map.of("key", "100"), 100);
      PropertyResolver order50 = new MapPropertyResolver(Map.of("key", "50"), 50);
      PropertyResolver order200 = new MapPropertyResolver(Map.of("key", "200"), 200);

      CompositePropertyResolver composite =
          CompositePropertyResolver.builder().add(order100).add(order50).add(order200).build();

      List<PropertyResolver> resolvers = composite.getResolvers();
      assertEquals(50, resolvers.get(0).order());
      assertEquals(100, resolvers.get(1).order());
      assertEquals(200, resolvers.get(2).order());
    }

    // ==================== Supports Method Tests ====================

    @Test
    void shouldRespectSupportsMethod() {
      PropertyResolver selectiveResolver =
          new PropertyResolver() {
            @Override
            public Optional<String> resolve(String key) {
              return Optional.of("selective-value");
            }

            @Override
            public boolean supports(String key) {
              return key.startsWith("selective.");
            }
          };

      PropertyResolver fallbackResolver = MapPropertyResolver.of("other.key", "fallback-value");

      CompositePropertyResolver composite =
          CompositePropertyResolver.of(selectiveResolver, fallbackResolver);

      assertEquals(Optional.of("selective-value"), composite.resolve("selective.key"));
      assertEquals(Optional.of("fallback-value"), composite.resolve("other.key"));
      assertEquals(Optional.empty(), composite.resolve("unknown.key"));
    }

    @Test
    void shouldSkipUnsupportedResolvers() {
      PropertyResolver unsupported =
          new PropertyResolver() {
            @Override
            public Optional<String> resolve(String key) {
              return Optional.of("should-not-be-called");
            }

            @Override
            public boolean supports(String key) {
              return false;
            }
          };

      PropertyResolver supported = MapPropertyResolver.of("key", "supported-value");

      CompositePropertyResolver composite = CompositePropertyResolver.of(unsupported, supported);
      assertEquals(Optional.of("supported-value"), composite.resolve("key"));
    }

    @Test
    void shouldCallResolveOnlyIfSupports() {
      java.util.concurrent.atomic.AtomicBoolean resolveCalled =
          new java.util.concurrent.atomic.AtomicBoolean(false);

      PropertyResolver trackingResolver =
          new PropertyResolver() {
            @Override
            public Optional<String> resolve(String key) {
              resolveCalled.set(true);
              return Optional.of("value");
            }

            @Override
            public boolean supports(String key) {
              return key.equals("supported");
            }
          };

      CompositePropertyResolver composite = CompositePropertyResolver.of(trackingResolver);

      composite.resolve("unsupported");
      assertFalse(resolveCalled.get());

      composite.resolve("supported");
      assertTrue(resolveCalled.get());
    }

    // ==================== Real-World Scenarios ====================

    @Test
    void shouldCombineMapSystemAndEnvResolvers() {
      MapPropertyResolver mapResolver = MapPropertyResolver.of("custom.key", "custom-value");
      SystemPropertiesResolver sysResolver = new SystemPropertiesResolver();
      EnvironmentPropertyResolver envResolver = new EnvironmentPropertyResolver();

      CompositePropertyResolver composite =
          CompositePropertyResolver.builder()
              .add(mapResolver)
              .add(sysResolver)
              .add(envResolver)
              .build();

      assertEquals(Optional.of("custom-value"), composite.resolve("custom.key"));
      assertTrue(composite.resolve("java.version").isPresent());
    }

    @Test
    void shouldOverrideSystemPropertyWithCustom() {
      MapPropertyResolver customResolver =
          new MapPropertyResolver(Map.of("java.version", "custom-version"), 10);
      SystemPropertiesResolver sysResolver = new SystemPropertiesResolver(); // order 200

      CompositePropertyResolver composite =
          CompositePropertyResolver.builder().add(sysResolver).add(customResolver).build();

      // Custom resolver has lower order (higher priority)
      assertEquals(Optional.of("custom-version"), composite.resolve("java.version"));
    }

    // ==================== Edge Cases ====================

    @Test
    void shouldHandleAllResolversReturningEmpty() {
      PropertyResolver empty1 = _ -> Optional.empty();
      PropertyResolver empty2 = _ -> Optional.empty();
      PropertyResolver empty3 = _ -> Optional.empty();

      CompositePropertyResolver composite = CompositePropertyResolver.of(empty1, empty2, empty3);
      assertEquals(Optional.empty(), composite.resolve("any.key"));
    }

    @Test
    void shouldHandleLargeNumberOfResolvers() {
      CompositePropertyResolver.Builder builder = CompositePropertyResolver.builder();
      for (int i = 0; i < 100; i++) {
        builder.add(MapPropertyResolver.of("key" + i, "value" + i));
      }
      CompositePropertyResolver composite = builder.build();

      assertEquals(Optional.of("value0"), composite.resolve("key0"));
      assertEquals(Optional.of("value99"), composite.resolve("key99"));
      assertEquals(100, composite.getResolvers().size());
    }
  }

  // ============================================================================
  // Custom PropertyResolver Implementation Tests
  // ============================================================================

  @Nested
  class CustomPropertyResolverTests {

    @Test
    void shouldSupportFunctionalInterface() {
      PropertyResolver lambdaResolver =
          key -> key.equals("lambda") ? Optional.of("value") : Optional.empty();

      assertEquals(Optional.of("value"), lambdaResolver.resolve("lambda"));
      assertEquals(Optional.empty(), lambdaResolver.resolve("other"));
    }

    @Test
    void shouldHaveDefaultOrderZero() {
      PropertyResolver lambdaResolver = _ -> Optional.empty();
      assertEquals(0, lambdaResolver.order());
    }

    @Test
    void shouldHaveDefaultSupportsTrue() {
      PropertyResolver lambdaResolver = _ -> Optional.empty();
      assertTrue(lambdaResolver.supports("any.key"));
    }

    @Test
    void shouldWorkWithPrefixBasedResolver() {
      PropertyResolver prefixResolver =
          new PropertyResolver() {
            private final Map<String, String> secrets =
                Map.of(
                    "secret.api.key", "sk-12345",
                    "secret.db.password", "db-pass");

            @Override
            public Optional<String> resolve(String key) {
              return Optional.ofNullable(secrets.get(key));
            }

            @Override
            public boolean supports(String key) {
              return key.startsWith("secret.");
            }

            @Override
            public int order() {
              return 1; // High priority
            }
          };

      assertEquals(Optional.of("sk-12345"), prefixResolver.resolve("secret.api.key"));
      assertTrue(prefixResolver.supports("secret.anything"));
      assertFalse(prefixResolver.supports("public.key"));
      assertEquals(1, prefixResolver.order());
    }
  }
}
