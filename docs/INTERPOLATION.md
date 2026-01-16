# Workflow Engine - String Interpolation Guide

## Table of Contents
- [Overview](#overview)
- [Getting Started](#getting-started)
- [StringInterpolator Interface](#stringinterpolator-interface)
- [JakartaElStringInterpolator](#jakartaelstringinterpolator)
- [Placeholder Syntax](#placeholder-syntax)
- [Expression Language Features](#expression-language-features)
- [Creating Interpolators](#creating-interpolators)
- [Variable Sources](#variable-sources)
- [Strict vs Non-Strict Mode](#strict-vs-non-strict-mode)
- [WorkflowContext Integration](#workflowcontext-integration)
- [Advanced Examples](#advanced-examples)
- [Best Practices](#best-practices)
- [Error Handling](#error-handling)
- [API Reference](#api-reference)

## Overview

The Workflow Engine provides powerful string interpolation capabilities through the **StringInterpolator** interface and its Jakarta Expression Language (EL) implementation. This feature enables dynamic string resolution with support for:

- **Variable substitution**: Replace placeholders with runtime values
- **Property access**: Navigate nested objects and maps
- **Arithmetic operations**: Perform calculations within expressions
- **Conditional logic**: Use ternary operators for dynamic content
- **Method calls**: Invoke methods on objects
- **Collection operations**: Access and manipulate lists, arrays, and maps
- **Logical operations**: Combine conditions with boolean operators

String interpolation is particularly useful for:
- Dynamic configuration values
- Template-based content generation
- Conditional message formatting
- Computed values based on workflow context
- Dynamic URL and query parameter construction

## Getting Started

### Quick Example

```java
import com.workflow.interpolation.JakartaElStringInterpolator;
import java.util.Map;

public class QuickStart {
    static void main(String[] args) {
        // Create variables
        Map<String, Object> variables = Map.of(
            "userName", "Alice",
            "age", 30,
            "isAdmin", true
        );
        
        // Create interpolator
        JakartaElStringInterpolator interpolator = 
            JakartaElStringInterpolator.forVariables(variables);
        
        // Interpolate strings
        String greeting = interpolator.interpolate("Hello, ${userName}!");
        // Result: "Hello, Alice!"
        
        String status = interpolator.interpolate(
            "User ${userName} is ${age >= 18 ? 'adult' : 'minor'}"
        );
        // Result: "User Alice is adult"
    }
}
```

## StringInterpolator Interface

The core interface for string interpolation.

### Interface Methods

```java
public interface StringInterpolator {
    /**
     * Interpolate all placeholders using default mode
     */
    String interpolate(String input);
    
    /**
     * Interpolate with explicit strict mode control
     */
    String interpolate(String input, boolean strict);
    
    /**
     * Check if string contains placeholders
     */
    boolean containsPlaceholders(String input);
}
```

### Method Behavior

| Method                         | Description                                        | Returns                      |
|--------------------------------|----------------------------------------------------|------------------------------|
| `interpolate(String)`          | Resolves all placeholders using default strictness | Interpolated string          |
| `interpolate(String, boolean)` | Resolves with explicit strict mode                 | Interpolated string          |
| `containsPlaceholders(String)` | Checks for placeholder presence                    | `true` if placeholders exist |

## JakartaElStringInterpolator

The Jakarta Expression Language implementation of `StringInterpolator` provides full EL 3.0+ support.

### Key Features

- **Full EL Syntax**: Supports complete Jakarta EL specification
- **High Performance**: Compiled expressions for efficiency
- **Type Safety**: Proper type coercion and handling
- **Extensible**: Custom variable sources via WorkflowContext
- **Secure**: Safe evaluation without code execution risks

### Import Statement

```java
import com.workflow.interpolation.JakartaElStringInterpolator;
import com.workflow.interpolation.StringInterpolator;
import com.workflow.interpolation.exception.InterpolationException;
```

## Placeholder Syntax

### Basic Placeholder Format

```
${expression}
```

Where `expression` can be:
- Variable name: `${userName}`
- Property path: `${user.name}`
- Array/list access: `${items[0]}`
- Method call: `${text.toUpperCase()}`
- Arithmetic: `${price * quantity}`
- Comparison: `${age >= 18}`
- Ternary: `${condition ? trueValue : falseValue}`

### Escaped Placeholders

To include literal `${...}` text without interpolation:

```java
String template = "Use \\${expression} syntax for placeholders";
String result = interpolator.interpolate(template);
// Result: "Use ${expression} syntax for placeholders"
```

### Examples

```
// Simple variable
"Hello, ${name}!"                    // "Hello, Alice!"

// Nested property
"Email: ${user.email}"               // "Email: alice@example.com"

// Array access
"First item: ${items[0]}"            // "First item: apple"

// Arithmetic
"Total: ${price * quantity}"         // "Total: 59.97"

// Conditional
"Status: ${age >= 18 ? 'adult' : 'minor'}"  // "Status: adult"

// Escaped
"Use \\${variable} syntax"           // "Use ${variable} syntax"
```

## Expression Language Features

### 1. Property Access

Access object properties and nested values:

```java
Map<String, Object> variables = Map.of(
    "user", Map.of(
        "name", "Bob",
        "address", Map.of(
            "city", "Seattle",
            "zip", "98101"
        )
    )
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

// Direct property
String name = interpolator.interpolate("${user.name}");
// Result: "Bob"

// Nested property
String city = interpolator.interpolate("${user.address.city}");
// Result: "Seattle"
```

### 2. Collection Access

Work with lists, arrays, and maps:

```java
Map<String, Object> variables = Map.of(
    "items", List.of("apple", "banana", "cherry"),
    "numbers", List.of(1, 2, 3, 4, 5)
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

// List index access
String first = interpolator.interpolate("${items[0]}");
// Result: "apple"

// List size
String count = interpolator.interpolate("${items.size()}");
// Result: "3"

// Multiple elements
String display = interpolator.interpolate("${items[0]}, ${items[1]}, ${items[2]}");
// Result: "apple, banana, cherry"
```

### 3. Arithmetic Operations

Perform mathematical calculations:

```java
Map<String, Object> variables = Map.of(
    "price", 19.99,
    "quantity", 3,
    "discount", 0.10
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

// Addition
String result = interpolator.interpolate("Total: ${price + 10}");
// Result: "Total: 29.99"

// Multiplication
String subtotal = interpolator.interpolate("${price * quantity}");
// Result: "59.97"

// Complex calculation
String finalPrice = interpolator.interpolate(
    "Final: ${(price * quantity) * (1 - discount)}"
);
// Result: "Final: 53.973"
```

**Supported Operators:**
- Addition: `+`
- Subtraction: `-`
- Multiplication: `*`
- Division: `/`
- Modulo: `%`

### 4. Comparison Operations

Compare values using relational operators:

```java
Map<String, Object> variables = Map.of(
    "age", 30,
    "score", 85,
    "status", "active"
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

// Greater than or equal
String isAdult = interpolator.interpolate("${age >= 18}");
// Result: "true"

// Equality
String isActive = interpolator.interpolate("${status == 'active'}");
// Result: "true"

// Less than
String needsImprovement = interpolator.interpolate("${score < 90}");
// Result: "true"
```

**Supported Operators:**
- Equal: `==`
- Not equal: `!=`
- Greater than: `>`
- Greater than or equal: `>=`
- Less than: `<`
- Less than or equal: `<=`

### 5. Conditional Expressions

Use ternary operators for conditional logic:

```java
Map<String, Object> variables = Map.of(
    "age", 30,
    "score", 85,
    "membership", "premium"
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

// Simple ternary
String ageCategory = interpolator.interpolate(
    "${age >= 18 ? 'adult' : 'minor'}"
);
// Result: "adult"

// Nested ternary
String category = interpolator.interpolate(
    "${age < 13 ? 'child' : (age < 20 ? 'teen' : 'adult')}"
);
// Result: "adult"

// With string concatenation
String message = interpolator.interpolate(
    "Grade: ${score >= 90 ? 'A' : (score >= 80 ? 'B' : 'C')}"
);
// Result: "Grade: B"
```

### 6. Logical Operations

Combine conditions with boolean operators:

```java
Map<String, Object> variables = Map.of(
    "isActive", true,
    "age", 30,
    "hasPermission", true,
    "items", List.of("a", "b", "c")
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

// AND operation
String canProceed = interpolator.interpolate(
    "${isActive && age > 18}"
);
// Result: "true"

// OR operation
String hasAccess = interpolator.interpolate(
    "${isActive || hasPermission}"
);
// Result: "true"

// NOT operation
String isInactive = interpolator.interpolate("${!isActive}");
// Result: "false"

// Empty check
String hasItems = interpolator.interpolate("${!empty items}");
// Result: "true"
```

**Supported Operators:**
- AND: `&&`
- OR: `||`
- NOT: `!`
- Empty check: `empty`

### 7. String Operations

Manipulate strings within expressions:

```java
Map<String, Object> variables = Map.of(
    "firstName", "John",
    "lastName", "Doe",
    "email", "john.doe@example.com"
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

// String concatenation with +=
String fullName = interpolator.interpolate(
    "${firstName += ' ' += lastName}"
);
// Result: "John Doe"

// Multiple concatenations
String formatted = interpolator.interpolate(
    "Name: ${firstName += ' ' += lastName}, Email: ${email}"
);
// Result: "Name: John Doe, Email: john.doe@example.com"
```

### 8. Method Calls

Invoke methods on objects:

```java
Map<String, Object> variables = Map.of(
    "text", "hello world",
    "items", List.of("apple", "banana", "cherry")
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

// String methods
String upper = interpolator.interpolate("${text.toUpperCase()}");
// Result: "HELLO WORLD"

// Collection methods
String size = interpolator.interpolate("Count: ${items.size()}");
// Result: "Count: 3"
```

### 9. Lambda Expressions (EL 3.0+)

Use lambda expressions for filtering and transforming collections:

```java
public void example() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    JakartaElStringInterpolator interpolator =
            JakartaElStringInterpolator.builder()
                    .variable("numbers", numbers)
                    .build();

    // Check if any number matches condition
    String hasEven = interpolator.interpolate(
            "${numbers.stream().anyMatch(x -> x % 2 == 0)}"
    );
    // Result: "true"

    // Count filtered elements
    List<String> names = List.of("Alice", "Bob", "Charlie", "David");
    interpolator = JakartaElStringInterpolator.builder()
            .variable("names", names)
            .build();

    String longNames = interpolator.interpolate(
            "${names.stream().filter(n -> n.length() > 3).count()}"
    );
    // Result: "3"
}
```

### 10. Optional Handling

Work with Optional values safely:

```java
import java.util.Optional;

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("emptyOpt", Optional.empty())
        .variable("presentOpt", Optional.of("found"))
        .build();

// Check if present
String isEmpty = interpolator.interpolate("${emptyOpt.isEmpty()}");
// Result: "true"

// Use with ternary operator for fallback
String value = interpolator.interpolate(
    "${presentOpt.isPresent() ? presentOpt.get() : 'default'}"
);
// Result: "found"
```

### 11. Array Operations

Access and manipulate arrays:

```java
public void example() {
    // Primitive arrays
    int[] numbers = {10, 20, 30, 40, 50};
    JakartaElStringInterpolator interpolator =
            JakartaElStringInterpolator.builder()
                    .variable("nums", numbers)
                    .build();

    String first = interpolator.interpolate("${nums[0]}");
    // Result: "10"

    String length = interpolator.interpolate("${nums.length}");
    // Result: "5"

    // Multi-dimensional arrays
    int[][] matrix = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    interpolator = JakartaElStringInterpolator.builder()
            .variable("matrix", matrix)
            .build();

    String element = interpolator.interpolate("${matrix[1][1]}");
    // Result: "5"
}
```

### 12. Set Operations

Work with Set collections:

```java
import java.util.Set;
import java.util.LinkedHashSet;

public void example() {
    Set<String> tags = new LinkedHashSet<>(List.of("java", "spring", "docker"));
    JakartaElStringInterpolator interpolator =
            JakartaElStringInterpolator.builder()
                    .variable("tags", tags)
                    .build();

    // Check size
    String count = interpolator.interpolate("${tags.size()}");
    // Result: "3"

    // Check contains
    String hasJava = interpolator.interpolate("${tags.contains('java')}");
    // Result: "true"

    // Check empty
    Set<String> emptySet = new HashSet<>();
    interpolator = JakartaElStringInterpolator.builder()
            .variable("emptySet", emptySet)
            .build();

    String isEmpty = interpolator.interpolate("${empty emptySet}");
   // Result: "true"
}
```

### 13. Advanced Stream Operations

Leverage Java Stream API in expressions:

```java
public void example() {
    List<String> words = List.of("cat", "dog", "elephant", "ant");
    JakartaElStringInterpolator interpolator =
            JakartaElStringInterpolator.builder()
                    .variable("words", words)
                    .build();

    // Filter and count
    String shortWords = interpolator.interpolate(
            "${words.stream().filter(w -> w.length() <= 3).count()}"
    );
    // Result: "3"

    // All match
    List<Integer> evenNumbers = List.of(2, 4, 6, 8);
    interpolator = JakartaElStringInterpolator.builder()
            .variable("nums", evenNumbers)
            .build();

    String allEven = interpolator.interpolate(
            "${nums.stream().allMatch(n -> n % 2 == 0)}"
    );
    // Result: "true"

    // Find first
    List<String> names = List.of("Alice", "Bob", "Charlie");
    interpolator = JakartaElStringInterpolator.builder()
            .variable("names", names)
            .build();

    String firstB = interpolator.interpolate(
            "${names.stream().filter(n -> n.startsWith('B')).findFirst().isPresent() ? " +
                    "names.stream().filter(n -> n.startsWith('B')).findFirst().get() : 'Not found'}"
    );
    // Result: "Bob"
}
```

### 14. Null-Safe Navigation

Handle null values safely without exceptions:

```java
public void example() {
    Map<String, Object> data = new HashMap<>();
    data.put("value", null);

    JakartaElStringInterpolator interpolator =
            JakartaElStringInterpolator.builder()
                    .variable("data", data)
                    .build();

    // Check null before accessing
    String result = interpolator.interpolate(
            "${data.value != null ? data.value : 'N/A'}"
    );
    // Result: "N/A"

    // Chained null checks
    Map<String, Object> outer = new HashMap<>();
    outer.put("inner", null);

    interpolator = JakartaElStringInterpolator.builder()
            .variable("obj", outer)
            .build();

    String safe = interpolator.interpolate(
            "${obj.inner != null ? obj.inner.value : 'default'}"
    );
    // Result: "default"
}
```

### 15. Special Character Handling

Work with special characters and escape sequences:

```java
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("path", "C:\\Users\\Documents")
        .variable("quote", "He said \"Hello\"")
        .variable("multiline", "Line1\nLine2\tTabbed")
        .build();

// Backslashes are preserved
String path = interpolator.interpolate("Path: ${path}");
// Result: "Path: C:\Users\Documents"

// Quotes are handled
String quoted = interpolator.interpolate("${quote}");
// Result: "He said "Hello""

// Newlines and tabs work
String multi = interpolator.interpolate("${multiline}");
// Result: "Line1
//         Line2    Tabbed"
```

## Creating Interpolators

### Factory Methods

```java
public void example() {
    // 1. From variables map
    Map<String, Object> vars = Map.of("key", "value");
    JakartaElStringInterpolator interpolator =
            JakartaElStringInterpolator.forVariables(vars);

    // 2. From WorkflowContext
    WorkflowContext context = new WorkflowContext();
    context.put("key", "value");
    JakartaElStringInterpolator interpolator =
            JakartaElStringInterpolator.forContext(context);

    // 3. From both context and variables
    JakartaElStringInterpolator interpolator =
            JakartaElStringInterpolator.forContextAndVariables(context, vars);
}
```

### Builder Pattern

For more control, use the builder:

```java
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("name", "Alice")
        .variable("age", 30)
        .strict(true)
        .build();
```

### Builder Methods

| Method                             | Description                           | Example                 |
|------------------------------------|---------------------------------------|-------------------------|
| `variable(String, Object)`         | Add single variable                   | `.variable("x", 10)`    |
| `variables(Map)`                   | Set all variables (replaces existing) | `.variables(map)`       |
| `addVariables(Map)`                | Add multiple variables (merges)       | `.addVariables(map)`    |
| `workflowContext(WorkflowContext)` | Set workflow context                  | `.workflowContext(ctx)` |
| `context(WorkflowContext)`         | Alias for workflowContext             | `.context(ctx)`         |
| `strict(boolean)`                  | Set default strict mode               | `.strict(true)`         |
| `build()`                          | Create the interpolator               | `.build()`              |

## Variable Sources

### 1. Simple Variables Map

Direct key-value pairs:

```
Map<String, Object> variables = new HashMap<>();
variables.put("userName", "Alice");
variables.put("age", 30);
variables.put("isActive", true);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);
```

### 2. Nested Objects

Complex object structures:

```java
Map<String, Object> address = Map.of("city", "Seattle", "zip", "98101");
Map<String, Object> user = Map.of("name", "Bob", "address", address);
Map<String, Object> variables = Map.of("user", user);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

String result = interpolator.interpolate("${user.address.city}");
// Result: "Seattle"
```

### 3. Collections

Lists and arrays:

```java
Map<String, Object> variables = Map.of(
    "items", List.of("apple", "banana", "cherry"),
    "scores", new int[]{85, 90, 95}
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);
```

#### Advanced List Operations

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("nums", numbers)
        .build();

// Get sublist
String sublistSize = interpolator.interpolate("${nums.subList(1, 4).size()}");
// Result: "3"

// Check if contains
String hasThree = interpolator.interpolate("${nums.contains(3)}");
// Result: "true"

// Get index of element
String index = interpolator.interpolate("${nums.indexOf(3)}");
// Result: "2"

// Access last element
String last = interpolator.interpolate("${nums.get(nums.size() - 1)}");
// Result: "5"
```

#### Advanced Map Operations

```java
Map<String, Integer> scores = Map.of("alice", 95, "bob", 88, "charlie", 92);
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("scores", scores)
        .build();

// Check if key exists
String hasAlice = interpolator.interpolate("${scores.containsKey('alice')}");
// Result: "true"

// Get keySet size
String keyCount = interpolator.interpolate("${scores.keySet().size()}");
// Result: "3"

// Check specific keys
String aliceScore = interpolator.interpolate(
    "${scores.containsKey('alice') ? scores.alice : 0}"
);
// Result: "95"
```

### 4. POJOs

Plain Java objects:

```java
public class User {
    private String name;
    private int age;
    // getters and setters
}

User user = new User("Alice", 30);
Map<String, Object> variables = Map.of("user", user);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

String result = interpolator.interpolate("${user.name} is ${user.age}");
// Result: "Alice is 30"
```

#### POJOs with Collections

```java
public class Team {
    private String name;
    private List<Person> members;
    // getters and setters
}

List<Person> members = List.of(
    new Person("Alice", 30),
    new Person("Bob", 25)
);
Team team = new Team("Alpha Team", members);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("team", team)
        .build();

String firstMember = interpolator.interpolate("${team.members[0].name}");
// Result: "Alice"

String teamSize = interpolator.interpolate("${team.members.size()}");
// Result: "2"
```

#### Java Records Support

```java
public record Product(String name, double price, int quantity) {}

Product product = new Product("Widget", 29.99, 5);
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("product", product)
        .build();

String total = interpolator.interpolate(
    "Total: $${product.price() * product.quantity()}"
);
// Result: "Total: $149.95"
```

## Strict vs Non-Strict Mode

### Non-Strict Mode (Default)

Unresolved placeholders are left as-is:

```java
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("x", 1)
        .strict(false)  // default
        .build();

String result = interpolator.interpolate("Value: ${unknown}");
// Result: "Value: ${unknown}"
// No exception thrown
```

### Strict Mode

Unresolved placeholders throw exceptions:

```java
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("x", 1)
        .strict(true)
        .build();

public void example() {
    try {
        String result = interpolator.interpolate("Value: ${unknown}");
    } catch (InterpolationException e) {
        // Exception thrown for unresolved placeholder
        System.err.println("Failed to resolve: " + e.getMessage());
    }
}
```

### Per-Call Strict Override

Override default behavior per call:

```java
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("x", 1)
        .strict(false)  // default is non-strict
        .build();

public void example() {
    // Use strict mode for this call only
    try {
        String result = interpolator.interpolate("${unknown}", true);
    } catch (InterpolationException e) {
        // Exception thrown
    }

    // Use non-strict mode for this call
    String result2 = interpolator.interpolate("${unknown}", false);
    // Returns: "${unknown}"
}
```

### When to Use Strict Mode

**Use Strict Mode When:**
- All variables must be present (fail-fast validation)
- Missing variables indicate configuration errors
- You want to catch typos in placeholder names
- Production systems requiring complete data

**Use Non-Strict Mode When:**
- Optional variables are acceptable
- Templates may have partial data
- Graceful degradation is preferred
- Development/testing environments

## WorkflowContext Integration

The interpolator integrates seamlessly with `WorkflowContext`:

### Basic Context Usage

```
import com.workflow.context.WorkflowContext;

WorkflowContext context = new WorkflowContext();
context.put("userId", 12345);
context.put("userName", "Charlie");

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forContext(context);

String result = interpolator.interpolate("User ${userId}: ${userName}");
// Result: "User 12345: Charlie"
```

### Context with Additional Variables

Combine context data with extra variables:

```
WorkflowContext context = new WorkflowContext();
context.put("userId", 12345);

Map<String, Object> additionalVars = Map.of(
    "prefix", "USER",
    "suffix", "ACTIVE"
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forContextAndVariables(
        context, 
        additionalVars
    );

String result = interpolator.interpolate(
    "${prefix}-${userId}-${suffix}"
);
// Result: "USER-12345-ACTIVE"
```

### Nested Objects in Context

```
WorkflowContext context = new WorkflowContext();
context.put("order", Map.of(
    "id", "ORD-001",
    "total", 99.99,
    "items", List.of("Item A", "Item B")
));

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forContext(context);

String result = interpolator.interpolate(
    "Order ${order.id}: $${order.total} (${order.items.size()} items)"
);
// Result: "Order ORD-001: $99.99 (2 items)"
```

### Workflow Integration Example

```java
public class InterpolatingWorkflow implements Workflow {
    private final String messageTemplate;
    
    public InterpolatingWorkflow(String template) {
        this.messageTemplate = template;
    }
    
    @Override
    public WorkflowResult execute(WorkflowContext context) {
        // Create interpolator from context
        JakartaElStringInterpolator interpolator = 
            JakartaElStringInterpolator.forContext(context);
        
        // Interpolate template with context data
        String message = interpolator.interpolate(messageTemplate);
        
        // Store result back in context
        context.put("generatedMessage", message);
        
        return WorkflowResult.success();
    }
    
    @Override
    public String getName() {
        return "InterpolatingWorkflow";
    }
}

// Usage
public void example() {
    WorkflowContext context = new WorkflowContext();
    context.put("userName", "Alice");
    context.put("action", "login");

    Workflow workflow = new InterpolatingWorkflow(
            "User ${userName} performed action: ${action}"
    );

    workflow.execute(context);
    String message = context.get("generatedMessage");
    // Result: "User Alice performed action: login"
}
```

## Advanced Examples

### 1. Dynamic API URL Construction

```java
Map<String, Object> variables = Map.of(
    "apiBaseUrl", "https://api.example.com",
    "version", "v1",
    "userId", 12345
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

String url = interpolator.interpolate(
    "${apiBaseUrl}/${version}/users/${userId}"
);
// Result: "https://api.example.com/v1/users/12345"
```

### 2. Conditional Message Formatting

```java
Map<String, Object> variables = Map.of(
    "userName", "Alice",
    "itemCount", 5,
    "orderTotal", 129.99,
    "isPremium", true
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

String message = interpolator.interpolate(
    "Hello ${userName}, your order of ${itemCount} " +
    "${itemCount == 1 ? 'item' : 'items'} " +
    "totals $${orderTotal}. " +
    "${isPremium ? 'Free shipping applied!' : 'Add $20 for free shipping.'}"
);
// Result: "Hello Alice, your order of 5 items totals $129.99. Free shipping applied!"
```

### 3. Report Generation

```java
Map<String, Object> data = Map.of(
    "reportDate", "2026-01-16",
    "totalSales", 45678.90,
    "orderCount", 234,
    "avgOrderValue", 195.25,
    "topProduct", "Widget Pro"
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(data);

String report = interpolator.interpolate(
    """
    Sales Report - ${reportDate}
    =============================
    Total Sales:     $${totalSales}
    Order Count:     ${orderCount}
    Avg Order Value: $${avgOrderValue}
    Top Product:     ${topProduct}
    
    Performance: ${totalSales > 40000 ? 'Excellent' : 'Good'}
    """
);
```

### 4. Configuration Templates

```java
Map<String, Object> config = Map.of(
    "environment", "production",
    "dbHost", "db.prod.example.com",
    "dbPort", 5432,
    "dbName", "myapp",
    "cacheEnabled", true,
    "cacheHost", "cache.prod.example.com",
    "maxConnections", 100
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(config);

String jdbcUrl = interpolator.interpolate(
    "jdbc:postgresql://${dbHost}:${dbPort}/${dbName}"
);
// Result: "jdbc:postgresql://db.prod.example.com:5432/myapp"

String cacheConfig = interpolator.interpolate(
    "Cache: ${cacheEnabled ? cacheHost : 'disabled'}"
);
// Result: "Cache: cache.prod.example.com"
```

### 5. Email Template

```java
Map<String, Object> emailData = Map.of(
    "recipientName", "John Doe",
    "senderName", "Support Team",
    "ticketNumber", "TKT-12345",
    "issueType", "Technical",
    "priority", "High",
    "estimatedResolution", "24 hours"
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(emailData);

String emailBody = interpolator.interpolate(
    """
    Dear ${recipientName},
    
    Thank you for contacting us. Your ${issueType} support ticket
    (${ticketNumber}) has been created with ${priority} priority.
    
    ${priority == 'High' ? 'Our team is working on this urgently.' :
     'Our team will respond soon.'}
    
    Estimated resolution time: ${estimatedResolution}
    
    Best regards,
    ${senderName}
    """
);
```

### 6. Dynamic Query Builder

```java
Map<String, Object> queryParams = Map.of(
    "table", "users",
    "fields", List.of("id", "name", "email"),
    "where", "status = 'active'",
    "orderBy", "created_at",
    "limit", 100
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(queryParams);

// Note: This is for demonstration. Use parameterized queries in production!
String query = interpolator.interpolate(
    "SELECT ${fields[0]}, ${fields[1]}, ${fields[2]} " +
    "FROM ${table} " +
    "WHERE ${where} " +
    "ORDER BY ${orderBy} " +
    "LIMIT ${limit}"
);
```

### 7. JSON-Like Structure Navigation

Navigate complex nested data structures:

```java
Map<String, Object> response = Map.of(
    "status", 200,
    "data", Map.of(
        "user", Map.of(
            "id", 123,
            "profile", Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "contacts", Map.of(
                    "email", "john@example.com",
                    "phone", "+1234567890"
                )
            ),
            "metadata", Map.of(
                "createdAt", "2026-01-01",
                "status", "active"
            )
        )
    )
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(response);

String email = interpolator.interpolate("${data.user.profile.contacts.email}");
// Result: "john@example.com"

String fullName = interpolator.interpolate(
    "${data.user.profile.firstName} ${data.user.profile.lastName}"
);
// Result: "John Doe"

String isActive = interpolator.interpolate(
    "Status: ${data.user.metadata.status == 'active' ? 'Active User' : 'Inactive'}"
);
// Result: "Status: Active User"
```

### 8. Pagination Information Display

```java
Map<String, Object> pagination = Map.of(
    "currentPage", 3,
    "pageSize", 20,
    "totalItems", 157,
    "totalPages", 8
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(pagination);

String display = interpolator.interpolate(
    "Showing page ${currentPage} of ${totalPages} " +
    "(${(currentPage - 1) * pageSize + 1}-" +
    "${currentPage * pageSize > totalItems ? totalItems : currentPage * pageSize} " +
    "of ${totalItems} items)"
);
// Result: "Showing page 3 of 8 (41-60 of 157 items)"
```

### 9. Shopping Cart Calculation

```java
Map<String, Object> cart = Map.of(
    "items", List.of(
        Map.of("name", "Widget", "price", 29.99, "qty", 2),
        Map.of("name", "Gadget", "price", 49.99, "qty", 1),
        Map.of("name", "Gizmo", "price", 19.99, "qty", 3)
    ),
    "itemCount", 3,
    "subtotal", 169.94,
    "tax", 13.60,
    "total", 183.54
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(cart);

String summary = interpolator.interpolate(
    "Cart: ${itemCount} item(s), Subtotal: $${subtotal}, " +
    "Tax: $${tax}, Total: $${total}"
);
// Result: "Cart: 3 item(s), Subtotal: $169.94, Tax: $13.6, Total: $183.54"

// Individual item details
String firstItem = interpolator.interpolate(
    "${items[0].name}: ${items[0].qty} x $${items[0].price}"
);
// Result: "Widget: 2 x $29.99"
```

### 10. Validation Error Messages

```java
Map<String, Object> error = Map.of(
    "field", "email",
    "value", "invalid-email",
    "constraint", "must be a valid email address",
    "code", "INVALID_FORMAT"
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(error);

String message = interpolator.interpolate(
    "Validation failed: '${field}' ${constraint}. " +
    "Provided value: '${value}' [${code}]"
);
// Result: "Validation failed: 'email' must be a valid email address. 
//          Provided value: 'invalid-email' [INVALID_FORMAT]"
```

### 11. Time-Based Greeting

```java
import java.time.LocalTime;

Map<String, Object> data = Map.of(
    "userName", "Alice",
    "hour", LocalTime.now().getHour()
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(data);

String greeting = interpolator.interpolate(
    "${hour < 12 ? 'Good morning' : (hour < 18 ? 'Good afternoon' : 'Good evening')}, " +
    "${userName}!"
);
// Result depends on time: "Good morning, Alice!" or "Good afternoon, Alice!" etc.
```

### 12. Feature Flag Configuration

```java
Map<String, Object> features = Map.of(
    "darkMode", true,
    "betaFeatures", false,
    "maxUploadSize", 50,
    "allowedFileTypes", List.of("pdf", "doc", "xlsx")
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(features);

String config = interpolator.interpolate(
    "Dark mode: ${darkMode ? 'enabled' : 'disabled'}, " +
    "Beta: ${betaFeatures ? 'enabled' : 'disabled'}, " +
    "Max upload: ${maxUploadSize}MB, " +
    "Allowed types: ${allowedFileTypes[0]}, ${allowedFileTypes[1]}, ${allowedFileTypes[2]}"
);
// Result: "Dark mode: enabled, Beta: disabled, Max upload: 50MB, 
//          Allowed types: pdf, doc, xlsx"
```

## Best Practices

### 1. Variable Naming

Use clear, descriptive variable names:

```java
// Good
Map<String, Object> variables = Map.of(
    "userName", "Alice",
    "orderTotal", 99.99,
    "isActive", true
);

// Avoid
Map<String, Object> variables = Map.of(
    "u", "Alice",      // unclear
    "t", 99.99,        // unclear
    "a", true          // unclear
);
```

### 2. Reuse Interpolators

Create once, use many times:

```java
// Good - reuse interpolator
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

String message1 = interpolator.interpolate(template1);
String message2 = interpolator.interpolate(template2);
String message3 = interpolator.interpolate(template3);

// Avoid - creating new interpolator each time
String message1 = JakartaElStringInterpolator
    .forVariables(variables)
    .interpolate(template1);
```

### 3. Check for Placeholders

Before interpolating, check if needed:

```
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

String template = getTemplate();

if (interpolator.containsPlaceholders(template)) {
    String result = interpolator.interpolate(template);
    // use result
} else {
    // use template directly, no interpolation needed
}
```

### 4. Handle Null Safely

Use null checks in expressions:

```
Map<String, Object> data = new HashMap<>();
data.put("value", null);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(data);

// Good - null-safe
String result = interpolator.interpolate(
    "${value != null ? value : 'default'}"
);

// Risky - may cause issues if value is null and you try to access properties
// String result = interpolator.interpolate("${value.someProperty}");
```

### 5. Avoid Reserved Words as Variable Names

EL has reserved keywords that should not be used as variable names:

```java
// Avoid using these as variable names:
// - empty
// - null
// - true
// - false
// - not
// - and
// - or
// - div
// - mod

// Bad - 'empty' is a reserved operator
Map<String, Object> bad = Map.of("empty", "");

// Good - use descriptive names
Map<String, Object> good = Map.of("emptyString", "");
```

### 6. Use Builder for Complex Configurations

When combining multiple sources:

```
// Good - clear and flexible
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .workflowContext(context)
        .variable("appVersion", "1.0.0")
        .variable("environment", "prod")
        .addVariables(configMap)
        .strict(true)
        .build();

// Less clear - harder to maintain
Map<String, Object> allVars = new HashMap<>(context.asMap());
allVars.put("appVersion", "1.0.0");
allVars.put("environment", "prod");
allVars.putAll(configMap);
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(allVars);
```

### 7. Prefer Simple Expressions

Keep expressions readable:

```java
// Good - simple and clear
String result = interpolator.interpolate(
    "User: ${user.name}, Age: ${user.age}"
);

// Acceptable - moderate complexity
String status = interpolator.interpolate(
    "Status: ${user.age >= 18 ? 'adult' : 'minor'}"
);

// Avoid - too complex, hard to maintain
String complex = interpolator.interpolate(
    "${user.age < 13 ? 'child' : (user.age < 20 ? 'teen' : " +
    "(user.age < 65 ? 'adult' : 'senior'))} - " +
    "${user.isActive && user.hasPermission && !user.isSuspended ? 'active' : 'inactive'}"
);

// Better - break into multiple interpolations
String ageCategory = interpolator.interpolate(
    "${user.age < 13 ? 'child' : (user.age < 20 ? 'teen' : 'adult')}"
);
String accountStatus = interpolator.interpolate(
    "${user.isActive && user.hasPermission ? 'active' : 'inactive'}"
);
String combined = ageCategory + " - " + accountStatus;
```

### 8. Handle Special Characters Properly

Be aware of escape sequences:

```java
// Paths with backslashes
Map<String, Object> vars = Map.of(
    "path", "C:\\Users\\Documents"  // Java escaping
);
String result = interpolator.interpolate("Path: ${path}");
// Result: "Path: C:\Users\Documents"

// Quotes in strings
Map<String, Object> vars2 = Map.of(
    "quote", "He said \"Hello\""  // Java escaping
);
String result2 = interpolator.interpolate("${quote}");
// Result: "He said "Hello""
```

### 9. Use Appropriate Collections

Choose the right collection type:

```java
// Use List for ordered, indexed access
List<String> items = List.of("first", "second", "third");
// ${items[0]}, ${items[1]}

// Use Set for uniqueness checks
Set<String> tags = Set.of("java", "spring", "docker");
// ${tags.contains('java')}

// Use Map for key-value lookups
Map<String, Object> config = Map.of("host", "localhost", "port", 8080);
// ${config.host}, ${config['port']}
```

### 10. Leverage Stream API Wisely

Use streams for complex filtering, but keep it readable:

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

// Good - clear intent
String hasEven = interpolator.interpolate(
    "${numbers.stream().anyMatch(n -> n % 2 == 0)}"
);

// Good - simple filter and count
String shortCount = interpolator.interpolate(
    "${words.stream().filter(w -> w.length() < 5).count()}"
);

// Avoid - too complex for inline expression
// Consider pre-processing data instead
```

### 11. Performance Considerations

Optimize for repeated use:

```
// Good - create interpolator once, reuse
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(sharedVariables);

for (String template : templates) {
    String result = interpolator.interpolate(template);
    process(result);
}

// Avoid - creating new interpolator in loop
for (String template : templates) {
    JakartaElStringInterpolator interp = 
        JakartaElStringInterpolator.forVariables(sharedVariables);
    String result = interp.interpolate(template);
    process(result);
}
```

### 12. Test Edge Cases

Ensure your templates handle edge cases:

```
// Test with empty collections
Map<String, Object> test1 = Map.of("items", List.of());
// Should handle: ${empty items}, ${items.size()}

// Test with null values
Map<String, Object> test2 = new HashMap<>();
test2.put("value", null);
// Should handle: ${value != null ? value : 'default'}

// Test with special characters
Map<String, Object> test3 = Map.of(
    "path", "C:\\Users\\Docs",
    "quote", "She said \"Hi\""
);
// Should preserve special characters correctly
```

The interpolator handles null input gracefully:

```
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

String result = interpolator.interpolate(null);
// Returns: null (no exception)

result = interpolator.interpolate("");
// Returns: "" (empty string)

result = interpolator.interpolate("  ");
// Returns: "  " (whitespace preserved)
```

### 5. Use Strict Mode for Validation

Validate templates during initialization:

```
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

// Validate template at startup
try {
    interpolator.interpolate(template, true);  // strict mode
    // Template is valid
} catch (InterpolationException e) {
    // Template has invalid placeholders
    throw new IllegalStateException("Invalid template", e);
}
```

### 6. Separate Configuration from Templates

```java
// Good - separate concerns
public class EmailService {
    private final JakartaElStringInterpolator interpolator;
    private final String welcomeEmailTemplate;
    
    public EmailService(Map<String, Object> config, String template) {
        this.interpolator = JakartaElStringInterpolator.forVariables(config);
        this.welcomeEmailTemplate = template;
    }
    
    public String generateWelcomeEmail(Map<String, Object> userData) {
        // Merge config with user data
        JakartaElStringInterpolator emailInterpolator = 
            JakartaElStringInterpolator.builder()
                .addVariables(interpolator.getVariables())
                .addVariables(userData)
                .build();
        
        return emailInterpolator.interpolate(welcomeEmailTemplate);
    }
}
```

### 7. Validate Complex Expressions

Test complex expressions separately:

```
// Test complex expression separately
String complexExpression = "${(price * quantity) * (1 - discount)}";

Map<String, Object> testVars = Map.of(
    "price", 10.0,
    "quantity", 2,
    "discount", 0.1
);

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(testVars);

String result = interpolator.interpolate(complexExpression);
assertEquals("18.0", result);  // Verify calculation
```

### 8. Document Template Variables

Document expected variables in templates:

```java
/**
 * Email template for order confirmation.
 * 
 * <pre>
 * Required variables:
 * - customerName (String): Customer's full name
 * - orderNumber (String): Order identifier
 * - orderTotal (Number): Total order amount
 * - itemCount (Number): Number of items in order
 * - estimatedDelivery (String): Delivery date
 * </pre>
 */
String orderConfirmationTemplate = """
    Dear ${customerName},
    
    Your order ${orderNumber} has been confirmed!
    
    Order Summary:
    - Items: ${itemCount}
    - Total: $${orderTotal}
    - Estimated Delivery: ${estimatedDelivery}
    """;
```

## Error Handling

### InterpolationException

All interpolation errors throw `InterpolationException`:

```java
import com.workflow.interpolation.exception.InterpolationException;

public void example() {
    try {
        JakartaElStringInterpolator interpolator =
                JakartaElStringInterpolator.builder()
                        .variable("x", 1)
                        .strict(true)
                        .build();

        String result = interpolator.interpolate("${unknown}");

    } catch (InterpolationException e) {
        // Handle interpolation error
        System.err.println("Interpolation failed: " + e.getMessage());

        // Get the problematic placeholder (if available)
        String placeholder = e.getPlaceholder();
        if (placeholder != null) {
            System.err.println("Failed placeholder: " + placeholder);
        }

        // Get underlying cause (if any)
        Throwable cause = e.getCause();
        if (cause != null) {
            System.err.println("Root cause: " + cause.getMessage());
        }
    }
}
```

### Common Error Scenarios

#### 1. Unresolved Variable (Strict Mode)

```java
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .variable("x", 1)
        .strict(true)
        .build();

public void example() {
    try {
        interpolator.interpolate("${unknownVariable}");
    } catch (InterpolationException e) {
        // Error: Unable to resolve EL expression
    }
}
```

#### 2. Invalid Property Access

```java
Map<String, Object> variables = Map.of("user", Map.of("name", "Alice"));

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

public void example() {
    try {
        interpolator.interpolate("${user.nonExistentProperty}", true);
    } catch (InterpolationException e) {
        // Error: Property not found
    }
}
```

#### 3. Type Errors

```java
Map<String, Object> variables = Map.of("text", "hello");

JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

public void example() {
    try {
        // Attempting to call method that doesn't exist
        interpolator.interpolate("${text.invalidMethod()}", true);
    } catch (InterpolationException e) {
        // Error: Method not found
    }
}
```

### Error Handling Strategies

#### 1. Fail-Fast (Strict)

```java
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .addVariables(variables)
        .strict(true)
        .build();

public void example() {
    try {
        String result = interpolator.interpolate(template);
        processResult(result);
    } catch (InterpolationException e) {
        logger.error("Template interpolation failed", e);
        throw new BusinessException("Invalid template", e);
    }
}
```

#### 2. Graceful Degradation (Non-Strict)

```java
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.builder()
        .addVariables(variables)
        .strict(false)  // default
        .build();

String result = interpolator.interpolate(template);
// Unresolved placeholders remain as ${...}
// Application continues normally
```

#### 3. Fallback Values

```java
JakartaElStringInterpolator interpolator = 
    JakartaElStringInterpolator.forVariables(variables);

String result = interpolator.interpolate(template, false);

public void example() {
    // Check if interpolation was complete
    if (interpolator.containsPlaceholders(result)) {
        logger.warn("Template has unresolved placeholders: {}", result);
        // Use default message
        result = "Default message";
    }
}
```

## API Reference

### JakartaElStringInterpolator

#### Static Factory Methods

```java
// Create from variables map
static JakartaElStringInterpolator forVariables(Map<String, Object> variables);

// Create from WorkflowContext
static JakartaElStringInterpolator forContext(WorkflowContext context);

// Create from context and additional variables
static JakartaElStringInterpolator forContextAndVariables(
    WorkflowContext context, 
    Map<String, Object> additionalVariables
);

// Create builder
static Builder builder();
```

#### Instance Methods

```java
// Interpolate with default strict mode
String interpolate(String input);

// Interpolate with explicit strict mode
String interpolate(String input, boolean strict);

// Check for placeholders
boolean containsPlaceholders(String input);

// Get default strict mode setting
boolean isStrictByDefault();
```

#### Builder Methods

```java
class Builder {
    // Set all variables (replaces existing)
    Builder variables(Map<String, Object> variables);
    
    // Add single variable
    Builder variable(String name, Object value);
    
    // Add multiple variables (merges with existing)
    Builder addVariables(Map<String, Object> variables);
    
    // Set workflow context
    Builder workflowContext(WorkflowContext context);
    
    // Alias for workflowContext
    Builder context(WorkflowContext context);
    
    // Set default strict mode
    Builder strict(boolean strict);
    
    // Build the interpolator
    JakartaElStringInterpolator build();
}
```

### InterpolationException

#### Constructors

```java
InterpolationException(String message);
InterpolationException(String message, String placeholder);
InterpolationException(String message, Throwable cause);
InterpolationException(String message, String placeholder, Throwable cause);
```

#### Methods

```java
// Get the problematic placeholder (maybe null)
String getPlaceholder();

// Standard exception methods
String getMessage();
Throwable getCause();
```

## Summary

The Workflow Engine's string interpolation feature provides:

✅ **Powerful Expression Language**: Full Jakarta EL 3.0+ support with lambda expressions  
✅ **Flexible Variable Sources**: Maps, contexts, POJOs, records, collections, arrays  
✅ **Multiple Expression Types**: Arithmetic, logical, conditional, string operations  
✅ **Advanced Collection Support**: Lists, Sets, Maps, Arrays (including multi-dimensional)  
✅ **Stream API Integration**: Filter, map, reduce operations with lambda syntax  
✅ **Optional Handling**: Safe navigation with Optional values  
✅ **Null-Safe Operations**: Ternary operators for safe null handling  
✅ **Special Character Support**: Proper escaping for paths, quotes, newlines  
✅ **Strict and Non-Strict Modes**: Configurable error handling  
✅ **WorkflowContext Integration**: Seamless workflow integration  
✅ **Performance**: Compiled expressions for efficiency  
✅ **Type Safety**: Proper type handling and coercion  
✅ **Easy to Use**: Simple API with builder pattern  
✅ **Comprehensive Testing**: 240+ test cases covering edge cases and real-world scenarios  

### Supported Data Types

- **Primitives**: int, long, double, boolean, etc.
- **Strings**: With full manipulation methods
- **Collections**: List, Set, Map with full API access
- **Arrays**: Primitive and object arrays, multidimensional support
- **POJOs**: Plain Java objects with getter methods
- **Records**: Java 14+ record types
- **Optional**: Java Optional with safe unwrapping
- **Nested Structures**: Unlimited nesting depth for objects and maps
