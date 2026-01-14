# String Interpolation

The Workflow Engine provides a powerful and flexible string interpolation system that allows you to dynamically resolve placeholders in strings from various sources such as workflow contexts, custom properties, system properties, and environment variables.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Placeholder Syntax](#placeholder-syntax)
- [Property Resolution](#property-resolution)
- [Using Interpolation](#using-interpolation)
  - [Static Methods](#static-methods)
  - [With Workflow Context](#with-workflow-context)
  - [With Custom Properties](#with-custom-properties)
  - [Combined Context and Properties](#combined-context-and-properties)
- [Property Resolvers](#property-resolvers)
  - [Built-in Resolvers](#built-in-resolvers)
  - [Custom Resolvers](#custom-resolvers)
  - [Resolution Order](#resolution-order)
- [Advanced Features](#advanced-features)
  - [Strict Mode](#strict-mode)
  - [Nested Interpolation](#nested-interpolation)
  - [Escaped Placeholders](#escaped-placeholders)
  - [Default Values](#default-values)
  - [Nested Property Access](#nested-property-access)
- [Customization](#customization)
  - [Building Custom Interpolators](#building-custom-interpolators)
  - [Configuring Max Depth](#configuring-max-depth)
  - [Custom Resolver Priority](#custom-resolver-priority)
- [Error Handling](#error-handling)
- [Best Practices](#best-practices)
- [Examples](#examples)
- [API Reference](#api-reference)

---

## Overview

String interpolation is a technique for substituting placeholders in strings with actual values at runtime. The Workflow Engine's interpolation system supports:

- **Multiple placeholder formats**: Simple (`${key}`) and with defaults (`${key:-default}`)
- **Nested resolution**: Values can contain placeholders that are recursively resolved
- **Multiple sources**: Properties from context, maps, system properties, environment variables, and custom sources
- **Flexible resolution order**: Configurable priority chain for resolver precedence
- **Type conversion**: Automatic conversion of non-string values to strings
- **Escape sequences**: Support for literal placeholder syntax
- **Strict and lenient modes**: Control how unresolved placeholders are handled

---

## Quick Start

### Basic Usage

```java
import com.workflow.interpolation.Interpolation;

public void example() {
    // Set a system property
    System.setProperty("app.name", "MyApplication");

    // Simple interpolation
    String result = Interpolation.interpolate("Welcome to ${app.name}!");
    // Result: "Welcome to MyApplication!"

    // With default value
    String message = Interpolation.interpolate("Status: ${app.status:-running}");
    // Result: "Status: running" (if app.status is not defined)
}
```

### With Workflow Context

```java
import com.workflow.context.WorkflowContext;
import com.workflow.interpolation.Interpolation;

public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("user", "Alice");
    context.put("action", "login");

    String log = Interpolation.interpolate(
            "User ${user} performed ${action}",
            context
    );
    // Result: "User Alice performed login"
}
```

---

## Placeholder Syntax

The interpolation system supports several placeholder formats:

### Simple Placeholder

```
${key}
```

Resolves the value associated with `key`. If the key is not found and strict mode is disabled, the placeholder is left as-is.

**Example:**
```java
public void example() {
    System.setProperty("version", "2.0.0");
    String result = Interpolation.interpolate("Version: ${version}");
    // Result: "Version: 2.0.0"
}
```

### Placeholder with Default Value

```
${key:-defaultValue}
```

Resolves the value associated with `key`, or uses `defaultValue` if the key is not found.

**Example:**
```java
String result = Interpolation.interpolate("Environment: ${env:-development}");
// Result: "Environment: development" (if env is not defined)
```

### Escaped Placeholder

```
\${literal}
```

Prevents interpolation and produces a literal placeholder in the output.

**Example:**
```java
String result = Interpolation.interpolate("Use \\${syntax} for placeholders");
// Result: "Use ${syntax} for placeholders"
```

---

## Property Resolution

The interpolation system uses a chain of **PropertyResolvers** to resolve placeholder values. Each resolver attempts to find the requested key from its source. If not found, the next resolver in the chain is tried.

### Resolution Flow

1. **Check if placeholder exists** in the input string
2. **Extract the key** from the placeholder syntax
3. **Iterate through resolvers** in priority order (lowest order value first)
4. **First non-empty result wins** - return the value
5. **If no resolver returns a value**:
   - Use the default value if specified in the placeholder
   - In strict mode: throw `InterpolationException`
   - In lenient mode: leave the placeholder as-is

---

## Using Interpolation

### Static Methods

The `Interpolation` utility class provides convenient static methods for common scenarios:

#### Basic Interpolation

```java
// Interpolate using system properties and environment variables
String result = Interpolation.interpolate("${user.home}");
```

#### With Strict Mode

```java
public void example() {
    // Throw exception if placeholder cannot be resolved
    try {
        String result = Interpolation.interpolate("${missing.key}", true);
    } catch (InterpolationException e) {
        // Handle unresolved placeholder
        System.err.println("Missing key: " + e.getPlaceholder());
    }
}
```

#### Check for Placeholders

```java
boolean hasPlaceholders = Interpolation.containsPlaceholders("Hello ${name}");
// Result: true

boolean noPlaceholders = Interpolation.containsPlaceholders("Hello World");
// Result: false
```

### With Workflow Context

Use workflow context values for interpolation:

```java
public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("orderId", "ORD-12345");
    context.put("customerId", "CUST-789");

    String message = Interpolation.interpolate(
            "Processing order ${orderId} for customer ${customerId}",
            context
    );
    // Result: "Processing order ORD-12345 for customer CUST-789"
}
```

**Type Conversion:**
The context can store values of any type, which are automatically converted to strings:

```java
public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("itemCount", 42);
    context.put("price", 19.99);
    context.put("isActive", true);

    String details = Interpolation.interpolate(
            "Items: ${itemCount}, Price: ${price}, Active: ${isActive}",
            context
    );
// Result: "Items: 42, Price: 19.99, Active: true"
}
```

### With Custom Properties

Use a custom map of properties:

```java
Map<String, String> config = Map.of(
    "app.name", "MyApp",
    "app.version", "1.0.0",
    "app.environment", "production"
);

String info = Interpolation.interpolate(
    "${app.name} v${app.version} (${app.environment})",
    config
);
// Result: "MyApp v1.0.0 (production)"
```

**Mutable Maps:**
```java
public void example() {
    Map<String, String> config = new HashMap<>();
    config.put("mode", "debug");

    String msg1 = Interpolation.interpolate("Mode: ${mode}", config);
    // Result: "Mode: debug"

    config.put("mode", "release");
    String msg2 = Interpolation.interpolate("Mode: ${mode}", config);
    // Result: "Mode: release"
}
```

### Combined Context and Properties

Use both workflow context and custom properties, with properties taking precedence:

```java
public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("source", "context");
    context.put("contextOnly", "from-context");

    Map<String, String> properties = Map.of(
            "source", "properties",
            "propsOnly", "from-props"
    );

    StringInterpolator interpolator =
            Interpolation.forContextAndProperties(context, properties);

    // Properties have higher priority (order 50 vs 100)
    interpolator.interpolate("${source}");
    // Result: "properties"

    interpolator.interpolate("${contextOnly}");
    // Result: "from-context"

    interpolator.interpolate("${propsOnly}");
    // Result: "from-props"
}
```

---

## Property Resolvers

Property resolvers are strategies for looking up values by key. The interpolation system includes several built-in resolvers and supports custom implementations.

### Built-in Resolvers

#### 1. MapPropertyResolver

Resolves values from a custom `Map<String, ?>`.

**Default Order:** 50

```java
Map<String, Object> props = Map.of(
    "database.url", "jdbc:postgresql://localhost/mydb",
    "database.username", "admin"
);

MapPropertyResolver resolver = new MapPropertyResolver(props);
```

**Features:**
- Supports nested property access with dot notation (in non-strict mode)
- Can resolve exact key matches
- Configurable strict mode

**Example with Nested Maps:**
```java
public void example() {
    Map<String, Object> config = Map.of(
            "database", Map.of(
                    "host", "localhost",
                    "port", 5432
            )
    );

    // Non-strict mode allows nested access
    MapPropertyResolver resolver = new MapPropertyResolver(config, 50, false);
    resolver.resolve("database.host"); // Returns "localhost"
}
```

#### 2. WorkflowContextPropertyResolver

Resolves values from a `WorkflowContext`.

**Default Order:** 100

```java
public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("userId", "USER-123");

    WorkflowContextPropertyResolver resolver =
            new WorkflowContextPropertyResolver(context);
}
```

**Features:**
- Automatically converts all types to strings
- Supports nested property access (in non-strict mode)
- Integrates seamlessly with workflow execution

#### 3. SystemPropertiesResolver

Resolves values from Java system properties (e.g., those set with `-D` flags or `System.setProperty()`).

**Default Order:** 200

```java
public void example() {
    System.setProperty("app.mode", "production");

    SystemPropertiesResolver resolver = new SystemPropertiesResolver();
    resolver.resolve("app.mode"); // Returns "production"
}
```

**Common System Properties:**
- `java.version` - Java runtime version
- `java.home` - Java installation directory
- `user.home` - User's home directory
- `user.name` - User account name
- `os.name` - Operating system name

#### 4. EnvironmentPropertyResolver

Resolves values from environment variables.

**Default Order:** 300

```java
public void example() {
    EnvironmentPropertyResolver resolver = new EnvironmentPropertyResolver();
    resolver.resolve("HOME"); // Returns the HOME environment variable
}
```

**Features:**
- Key normalization: tries multiple variants
  - Original key: `database.url`
  - With underscores: `database_url`
  - Uppercase: `DATABASE_URL`
- Configurable normalization behavior

**Example:**
```java
// All of these can resolve the same environment variable
String value1 = resolver.resolve("PATH");
String value2 = resolver.resolve("path");
String value3 = resolver.resolve("PATH");
```

#### 5. CompositePropertyResolver

Combines multiple resolvers into a chain, trying each in order until one returns a value.

```java
PropertyResolver composite = CompositePropertyResolver.builder()
    .add(new MapPropertyResolver(customProps))
    .add(new SystemPropertiesResolver())
    .add(new EnvironmentPropertyResolver())
    .build();
```

**Features:**
- Automatically sorts resolvers by priority order
- Stops at the first resolver that returns a non-empty result
- Efficient chaining with short-circuit evaluation

### Custom Resolvers

Implement the `PropertyResolver` interface to create custom resolution strategies:

```java
public class DatabasePropertyResolver implements PropertyResolver {
    
    private final DataSource dataSource;
    
    public DatabasePropertyResolver(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Optional<String> resolve(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT value FROM config WHERE key = ?")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("value"));
                }
            }
        } catch (SQLException e) {
            // Log error
        }
        return Optional.empty();
    }
    
    @Override
    public int order() {
        return 75; // Between MapPropertyResolver (50) and WorkflowContext (100)
    }
}
```

**Use the Custom Resolver:**
```java
StringInterpolator interpolator = DefaultStringInterpolator.builder()
    .addResolver(new DatabasePropertyResolver(dataSource))
    .addResolver(new SystemPropertiesResolver())
    .addResolver(new EnvironmentPropertyResolver())
    .build();

String value = interpolator.interpolate("${db.config.timeout}");
```

### Resolution Order

Resolvers are executed in ascending order of their `order()` value. **Lower values = higher priority.**

**Default Order:**
1. **Custom Map** (order 50) - `MapPropertyResolver`
2. **Workflow Context** (order 100) - `WorkflowContextPropertyResolver`
3. **System Properties** (order 200) - `SystemPropertiesResolver`
4. **Environment Variables** (order 300) - `EnvironmentPropertyResolver`

This means a key in a custom map will override the same key in the workflow context or system properties.

**Example:**
```java
public void example() {
    System.setProperty("env", "system-prop");
    System.setenv().put("ENV", "env-var"); // Conceptual - env vars are read-only

    Map<String, String> props = Map.of("env", "custom-map");

    String result = Interpolation.interpolate("${env}", props);
    // Result: "custom-map" (highest priority)
}
```

---

## Advanced Features

### Strict Mode

Strict mode controls how the interpolator handles unresolved placeholders.

#### Lenient Mode (Default)

Unresolved placeholders without defaults are left as-is:

```java
String result = Interpolation.interpolate("${unknown.key}", false);
// Result: "${unknown.key}"
```

#### Strict Mode

Unresolved placeholders without defaults throw an exception:

```java
public void example() {
    try {
        String result = Interpolation.interpolate("${unknown.key}", true);
    } catch (InterpolationException e) {
        System.err.println("Failed to resolve: " + e.getPlaceholder());
        // Output: "Failed to resolve: unknown.key"
    }
}
```

#### Defaults Override Strict Mode

Even in strict mode, placeholders with defaults won't throw an exception:

```java
String result = Interpolation.interpolate("${unknown.key:-default}", true);
// Result: "default" (no exception thrown)
```

### Nested Interpolation

Resolved values can themselves contain placeholders, which are recursively resolved:

```java
public void example() {
    System.setProperty("base.path", "/usr/local");
    System.setProperty("app.home", "${base.path}/myapp");
    System.setProperty("config.file", "${app.home}/config.yml");

    String result = Interpolation.interpolate("Config: ${config.file}");
    // Result: "Config: /usr/local/myapp/config.yml"
}
```

**Circular Reference Detection:**
```java
public void example() {
    System.setProperty("a", "${b}");
    System.setProperty("b", "${a}");

    try {
        String result = Interpolation.interpolate("${a}");
    } catch (InterpolationException e) {
        System.err.println(e.getMessage());
        // Output: "Circular reference detected for key: a"
    }
}
```

**Max Depth Protection:**
The interpolator has a configurable maximum recursion depth (default: 10) to prevent infinite loops:

```java
StringInterpolator interpolator = DefaultStringInterpolator.builder()
    .addResolver(new SystemPropertiesResolver())
    .maxDepth(5)
    .build();
```

### Escaped Placeholders

Use backslash to escape placeholders and prevent interpolation:

```java
String template = Interpolation.interpolate(
    "To use variables, write \\${variableName}"
);
// Result: "To use variables, write ${variableName}"
```

**Mixed Escaped and Real:**
```java
public void example() {
    System.setProperty("actual", "ACTUAL_VALUE");

    String result = Interpolation.interpolate(
            "Real: ${actual}, Escaped: \\${example}"
    );
    // Result: "Real: ACTUAL_VALUE, Escaped: ${example}"
}
```

### Default Values

Provide fallback values for missing placeholders:

```java
String message = Interpolation.interpolate(
    "Hello ${user.name:-Guest}!"
);
// Result: "Hello Guest!" (if user.name is not defined)
```

**Default with Spaces:**
```java
String result = Interpolation.interpolate(
    "${greeting:-Welcome to our application}"
);
// Result: "Welcome to our application"
```

**Defaults Can Be Empty:**
```java
String result = Interpolation.interpolate("${optional:-}");
// Result: "" (empty string)
```

**Defaults Can Contain Placeholders:**
```java
public void example() {
    System.setProperty("fallback.name", "DefaultApp");

    String result = Interpolation.interpolate(
            "${app.name:-${fallback.name}}"
    );
    // Result: "DefaultApp" (if app.name is not defined)
}
```

### Nested Property Access

When strict mode is disabled, both `MapPropertyResolver` and `WorkflowContextPropertyResolver` support dot notation for nested property access:

```java
public void example() {
    Map<String, Object> config = new HashMap<>();
    config.put("server", Map.of(
            "host", "localhost",
            "port", 8080,
            "ssl", Map.of(
                    "enabled", true,
                    "keystore", "/path/to/keystore"
            )
    ));

    MapPropertyResolver resolver = new MapPropertyResolver(config, 50, false);

    StringInterpolator interpolator = DefaultStringInterpolator.builder()
            .addResolver(resolver)
            .build();

    interpolator.interpolate("${server.host}");
    // Result: "localhost"

    interpolator.interpolate("${server.ssl.enabled}");
    // Result: "true"
}
```

---

## Customization

### Building Custom Interpolators

Use the builder pattern to create highly customized interpolators:

```java
StringInterpolator interpolator = DefaultStringInterpolator.builder()
    .addResolver(new MapPropertyResolver(customConfig))
    .addResolver(new WorkflowContextPropertyResolver(context))
    .addResolver(new SystemPropertiesResolver())
    .addResolver(new EnvironmentPropertyResolver())
    .maxDepth(15)
    .strict(true)
    .build();

String result = interpolator.interpolate("${my.property}");
```

### Configuring Max Depth

Control the maximum nesting level for recursive placeholder resolution:

```java
StringInterpolator interpolator = DefaultStringInterpolator.builder()
    .addResolver(new SystemPropertiesResolver())
    .maxDepth(5) // Maximum 5 levels of nested placeholders
    .build();
```

**Why Max Depth?**
- Prevents infinite loops from circular references
- Protects against deeply nested configurations
- Default value (10) is suitable for most use cases

### Custom Resolver Priority

Change the default priority order by specifying custom order values:

```java
// Custom resolver with high priority (lower order value)
PropertyResolver highPriorityResolver = new PropertyResolver() {
    @Override
    public Optional<String> resolve(String key) {
        // Custom logic
        return Optional.empty();
    }
    
    @Override
    public int order() {
        return 25; // Higher priority than MapPropertyResolver (50)
    }
};

StringInterpolator interpolator = DefaultStringInterpolator.builder()
    .addResolver(highPriorityResolver)
    .addResolver(new MapPropertyResolver(props)) // order 50
    .addResolver(new SystemPropertiesResolver()) // order 200
    .build();
```

---

## Error Handling

### InterpolationException

The main exception type for interpolation errors:

```java
public void example() {
    try {
        String result = Interpolation.interpolate("${missing}", true);
    } catch (InterpolationException e) {
        // Get the placeholder that failed
        String placeholder = e.getPlaceholder();

        // Get the error message
        String message = e.getMessage();

        System.err.println("Failed to resolve '" + e.getPlaceholder() + "': " + e.getMessage());
    }
}
```

### Common Error Scenarios

#### 1. Unresolved Placeholder (Strict Mode)

```java
public void example() {
    try {
        Interpolation.interpolate("${undefined.key}", true);
    } catch (InterpolationException e) {
        // e.getMessage() -> "Unable to resolve placeholder: ${undefined.key}"
        // e.getPlaceholder() -> "undefined.key"
    }
}
```

#### 2. Circular Reference

```java
public void example() {
    System.setProperty("a", "${b}");
    System.setProperty("b", "${c}");
    System.setProperty("c", "${a}");

    try {
        Interpolation.interpolate("${a}");
    } catch (InterpolationException e) {
        // e.getMessage() -> "Circular reference detected for key: a"
    }
}
```

#### 3. Maximum Depth Exceeded

```java
public void example() {
    // Create deeply nested properties
    System.setProperty("level.1", "${level.2}");
    System.setProperty("level.2", "${level.3}");
    // ... many levels ...
    System.setProperty("level.15", "value");

    StringInterpolator interpolator = DefaultStringInterpolator.builder()
            .addResolver(new SystemPropertiesResolver())
            .maxDepth(5)
            .build();

    try {
        interpolator.interpolate("${level.1}");
    } catch (InterpolationException e) {
        // e.getMessage() -> "Maximum interpolation depth (5) exceeded..."
    }
}
```

---

## Best Practices

### 1. Use Default Values for Optional Properties

Always provide defaults for optional configuration:

```java
String timeout = Interpolation.interpolate("${http.timeout:-30000}");
String retries = Interpolation.interpolate("${http.retries:-3}");
```

### 2. Enable Strict Mode for Critical Configuration

Use strict mode when missing configuration should fail fast:

```java
// Application startup - fail if critical config is missing
String dbUrl = Interpolation.interpolate("${database.url}", true);
String apiKey = Interpolation.interpolate("${api.key}", true);
```

### 3. Reuse Interpolators for Better Performance

Create and reuse interpolators instead of recreating them:

```java
// At application startup
private static final StringInterpolator CONFIG_INTERPOLATOR = 
    Interpolation.forProperties(applicationConfig);

// During execution
String value = CONFIG_INTERPOLATOR.interpolate("${some.key}");
```

### 4. Use Appropriate Resolver Priority

Place more specific resolvers before generic ones:

```java
StringInterpolator interpolator = DefaultStringInterpolator.builder()
    .addResolver(new ApplicationConfigResolver())  // Most specific
    .addResolver(new DatabaseConfigResolver())
    .addResolver(new SystemPropertiesResolver())
    .addResolver(new EnvironmentPropertyResolver()) // Most generic
    .build();
```

### 5. Document Expected Placeholders

Clearly document which placeholders are expected:

```java
/**
 * Processes the notification template.
 * <p><
 * Expected placeholders:
 * - ${user.name}: The recipient's name
 * - ${notification.message}: The notification content
 * - ${app.name}: Application name (optional, defaults to "MyApp")
 */
public String processNotificationTemplate(String template, WorkflowContext context) {
    return Interpolation.interpolate(template, context);
}
```

### 6. Validate Input Before Interpolation

Check for placeholders before attempting interpolation:

```java
public void example() {
    if (Interpolation.containsPlaceholders(input)) {
        return Interpolation.interpolate(input, context);
    } else {
        return input; // No interpolation needed
    }
}
```

### 7. Handle Sensitive Data Carefully

Be cautious with interpolation of sensitive values:

```java
public void example() {
    // DON'T log interpolated sensitive data
    String apiKey = Interpolation.interpolate("${api.key}");
    logger.info("API Key: " + apiKey); // Security risk!

    // DO use secure handling
    String apiKey = Interpolation.interpolate("${api.key}");
    // Use the key without logging it
}
```

### 8. Use Type-Safe Context Keys

Define constants for commonly used context keys:

```java
public class ContextKeys {
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";
    public static final String REQUEST_ID = "requestId";
}

// Usage
public void example() {
    context.put(ContextKeys.USER_ID, "USER-123");
    String message = Interpolation.interpolate(
            "User ${userId} initiated request",
            context
    );
}
```

---

## Examples

### Example 1: HTTP Request Configuration

```java
public void example() {
    Map<String, String> config = Map.of(
            "api.base.url", "https://api.example.com",
            "api.version", "v1",
            "api.key", "secret-key-123"
    );

    WorkflowContext context = new WorkflowContext();
    context.put("endpoint", "users");
    context.put("userId", "12345");

    StringInterpolator interpolator =
            Interpolation.forContextAndProperties(context, config);

    // Build the complete URL
    String url = interpolator.interpolate(
            "${api.base.url}/${api.version}/${endpoint}/${userId}"
    );
    // Result: "https://api.example.com/v1/users/12345"

    // Build authorization header
    String authHeader = interpolator.interpolate("Bearer ${api.key}");
    // Result: "Bearer secret-key-123"
}
```

### Example 2: Database Connection String

```java
Map<String, String> dbConfig = Map.of(
    "db.host", "localhost",
    "db.port", "5432",
    "db.name", "myapp_db",
    "db.user", "admin"
);

String connectionUrl = Interpolation.interpolate(
    "jdbc:postgresql://${db.host}:${db.port}/${db.name}?user=${db.user}",
    dbConfig
);
// Result: "jdbc:postgresql://localhost:5432/myapp_db?user=admin"
```

### Example 3: Email Template Processing

```java
public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("user.firstName", "John");
    context.put("user.lastName", "Doe");
    context.put("user.email", "john.doe@example.com");
    context.put("order.id", "ORD-789456");
    context.put("order.total", "$149.99");

    String emailTemplate = """
            Dear ${user.firstName} ${user.lastName},
            
            Your order ${order.id} has been confirmed!
            Total amount: ${order.total}
            
            Thank you for shopping with us.
            
            Best regards,
            ${app.name:-Our Store}
            """;

    String processedEmail = Interpolation.interpolate(emailTemplate, context);
}
```

### Example 4: Dynamic SQL Query

```java
public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("schema", "public");
    context.put("tableName", "users");
    context.put("whereClause", "status = 'active'");
    context.put("limit", "100");

    String query = Interpolation.interpolate(
            "SELECT * FROM ${schema}.${tableName} WHERE ${whereClause} LIMIT ${limit}",
            context
    );
    // Result: "SELECT * FROM public.users WHERE status = 'active' LIMIT 100"
}
```

### Example 5: File Path Construction

```java
public void example() {
    System.setProperty("user.home", "/home/john");
    System.setProperty("app.data.dir", "${user.home}/.myapp/data");

    String logFile = Interpolation.interpolate(
            "${app.data.dir}/logs/application-${date:-latest}.log"
    );
    // Result: "/home/john/.myapp/data/logs/application-latest.log"
}
```

### Example 6: Environment-Specific Configuration

```java
// Set environment variable (typically done outside the app)
// export APP_ENV=production
// export DATABASE_URL=postgres://prod-db:5432/myapp

public void example() {
    String environment = Interpolation.interpolate("${APP_ENV:-development}");

    if ("production".equals(environment)) {
        String dbUrl = Interpolation.interpolate("${DATABASE_URL}", true);
        // Will throw exception if DATABASE_URL is not set in production
    } else {
        String dbUrl = Interpolation.interpolate(
                "${DATABASE_URL:-jdbc:h2:mem:testdb}"
        );
    }
}
```

### Example 7: Multi-Environment Workflow

```java
public void example() {
    Map<String, String> envConfig = new HashMap<>();

    // Load environment-specific configuration
    String env = System.getenv("ENVIRONMENT");
    if ("production".equals(env)) {
        envConfig.put("log.level", "INFO");
        envConfig.put("cache.size", "10000");
        envConfig.put("db.pool.size", "50");
    } else {
        envConfig.put("log.level", "DEBUG");
        envConfig.put("cache.size", "100");
        envConfig.put("db.pool.size", "5");
    }

    Workflow workflow = SequentialWorkflow.builder()
            .name("ConfigurableWorkflow")
            .task(context -> {
                String logLevel = Interpolation.interpolate("${log.level}", envConfig);
                context.put("configuredLogLevel", logLevel);
            })
            .build();
}
```

### Example 8: Custom Resolver for External Service

```java
public class ConsulPropertyResolver implements PropertyResolver {
    
    private final ConsulClient consulClient;
    
    public ConsulPropertyResolver(ConsulClient consulClient) {
        this.consulClient = consulClient;
    }
    
    @Override
    public Optional<String> resolve(String key) {
        try {
            Response<GetValue> response = consulClient.getKVValue(key);
            if (response.getValue() != null) {
                String value = response.getValue().getDecodedValue();
                return Optional.ofNullable(value);
            }
        } catch (Exception e) {
            // Log error
        }
        return Optional.empty();
    }
    
    @Override
    public int order() {
        return 150; // Between WorkflowContext (100) and System (200)
    }
}

// Usage
StringInterpolator interpolator = DefaultStringInterpolator.builder()
    .addResolver(new ConsulPropertyResolver(consulClient))
    .addResolver(new SystemPropertiesResolver())
    .addResolver(new EnvironmentPropertyResolver())
    .build();

String serviceUrl = interpolator.interpolate("${service.user.api.url}");
```

---

## API Reference

### Interpolation Class

Main utility class for string interpolation.

#### Static Methods

| Method                                                                        | Description                                                                       |
|-------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `String interpolate(String input)`                                            | Interpolate using default resolvers (system properties and environment variables) |
| `String interpolate(String input, boolean strict)`                            | Interpolate with strict mode control                                              |
| `String interpolate(String input, WorkflowContext context)`                   | Interpolate using workflow context                                                |
| `String interpolate(String input, WorkflowContext context, boolean strict)`   | Interpolate using workflow context with strict mode                               |
| `String interpolate(String input, Map<String, ?> properties)`                 | Interpolate using custom properties map                                           |
| `String interpolate(String input, Map<String, ?> properties, boolean strict)` | Interpolate using custom properties with strict mode                              |
| `boolean containsPlaceholders(String input)`                                  | Check if string contains any placeholders                                         |
| `StringInterpolator forContext(WorkflowContext context)`                      | Create interpolator for workflow context                                          |
| `StringInterpolator forProperties(Map<String, ?> properties)`                 | Create interpolator for custom properties                                         |
| `StringInterpolator forContextAndProperties(WorkflowContext, Map<String, ?>)` | Create interpolator for both context and properties                               |
| `StringInterpolator defaultInterpolator()`                                    | Get the default interpolator instance                                             |
| `DefaultStringInterpolator.Builder builder()`                                 | Get a builder for custom interpolators                                            |

### StringInterpolator Interface

Core interface for interpolation functionality.

#### Methods

| Method                                             | Description                                |
|----------------------------------------------------|--------------------------------------------|
| `String interpolate(String input)`                 | Interpolate all placeholders in the string |
| `String interpolate(String input, boolean strict)` | Interpolate with strict mode control       |
| `boolean containsPlaceholders(String input)`       | Check if string contains placeholders      |

### DefaultStringInterpolator Class

Default implementation of `StringInterpolator`.

#### Builder Methods

| Method                                                   | Description                               |
|----------------------------------------------------------|-------------------------------------------|
| `Builder addResolver(PropertyResolver resolver)`         | Add a resolver to the chain               |
| `Builder addResolvers(List<PropertyResolver> resolvers)` | Add multiple resolvers                    |
| `Builder resolver(PropertyResolver resolver)`            | Set a single resolver (clears existing)   |
| `Builder maxDepth(int maxDepth)`                         | Set maximum recursion depth (default: 10) |
| `Builder strict(boolean strict)`                         | Set default strict mode (default: false)  |
| `DefaultStringInterpolator build()`                      | Build the interpolator                    |

#### Static Methods

| Method                                     | Description                                             |
|--------------------------------------------|---------------------------------------------------------|
| `Builder builder()`                        | Create a new builder                                    |
| `DefaultStringInterpolator withDefaults()` | Create with system properties and environment variables |

### PropertyResolver Interface

Strategy interface for resolving property values.

#### Methods

| Method                                 | Description                                                  |
|----------------------------------------|--------------------------------------------------------------|
| `Optional<String> resolve(String key)` | Resolve a property value by key                              |
| `int order()`                          | Get the priority order (default: 0, lower = higher priority) |
| `boolean supports(String key)`         | Check if this resolver supports the key (default: true)      |

### Built-in Resolver Classes

| Class                             | Default Order  | Description                         |
|-----------------------------------|----------------|-------------------------------------|
| `MapPropertyResolver`             | 50             | Resolves from custom Map            |
| `WorkflowContextPropertyResolver` | 100            | Resolves from WorkflowContext       |
| `SystemPropertiesResolver`        | 200            | Resolves from system properties     |
| `EnvironmentPropertyResolver`     | 300            | Resolves from environment variables |
| `CompositePropertyResolver`       | N/A            | Chains multiple resolvers           |

### InterpolationException

Exception thrown when interpolation fails.

#### Methods

| Method                    | Description                                              |
|---------------------------|----------------------------------------------------------|
| `String getPlaceholder()` | Get the placeholder that failed to resolve (may be null) |
| `String getMessage()`     | Get the error message                                    |

---

## Integration with Workflows

String interpolation can be seamlessly integrated into workflows:

### In Task Configuration

```java
public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("apiEndpoint", "https://api.example.com/users");
    context.put("apiKey", "secret-123");

    Workflow workflow = SequentialWorkflow.builder()
            .name("API Workflow")
            .task(ctx -> {
                String url = Interpolation.interpolate("${apiEndpoint}", ctx);
                String authHeader = Interpolation.interpolate("Bearer ${apiKey}", ctx);

                // Use in HTTP request
                // ... HTTP client code ...
            })
            .build();
}
```

### In Conditional Workflows

```java
ConditionalWorkflow workflow = ConditionalWorkflow.builder()
    .name("Environment-Based Workflow")
    .condition(ctx -> {
        String env = Interpolation.interpolate(
            "${APP_ENV:-development}", 
            ctx
        );
        return "production".equals(env);
    })
    .onTrue(productionWorkflow)
    .onFalse(developmentWorkflow)
    .build();
```

### In Database Workflows

```java
public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("userId", "12345");

    String query = Interpolation.interpolate(
            "SELECT * FROM users WHERE id = ${userId}",
            context
    );

    // Use with JDBC query task
}
```

---

## Performance Considerations

1. **Reuse Interpolators**: Creating interpolators is relatively expensive. Reuse them when possible.

2. **Check Before Interpolating**: Use `containsPlaceholders()` to avoid unnecessary interpolation work.

3. **Limit Nesting Depth**: Deep nesting requires more recursion. Keep configurations reasonably flat.

4. **Resolver Ordering**: Place faster/more specific resolvers first for better performance.

5. **Caching**: Consider caching interpolated values if the same strings are processed repeatedly.

---

## Thread Safety

- `StringInterpolator` implementations are **thread-safe** and can be shared across threads
- `PropertyResolver` implementations should be thread-safe if used in concurrent contexts
- The default interpolator returned by `Interpolation.defaultInterpolator()` is a singleton and thread-safe

---

## Summary

The Workflow Engine's string interpolation system provides:

✅ **Flexible placeholder syntax** with defaults and escaping  
✅ **Multiple resolution sources** (context, maps, system, environment)  
✅ **Configurable priority chain** for resolver precedence  
✅ **Nested interpolation** with circular reference detection  
✅ **Strict and lenient modes** for error handling  
✅ **Custom resolvers** for extensibility  
✅ **Thread-safe implementations** for concurrent use  
✅ **Integration with workflows** for dynamic configuration  

Use interpolation to make your workflows more flexible, configurable, and environment-aware!
