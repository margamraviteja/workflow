package com.workflow.context;

import com.workflow.listener.WorkflowListeners;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import lombok.Getter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.type.TypeFactory;

/**
 * Thread-safe, key-value store that acts as shared state across workflows and tasks.
 *
 * <p>Provides access to a registry of {@link com.workflow.listener.WorkflowListener} instances.
 * This class holds a {@link WorkflowListeners} instance that can be used by workflow runners and
 * components to register listeners and broadcast lifecycle events (start, success, failure,
 * complete) for the current workflow execution.
 *
 * <p><b>Purpose:</b> Central hub for passing data between workflow stages and tasks. Enables data
 * flow through a workflow pipeline. Each task can read inputs from and write outputs to the
 * context.
 *
 * <p><b>Thread Safety:</b> This context is thread-safe (backed by ConcurrentHashMap). Safe to
 * access from multiple threads simultaneously, including parallel workflows.
 *
 * <p><b>Context Isolation:</b> By default, all child workflows/tasks share the same context
 * instance. Some workflow types (like ParallelWorkflow with shareContext=false) can create isolated
 * copies of context.
 *
 * <p><b>Key Naming Conventions:</b>
 *
 * <ul>
 *   <li>Use hierarchical names for related data: "user.id", "user.name"
 *   <li>Use descriptive names: "processedData", "errorCount"
 *   <li>Avoid collisions by documenting which tasks read/write which keys
 *   <li>Consider using constants for shared keys
 * </ul>
 *
 * <p><b>Value Lifetime:</b> Values remain in context until explicitly overwritten or removed.
 * Earlier tasks can establish state that later tasks depend on.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Pass results from one task to the next in a pipeline
 *   <li>Store configuration or flags for downstream tasks
 *   <li>Accumulate results from parallel tasks
 *   <li>Track metadata (counts, timestamps, errors)
 * </ul>
 *
 * <p><b>Example Usage - Simple Data Flow:</b>
 *
 * <pre>{@code
 * WorkflowContext context = new WorkflowContext();
 *
 * // Task 1: Store a value
 * context.put("userId", 12345);
 *
 * // Task 2: Read and transform
 * Integer userId = context.getTyped("userId", Integer.class);
 * String userName = "user_" + userId;
 * context.put("userName", userName);
 *
 * // Task 3: Use transformed value
 * String finalName = context.getTyped("userName", String.class);
 * System.out.println("User: " + finalName);
 * }</pre>
 *
 * <p><b>Example Usage - In Sequential Workflow:</b>
 *
 * <pre>{@code
 * WorkflowContext context = new WorkflowContext();
 *
 * // Stage 1: Read file
 * context.put("filePath", "data.json");
 * FileReadTask readTask = new FileReadTask(
 *     Path.of("data.json"),
 *     "rawJson"
 * );
 * readTask.execute(context);
 *
 * // Stage 2: Parse JSON (context now has "rawJson")
 * String json = context.getTyped("rawJson", String.class);
 * List<User> users = parseJson(json);
 * context.put("users", users);
 *
 * // Stage 3: Filter users (context now has "users")
 * List<User> filtered = users.stream()
 *     .filter(u -> u.isActive())
 *     .collect(Collectors.toList());
 * context.put("activeUsers", filtered);
 * }</pre>
 *
 * <p><b>Example Usage - Accessing with Defaults:</b>
 *
 * <pre>{@code
 * WorkflowContext context = new WorkflowContext();
 * context.put("retryCount", 3);
 *
 * // Get with default value
 * Integer retries = context.getTyped("retryCount", Integer.class, 5);  // 3
 * Integer timeout = context.getTyped("timeout", Integer.class, 5000);   // Default: 5000
 *
 * // Check existence
 * if (context.containsKey("errorMessage")) {
 *     String error = context.getTyped("errorMessage", String.class);
 *     System.err.println("Error: " + error);
 * }
 * }</pre>
 *
 * <p><b>Example Usage - Parallel Task Coordination:</b>
 *
 * <pre>{@code
 * // Multiple parallel tasks writing to shared context
 * WorkflowContext context = new WorkflowContext();
 * context.put("results", new ConcurrentHashMap<String, Object>());
 *
 * // Task A: Fetch users
 * context.put("userFetchResult", users);
 *
 * // Task B: Fetch orders (parallel with A)
 * context.put("orderFetchResult", orders);
 *
 * // All results available after parallel completion
 * List<User> allUsers = context.getTyped("userFetchResult", List.class);
 * List<Order> allOrders = context.getTyped("orderFetchResult", List.class);
 * }</pre>
 *
 * @see com.workflow.Workflow
 * @see com.workflow.task.Task
 * @see TypedKey
 */
public class WorkflowContext {
  private final Map<String, Object> context;

  /**
   * Registry of listeners associated with this context.
   *
   * <p>The registry is thread-safe and can be used concurrently by multiple threads. It is
   * intentionally final and exposed via {@link #getListeners()} so callers can register listeners
   * at any time during the lifecycle of the context.
   */
  @Getter private final WorkflowListeners listeners;

  /** Default constructor uses a ConcurrentHashMap for thread-safe mutable context. */
  public WorkflowContext() {
    this(new WorkflowListeners());
  }

  /** Default constructor uses a ConcurrentHashMap for thread-safe mutable context. */
  public WorkflowContext(WorkflowListeners listeners) {
    this.context = new ConcurrentHashMap<>();
    this.listeners = listeners;
  }

  /** Store a value in the context under the given key. */
  public <T> void put(String key, T value) {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(value, "value must not be null");
    context.put(key, value);
  }

  /**
   * Retrieve a raw value by key.
   *
   * @param key the context key
   * @return the stored value or {@code null} if absent
   */
  public Object get(String key) {
    return context.get(key);
  }

  /**
   * Retrieve a value or a default if absent.
   *
   * @param key the context key
   * @param defaultValue value returned when key is absent
   * @return the stored value or {@code defaultValue}
   */
  public Object get(String key, Object defaultValue) {
    return context.getOrDefault(key, defaultValue);
  }

  /**
   * Retrieve a typed value by key. Throws {@link ClassCastException} if the stored value is not
   * assignable to {@code type}.
   *
   * @param key the context key
   * @param type the expected value type
   * @param <T> the generic value type
   * @return the typed value or {@code null} if absent
   */
  public <T> T getTyped(String key, Class<T> type) {
    return type.cast(context.get(key));
  }

  /**
   * Retrieve a typed value by key. Throws {@link IllegalStateException} if the stored value is not
   * assignable to {@code type}.
   *
   * @param key the context key
   * @param type the expected value type
   * @param <T> the generic value type
   * @return the typed value or {@code null} if absent
   */
  public <T> T getTypedStrict(String key, Class<T> type) {
    Object value = context.get(key);
    if (value == null) return null;
    if (!type.isInstance(value)) {
      throw new IllegalStateException("Expected key '" + key + "' to be " + type.getName());
    }
    return type.cast(value);
  }

  /**
   * Retrieve typed value or return a default when absent.
   *
   * @param key the context key
   * @param type the expected value type
   * @param defaultValue value returned when key is absent
   * @param <T> the generic value type
   * @return typed value or {@code defaultValue}
   */
  public <T> T getTyped(String key, Class<T> type, T defaultValue) {
    Object value = context.get(key);
    return value == null ? defaultValue : type.cast(value);
  }

  /**
   * Checks if the context contains a value for the given key.
   *
   * @param key the context key
   * @return {@code true} if the key exists, {@code false} otherwise
   */
  public boolean containsKey(String key) {
    return context.containsKey(key);
  }

  /**
   * Remove a value from the context by key.
   *
   * @param key the context key
   * @return the previous value associated with the key, or {@code null} if absent
   */
  public Object remove(String key) {
    return context.remove(key);
  }

  /**
   * Remove a typed value from the context by key.
   *
   * @param key the context key
   * @param type the expected value type
   * @param <T> the generic value type
   * @return the typed value that was removed, or {@code null} if absent
   * @throws ClassCastException if the stored value is not assignable to {@code type}
   */
  public <T> T remove(String key, Class<T> type) {
    Object value = context.remove(key);
    return value == null ? null : type.cast(value);
  }

  /**
   * Store a typed value in the context.
   *
   * @param key the typed key
   * @param value the value to store
   * @param <T> the generic value type
   */
  public <T> void put(TypedKey<T> key, T value) {
    Objects.requireNonNull(key, "TypedKey must not be null");
    context.put(key.name(), value);
  }

  /**
   * Retrieve a typed value by {@link TypedKey}. Throws {@link ClassCastException} if the stored
   * value is not assignable to the expected type.
   *
   * <p>Example:
   *
   * <pre>{@code
   * TypedKey<Integer> COUNT = TypedKey.of("count", Integer.class);
   * TypedKey<Boolean> IS_VALID = TypedKey.of("isValid", Boolean.class);
   *
   * WorkflowContext ctx = new WorkflowContext();
   * ctx.put(COUNT, 10);
   *
   * Integer count = ctx.get(COUNT);
   * Boolean isValid = ctx.get(IS_VALID); // returns null if absent
   * }</pre>
   *
   * @param key the typed key
   * @param <T> the generic value type
   * @return the typed value or {@code null} if absent
   */
  public <T> T get(TypedKey<T> key) {
    Object value = context.get(key.name());
    return value == null ? null : key.type().cast(value);
  }

  /**
   * Retrieve a typed value by {@link TypedKey}. Throws {@link IllegalStateException} if the stored
   * value is not assignable to the expected type.
   *
   * <p>Example:
   *
   * <pre>{@code
   * TypedKey<Integer> COUNT = TypedKey.of("count", Integer.class);
   * TypedKey<Boolean> IS_VALID = TypedKey.of("isValid", Boolean.class);
   *
   * WorkflowContext ctx = new WorkflowContext();
   * ctx.put(COUNT, 10);
   *
   * Integer count = ctx.getStrict(COUNT);
   * Boolean isValid = ctx.getStrict(IS_VALID); // returns null if absent
   * }</pre>
   *
   * @param key the typed key
   * @param <T> the generic value type
   * @return the typed value or {@code null} if absent
   */
  public <T> T getStrict(TypedKey<T> key) {
    Object value = context.get(key.name());
    if (value == null) return null;
    if (!key.type().isInstance(value)) {
      throw new IllegalStateException(
          "Key "
              + key
              + " expected type "
              + key.type().getName()
              + " but found "
              + value.getClass().getName());
    }
    return key.type().cast(value);
  }

  /**
   * Remove a typed value from the context by {@link TypedKey}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * TypedKey<Integer> COUNT = TypedKey.of("count", Integer.class);
   *
   * WorkflowContext ctx = new WorkflowContext();
   * ctx.put(COUNT, 10);
   *
   * Integer removed = ctx.remove(COUNT); // returns 10
   * Integer absent = ctx.remove(COUNT);   // returns null
   * }</pre>
   *
   * @param key the typed key
   * @param <T> the generic value type
   * @return the typed value that was removed, or {@code null} if absent
   */
  public <T> T remove(TypedKey<T> key) {
    Object value = context.remove(key.name());
    return value == null ? null : key.type().cast(value);
  }

  /**
   * Retrieve a typed value using Jackson's {@link TypeReference}. This allows safe retrieval of
   * generic types like List&lt;User&gt; or Map&lt;String, Object&gt;.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * WorkflowContext ctx = new WorkflowContext();
   *
   * // Store a list of users
   * List<User> users = List.of(new User("Alice"), new User("Bob"));
   * ctx.put("users", users);
   *
   * // Retrieve with TypeReference
   * List<User> retrieved = ctx.getTyped("users", new TypeReference<List<User>>() {});
   * System.out.println(retrieved.get(0).getName()); // "Alice"
   * }</pre>
   *
   * @param key the context key
   * @param typeRef the expected value type reference
   * @param <T> the generic value type
   * @return the typed value or {@code null} if absent
   * @throws IllegalStateException if the stored value is not assignable to the expected type
   */
  @SuppressWarnings("unchecked")
  public <T> T getTyped(String key, TypeReference<T> typeRef) {
    Object value = context.get(key);
    if (value == null) return null;

    // Runtime type check using raw class from TypeReference
    Class<?> rawType =
        TypeFactory.createDefaultInstance().constructType(typeRef.getType()).getRawClass();
    if (rawType != null && !rawType.isInstance(value)) {
      throw new IllegalStateException(
          "Expected key '"
              + key
              + "' to be "
              + rawType.getName()
              + " but found "
              + value.getClass().getName());
    }

    return (T) value;
  }

  /**
   * Retrieve a typed value using {@link TypeReference}, with a default fallback.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * WorkflowContext ctx = new WorkflowContext();
   *
   * // Store a list of users
   * List<User> users = List.of(new User("Alice"), new User("Bob"));
   * ctx.put("users", users);
   *
   * // With default fallback
   * List<User> fallback = ctx.getTyped("missingUsers", new TypeReference<List<User>>() {}, List.of());
   * System.out.println(fallback.isEmpty()); // true
   * }</pre>
   *
   * @param key the context key
   * @param typeRef the expected value type reference
   * @param defaultValue value returned when key is absent
   * @param <T> the generic value type
   * @return typed value or {@code defaultValue}
   */
  public <T> T getTyped(String key, TypeReference<T> typeRef, T defaultValue) {
    T value = getTyped(key, typeRef);
    if (value == null) return defaultValue;
    return value;
  }

  /**
   * Context scoping prevents key collisions and make large workflows debuggable and safe for
   * parallelism, add logical scoping via a prefix, without copying data
   *
   * <p>Example:
   *
   * <pre>{@code
   * WorkflowContext userCtx = context.scope("userValidation");
   * userCtx.put("isValid", true);
   * userCtx.put("errors", List.of());
   *
   * // Stored keys: userValidation.isValid, userValidation.errors
   * }</pre>
   *
   * @param namespace the prefix for the key
   * @return {@link ScopedWorkflowContext}
   */
  public WorkflowContext scope(String namespace) {
    return new ScopedWorkflowContext(this, namespace);
  }

  /**
   * Creates a shallow copy of this context. The backing map is copied; stored objects are not
   * deep-copied.
   *
   * @return a new {@link WorkflowContext} instance pre-populated with the current entries
   */
  public WorkflowContext copy() {
    WorkflowContext newContext = new WorkflowContext(this.listeners);
    newContext.context.putAll(this.context);
    return newContext;
  }

  /**
   * Creates a copy of this context based on the predicate; stored objects are not deep-copied.
   *
   * @param filter the {@link Predicate} to filter
   * @return a new {@link WorkflowContext} instance pre-populated with the filtered entries
   */
  public WorkflowContext copy(Predicate<String> filter) {
    WorkflowContext newContext = new WorkflowContext();
    context.entrySet().stream()
        .filter(e -> filter.test(e.getKey()))
        .forEach(e -> newContext.context.put(e.getKey(), e.getValue()));
    return newContext;
  }
}
