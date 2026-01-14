package com.workflow.interpolation;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.interpolation.exception.InterpolationException;
import com.workflow.interpolation.resolver.*;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultStringInterpolatorTest {

  private StringInterpolator interpolator;

  @BeforeEach
  void setUp() {
    interpolator =
        DefaultStringInterpolator.builder()
            .addResolver(
                MapPropertyResolver.of(
                    "user.name", "John",
                    "app.version", "1.0.0",
                    "greeting", "Hello",
                    "nested.ref", "${user.name}",
                    "deeply.nested", "${nested.ref} is great!",
                    "circular.a", "${circular.b}",
                    "circular.b", "${circular.a}"))
            .addResolver(new SystemPropertiesResolver())
            .addResolver(new EnvironmentPropertyResolver())
            .build();
  }

  @Nested
  class SimplePlaceholderResolution {

    @Test
    void shouldResolveSimplePlaceholder() {
      String result = interpolator.interpolate("Hello, ${user.name}!");
      assertEquals("Hello, John!", result);
    }

    @Test
    void shouldResolveMultiplePlaceholders() {
      String result =
          interpolator.interpolate("${greeting}, ${user.name}! Version: ${app.version}");
      assertEquals("Hello, John! Version: 1.0.0", result);
    }

    @Test
    void shouldHandleNullInput() {
      assertNull(interpolator.interpolate(null));
    }

    @Test
    void shouldHandleEmptyInput() {
      assertEquals("", interpolator.interpolate(""));
    }
  }

  @Nested
  class DefaultValueResolution {

    @Test
    void shouldUseDefaultWhenKeyNotFound() {
      String result = interpolator.interpolate("User: ${unknown.key:-Guest}");
      assertEquals("User: Guest", result);
    }

    @Test
    void shouldNotUseDefaultWhenKeyExists() {
      String result = interpolator.interpolate("User: ${user.name:-Guest}");
      assertEquals("User: John", result);
    }
  }

  @Nested
  class EscapedPlaceholders {

    @Test
    void shouldNotInterpolateEscapedPlaceholder() {
      String result = interpolator.interpolate("Use \\${placeholder} syntax");
      assertEquals("Use ${placeholder} syntax", result);
    }

    @Test
    void shouldHandleMixedEscapedAndUnescapedPlaceholders() {
      String result = interpolator.interpolate("${user.name} uses \\${syntax}");
      assertEquals("John uses ${syntax}", result);
    }
  }

  @Nested
  class NestedPlaceholderResolution {

    @Test
    void shouldResolveNestedPlaceholder() {
      String result = interpolator.interpolate("User: ${nested.ref}");
      assertEquals("User: John", result);
    }

    @Test
    void shouldResolveDeeplyNestedPlaceholders() {
      String result = interpolator.interpolate("${deeply.nested}");
      assertEquals("John is great!", result);
    }

    @Test
    void shouldDetectCircularReference() {
      assertThrows(InterpolationException.class, () -> interpolator.interpolate("${circular.a}"));
    }

    @Test
    void shouldRespectMaxDepth() {
      StringInterpolator limitedInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "a", "${b}",
                      "b", "${c}",
                      "c", "${d}",
                      "d", "final"))
              .maxDepth(2)
              .build();

      assertThrows(InterpolationException.class, () -> limitedInterpolator.interpolate("${a}"));
    }
  }

  @Nested
  class StrictMode {

    @Test
    void shouldThrowInStrictModeWhenKeyNotFound() {
      assertThrows(
          InterpolationException.class, () -> interpolator.interpolate("${unknown.key}", true));
    }

    @Test
    void shouldNotThrowInStrictModeWhenDefaultProvided() {
      String result = interpolator.interpolate("${unknown.key:-default}", true);
      assertEquals("default", result);
    }

    @Test
    void shouldNotThrowInNonStrictModeWhenKeyNotFound() {
      String result = interpolator.interpolate("${unknown.key}", false);
      assertEquals("${unknown.key}", result);
    }
  }

  @Nested
  class ContainsPlaceholders {

    @Test
    void shouldDetectPlaceholders() {
      assertTrue(interpolator.containsPlaceholders("Hello ${name}"));
      assertTrue(interpolator.containsPlaceholders("${a} and ${b}"));
    }

    @Test
    void shouldNotDetectEscapedPlaceholders() {
      assertFalse(interpolator.containsPlaceholders("Hello \\${name}"));
    }

    @Test
    void shouldReturnFalseForNullOrEmpty() {
      assertFalse(interpolator.containsPlaceholders(null));
      assertFalse(interpolator.containsPlaceholders(""));
    }
  }

  @Nested
  class WorkflowContextIntegration {

    @Test
    void shouldResolveFromWorkflowContext() {
      WorkflowContext context = new WorkflowContext();
      context.put("user.name", "Alice");
      context.put("count", 42);

      StringInterpolator ctxInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(new WorkflowContextPropertyResolver(context))
              .build();

      assertEquals("Hello, Alice!", ctxInterpolator.interpolate("Hello, ${user.name}!"));
      assertEquals("Count: 42", ctxInterpolator.interpolate("Count: ${count}"));
    }
  }

  @Nested
  class PropertyResolverChaining {

    @Test
    void shouldResolveInPriorityOrder() {
      Map<String, String> customProps = Map.of("key", "custom");
      System.setProperty("key", "system");

      try {
        StringInterpolator chainedInterpolator =
            DefaultStringInterpolator.builder()
                .addResolver(new MapPropertyResolver(customProps))
                .addResolver(new SystemPropertiesResolver())
                .addResolver(new EnvironmentPropertyResolver())
                .build();

        assertEquals("custom", chainedInterpolator.interpolate("${key}"));
      } finally {
        System.clearProperty("key");
      }
    }
  }

  @Nested
  class DatabaseConfigurationScenarios {

    @Test
    void shouldBuildJdbcConnectionString() {
      StringInterpolator dbInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "db.host", "localhost",
                      "db.port", "5432",
                      "db.name", "myapp",
                      "db.user", "admin",
                      "db.password", "secret123"))
              .build();

      String jdbcUrl =
          dbInterpolator.interpolate(
              "jdbc:postgresql://${db.host}:${db.port}/${db.name}?user=${db.user}&password=${db.password}");

      assertEquals("jdbc:postgresql://localhost:5432/myapp?user=admin&password=secret123", jdbcUrl);
    }

    @Test
    void shouldUseDefaultDatabasePort() {
      StringInterpolator dbInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("db.host", "production-db.example.com"))
              .build();

      String jdbcUrl =
          dbInterpolator.interpolate(
              "jdbc:mysql://${db.host}:${db.port:-3306}/${db.name:-defaultdb}");

      assertEquals("jdbc:mysql://production-db.example.com:3306/defaultdb", jdbcUrl);
    }

    @Test
    void shouldHandleConnectionPoolConfig() {
      StringInterpolator poolInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "pool.min", "5",
                      "pool.max", "20",
                      "pool.timeout", "30000"))
              .build();

      String config =
          poolInterpolator.interpolate(
              "HikariCP: min=${pool.min}, max=${pool.max}, timeout=${pool.timeout}ms, "
                  + "idleTimeout=${pool.idleTimeout:-600000}ms");

      assertEquals("HikariCP: min=5, max=20, timeout=30000ms, idleTimeout=600000ms", config);
    }
  }

  @Nested
  class ApiEndpointConfigurationScenarios {

    @Test
    void shouldBuildRestApiUrl() {
      StringInterpolator apiInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "api.protocol", "https",
                      "api.host", "api.example.com",
                      "api.version", "v2",
                      "api.resource", "users"))
              .build();

      String url =
          apiInterpolator.interpolate(
              "${api.protocol}://${api.host}/${api.version}/${api.resource}");

      assertEquals("https://api.example.com/v2/users", url);
    }

    @Test
    void shouldBuildApiUrlWithQueryParams() {
      StringInterpolator apiInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "base.url", "https://api.github.com",
                      "owner", "apache",
                      "repo", "kafka",
                      "page", "1",
                      "per_page", "30"))
              .build();

      String url =
          apiInterpolator.interpolate(
              "${base.url}/repos/${owner}/${repo}/issues?page=${page}&per_page=${per_page}");

      assertEquals("https://api.github.com/repos/apache/kafka/issues?page=1&per_page=30", url);
    }

    @Test
    void shouldHandleMicroserviceEndpoints() {
      StringInterpolator serviceInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "service.discovery.host", "consul.local",
                      "user.service.name", "user-service",
                      "order.service.name", "order-service",
                      "env", "staging"))
              .build();

      String userServiceUrl =
          serviceInterpolator.interpolate(
              "https://${service.discovery.host}/${env}/${user.service.name}");
      String orderServiceUrl =
          serviceInterpolator.interpolate(
              "https://${service.discovery.host}/${env}/${order.service.name}");

      assertEquals("https://consul.local/staging/user-service", userServiceUrl);
      assertEquals("https://consul.local/staging/order-service", orderServiceUrl);
    }
  }

  @Nested
  class LoggingAndMessagingScenarios {

    @Test
    void shouldFormatLogMessage() {
      WorkflowContext context = new WorkflowContext();
      context.put("user.id", "USR-12345");
      context.put("action", "LOGIN");
      context.put("ip.address", "192.168.1.100");
      context.put("timestamp", "2026-01-13T10:30:00Z");

      StringInterpolator logInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(new WorkflowContextPropertyResolver(context))
              .build();

      String logEntry =
          logInterpolator.interpolate(
              "[${timestamp}] User ${user.id} performed ${action} from ${ip.address}");

      assertEquals(
          "[2026-01-13T10:30:00Z] User USR-12345 performed LOGIN from 192.168.1.100", logEntry);
    }

    @Test
    void shouldFormatEmailTemplate() {
      StringInterpolator emailInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "recipient.name", "John Doe",
                      "order.id", "ORD-98765",
                      "order.total", "$149.99",
                      "delivery.date", "January 15, 2026",
                      "company.name", "TechShop Inc."))
              .build();

      String emailBody =
          emailInterpolator.interpolate(
              """
                    Dear ${recipient.name},

                    Your order #${order.id} for ${order.total} has been shipped!
                    Expected delivery: ${delivery.date}

                    Thank you for shopping with ${company.name}!
                    """);

      assertTrue(emailBody.contains("Dear John Doe"));
      assertTrue(emailBody.contains("order #ORD-98765"));
      assertTrue(emailBody.contains("$149.99"));
      assertTrue(emailBody.contains("January 15, 2026"));
    }

    @Test
    void shouldFormatSlackNotification() {
      WorkflowContext context = new WorkflowContext();
      context.put("build.number", 1234);
      context.put("build.status", "SUCCESS");
      context.put("branch", "main");
      context.put("commit.sha", "abc123def");
      context.put("duration", "5m 32s");

      StringInterpolator slackInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(new WorkflowContextPropertyResolver(context))
              .build();

      String notification =
          slackInterpolator.interpolate(
              """
                    :rocket: Build #${build.number} ${build.status}
                    Branch: `${branch}` | Commit: `${commit.sha}`
                    Duration: ${duration}
                    """);

      assertTrue(notification.contains("Build #1234 SUCCESS"));
      assertTrue(notification.contains("Branch: `main`"));
    }
  }

  @Nested
  class FilePathAndDockerScenarios {

    @Test
    void shouldBuildFilePath() {
      StringInterpolator pathInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "base.dir", "/var/log",
                      "app.name", "myapp",
                      "env", "production",
                      "date", "2026-01-13"))
              .build();

      String logPath =
          pathInterpolator.interpolate("${base.dir}/${app.name}/${env}/${date}/app.log");

      assertEquals("/var/log/myapp/production/2026-01-13/app.log", logPath);
    }

    @Test
    void shouldBuildDockerImageTag() {
      StringInterpolator dockerInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "registry", "gcr.io",
                      "project", "my-project",
                      "image", "web-app",
                      "version", "2.1.0",
                      "build", "456"))
              .build();

      String imageTag =
          dockerInterpolator.interpolate(
              "${registry}/${project}/${image}:${version}-build${build}");

      assertEquals("gcr.io/my-project/web-app:2.1.0-build456", imageTag);
    }

    @Test
    void shouldBuildKubernetesResourceName() {
      StringInterpolator k8sInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "app", "payment-gateway",
                      "env", "prod",
                      "region", "us-east-1"))
              .build();

      String deploymentName = k8sInterpolator.interpolate("${app}-${env}-${region}-deployment");
      String serviceName = k8sInterpolator.interpolate("${app}-${env}-svc");

      assertEquals("payment-gateway-prod-us-east-1-deployment", deploymentName);
      assertEquals("payment-gateway-prod-svc", serviceName);
    }
  }

  // ============================================================================
  // NEGATIVE SCENARIOS - Error Handling
  // ============================================================================

  @Nested
  class NegativeScenarios {

    @Test
    void shouldHandleMalformedPlaceholder() {
      // Missing closing brace - should be treated as literal text
      String result = interpolator.interpolate("Hello ${user.name");
      assertEquals("Hello ${user.name", result);
    }

    @Test
    void shouldHandleEmptyPlaceholder() {
      String result = interpolator.interpolate("Hello ${}!");
      // Empty placeholder should remain as-is in non-strict mode
      assertEquals("Hello ${}!", result);
    }

    @Test
    void shouldHandlePlaceholderWithOnlyWhitespace() {
      // Whitespace-only placeholder trims to empty, which may cause validation errors
      // in some resolvers (like WorkflowContext). Using a minimal interpolator to test.
      StringInterpolator minimalInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("valid", "value"))
              .build();

      // Should remain unchanged since empty key after trim won't resolve
      String result = minimalInterpolator.interpolate("Hello ${   }!", false);
      assertEquals("Hello ${   }!", result);
    }

    @Test
    void shouldThrowForMissingRequiredConfigInStrictMode() {
      StringInterpolator strictInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("existing.key", "value"))
              .strict(true)
              .build();

      InterpolationException exception =
          assertThrows(
              InterpolationException.class,
              () -> strictInterpolator.interpolate("Required: ${missing.required.config}"));

      assertTrue(exception.getMessage().contains("missing.required.config"));
      assertEquals("missing.required.config", exception.getPlaceholder());
    }

    @Test
    void shouldHandleSpecialCharactersInValue() {
      StringInterpolator specialInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "special.chars", "Hello $100 & <tag> \"quoted\"",
                      "regex.pattern", "^[a-z]+$"))
              .build();

      String result1 = specialInterpolator.interpolate("Value: ${special.chars}");
      String result2 = specialInterpolator.interpolate("Pattern: ${regex.pattern}");

      assertEquals("Value: Hello $100 & <tag> \"quoted\"", result1);
      assertEquals("Pattern: ^[a-z]+$", result2);
    }

    @Test
    void shouldHandleDollarSignsInReplacement() {
      StringInterpolator dollarInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("price", "$99.99", "currency", "USD"))
              .build();

      String result = dollarInterpolator.interpolate("Price: ${price} ${currency}");
      assertEquals("Price: $99.99 USD", result);
    }

    @Test
    void shouldHandleBackslashesInValue() {
      StringInterpolator pathInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("windows.path", "C:\\Users\\Admin\\Documents"))
              .build();

      String result = pathInterpolator.interpolate("Path: ${windows.path}");
      assertEquals("Path: C:\\Users\\Admin\\Documents", result);
    }

    @Test
    void shouldPreserveUnresolvedPlaceholdersInNonStrictMode() {
      StringInterpolator nonStrictInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("known", "value"))
              .strict(false)
              .build();

      String result =
          nonStrictInterpolator.interpolate("Known: ${known}, Unknown: ${unknown.placeholder}");

      assertEquals("Known: value, Unknown: ${unknown.placeholder}", result);
    }

    @Test
    void shouldHandleVeryLongPlaceholderNames() {
      String longKey =
          "very.long.property.name.that.goes.on.and.on.and.keeps.going.until.it.reaches.a.ridiculous.length";
      StringInterpolator longKeyInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of(longKey, "found"))
              .build();

      String result = longKeyInterpolator.interpolate("Result: ${" + longKey + "}");
      assertEquals("Result: found", result);
    }

    @Test
    void shouldHandleUnicodeInKeysAndValues() {
      StringInterpolator unicodeInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "greeting.japanese", "こんにちは",
                      "greeting.emoji", "Hello 👋🌍",
                      "name.chinese", "李明"))
              .build();

      String result =
          unicodeInterpolator.interpolate(
              "${greeting.japanese} ${name.chinese}! ${greeting.emoji}");
      assertEquals("こんにちは 李明! Hello 👋🌍", result);
    }
  }

  // ============================================================================
  // EDGE CASES
  // ============================================================================

  @Nested
  class EdgeCases {

    @Test
    void shouldHandleConsecutivePlaceholders() {
      StringInterpolator edgeInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("a", "1", "b", "2", "c", "3"))
              .build();

      String result = edgeInterpolator.interpolate("${a}${b}${c}");
      assertEquals("123", result);
    }

    @Test
    void shouldHandlePlaceholderAtStartAndEnd() {
      String result = interpolator.interpolate("${user.name}");
      assertEquals("John", result);
    }

    @Test
    void shouldHandleMultipleDefaultSeparators() {
      StringInterpolator edgeInterpolator =
          DefaultStringInterpolator.builder().addResolver(MapPropertyResolver.of()).build();

      // Only first :- should be treated as separator
      String result = edgeInterpolator.interpolate("${key:-default:-value}");
      assertEquals("default:-value", result);
    }

    @Test
    void shouldHandleNestedBraces() {
      StringInterpolator edgeInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("json", "{\"key\": \"value\"}"))
              .build();

      String result = edgeInterpolator.interpolate("JSON: ${json}");
      assertEquals("JSON: {\"key\": \"value\"}", result);
    }

    @Test
    void shouldHandleNewlinesInTemplate() {
      StringInterpolator edgeInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("name", "World", "greeting", "Hello"))
              .build();

      String template = "${greeting},\n${name}!\nWelcome.";
      String result = edgeInterpolator.interpolate(template);

      assertEquals("Hello,\nWorld!\nWelcome.", result);
    }

    @Test
    void shouldHandleTabsInTemplate() {
      StringInterpolator edgeInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("col1", "A", "col2", "B", "col3", "C"))
              .build();

      String result = edgeInterpolator.interpolate("${col1}\t${col2}\t${col3}");
      assertEquals("A\tB\tC", result);
    }

    @Test
    void shouldHandleEmptyStringValue() {
      StringInterpolator edgeInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("empty", ""))
              .build();

      String result = edgeInterpolator.interpolate("Value: [${empty}]");
      assertEquals("Value: []", result);
    }

    @Test
    void shouldHandleValueContainingPlaceholderSyntax() {
      StringInterpolator edgeInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "instruction", "Use ${variable} syntax to reference variables"))
              .build();

      // The value contains ${variable} which should be resolved if possible,
      // but since "variable" doesn't exist, it stays as-is in non-strict mode
      String result = edgeInterpolator.interpolate("Tip: ${instruction}");
      assertEquals("Tip: Use ${variable} syntax to reference variables", result);
    }
  }

  // ============================================================================
  // BUILDER CONFIGURATION SCENARIOS
  // ============================================================================

  @Nested
  class BuilderConfigurationScenarios {

    @Test
    void shouldFailWhenNoResolversConfigured() {
      try {
        DefaultStringInterpolator.builder().build();
        fail("Expected IllegalStateException due to no resolvers configured");
      } catch (Exception e) {
        assertTrue(e.getMessage().contains("At least one resolver must be configured"));
      }
    }

    @Test
    void shouldFailWhenMaxDepthIsZero() {
      try {
        DefaultStringInterpolator.builder().maxDepth(0);
        fail("Expected IllegalArgumentException due to zero max depth");
      } catch (Exception e) {
        assertTrue(e.getMessage().contains("maxDepth must be positive"));
      }
    }

    @Test
    void shouldFailWhenMaxDepthIsNegative() {
      try {
        DefaultStringInterpolator.builder().maxDepth(-1);
        fail("Expected IllegalArgumentException due to negative max depth");
      } catch (Exception e) {
        assertTrue(e.getMessage().contains("maxDepth must be positive"));
      }
    }

    @Test
    void shouldAllowCustomMaxDepth() {
      StringInterpolator customDepthInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(
                  MapPropertyResolver.of(
                      "level1", "${level2}",
                      "level2", "${level3}",
                      "level3", "${level4}",
                      "level4", "${level5}",
                      "level5", "deep"))
              .maxDepth(5)
              .build();

      String result = customDepthInterpolator.interpolate("${level1}");
      assertEquals("deep", result);
    }

    @Test
    void shouldReplaceResolversWhenUsingResolverMethod() {
      StringInterpolator interpolator1 =
          DefaultStringInterpolator.builder()
              .addResolver(MapPropertyResolver.of("key", "first"))
              .addResolver(MapPropertyResolver.of("key", "second"))
              .resolver(MapPropertyResolver.of("key", "replaced"))
              .build();

      assertEquals("replaced", interpolator1.interpolate("${key}"));
    }

    @Test
    void shouldAddMultipleResolversAtOnce() {
      java.util.List<PropertyResolver> resolvers =
          java.util.List.of(
              new MapPropertyResolver(Map.of("a", "1")), new MapPropertyResolver(Map.of("b", "2")));

      StringInterpolator multiResolverInterpolator =
          DefaultStringInterpolator.builder().addResolvers(resolvers).build();

      assertEquals("1-2", multiResolverInterpolator.interpolate("${a}-${b}"));
    }
  }

  // ============================================================================
  // WORKFLOW CONTEXT ADVANCED SCENARIOS
  // ============================================================================

  @Nested
  class WorkflowContextAdvancedScenarios {

    @Test
    void shouldResolveNumericValuesFromContext() {
      WorkflowContext context = new WorkflowContext();
      context.put("int.value", 42);
      context.put("double.value", 3.14159);
      context.put("long.value", 9876543210L);

      StringInterpolator ctxInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(new WorkflowContextPropertyResolver(context))
              .build();

      assertEquals("Int: 42", ctxInterpolator.interpolate("Int: ${int.value}"));
      assertEquals("Double: 3.14159", ctxInterpolator.interpolate("Double: ${double.value}"));
      assertEquals("Long: 9876543210", ctxInterpolator.interpolate("Long: ${long.value}"));
    }

    @Test
    void shouldResolveBooleanValuesFromContext() {
      WorkflowContext context = new WorkflowContext();
      context.put("feature.enabled", true);
      context.put("debug.mode", false);

      StringInterpolator ctxInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(new WorkflowContextPropertyResolver(context))
              .build();

      assertEquals("Feature: true", ctxInterpolator.interpolate("Feature: ${feature.enabled}"));
      assertEquals("Debug: false", ctxInterpolator.interpolate("Debug: ${debug.mode}"));
    }

    @Test
    void shouldPrioritizeContextOverSystemProperties() {
      WorkflowContext context = new WorkflowContext();
      context.put("java.version", "custom-version");

      StringInterpolator ctxInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(new WorkflowContextPropertyResolver(context))
              .addResolver(new SystemPropertiesResolver())
              .build();

      // Context has order 100, SystemProperties has order 200
      // Context should win
      assertEquals("custom-version", ctxInterpolator.interpolate("${java.version}"));
    }

    @Test
    void shouldFallbackToSystemPropertiesWhenNotInContext() {
      WorkflowContext context = new WorkflowContext();
      context.put("custom.key", "custom-value");

      StringInterpolator ctxInterpolator =
          DefaultStringInterpolator.builder()
              .addResolver(new WorkflowContextPropertyResolver(context))
              .addResolver(new SystemPropertiesResolver())
              .build();

      // java.home is a system property, not in context
      String javaHome = System.getProperty("java.home");
      assertEquals(javaHome, ctxInterpolator.interpolate("${java.home}"));
      assertEquals("custom-value", ctxInterpolator.interpolate("${custom.key}"));
    }
  }
}
