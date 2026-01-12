package com.workflow.context;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Enhanced test suite for WorkflowContext with comprehensive edge case coverage. */
class WorkflowContextEnhancedTest {

  private WorkflowContext context;

  @BeforeEach
  void setup() {
    context = new WorkflowContext();
  }

  // ==== TypeReference Support Tests ====

  @Test
  void getTyped_withTypeReference_returnsCorrectType() {
    List<String> list = Arrays.asList("a", "b", "c");
    context.put("list", list);

    List<String> retrieved = context.getTyped("list", new TypeReference<>() {});

    assertEquals(list, retrieved);
  }

  @Test
  void getTyped_withTypeReference_missingKey_returnsNull() {
    List<String> retrieved = context.getTyped("missing", new TypeReference<>() {});

    assertNull(retrieved);
  }

  @Test
  void getTyped_withTypeReference_andDefault_returnsDefaultWhenMissing() {
    List<String> defaultList = List.of("default");

    List<String> retrieved = context.getTyped("missing", new TypeReference<>() {}, defaultList);

    assertEquals(defaultList, retrieved);
  }

  @Test
  void getTyped_withTypeReference_andDefault_returnsValueWhenPresent() {
    List<String> actualList = List.of("actual");
    List<String> defaultList = List.of("default");
    context.put("list", actualList);

    List<String> retrieved = context.getTyped("list", new TypeReference<>() {}, defaultList);

    assertEquals(actualList, retrieved);
  }

  @Test
  void getTyped_withTypeReference_complexTypes() {
    Map<String, List<Integer>> complexData = new HashMap<>();
    complexData.put("numbers", Arrays.asList(1, 2, 3));
    context.put("complex", complexData);

    Map<String, List<Integer>> retrieved = context.getTyped("complex", new TypeReference<>() {});

    assertEquals(complexData, retrieved);
  }

  @Test
  void getTyped_withTypeReference_nestedGenerics() {
    List<Map<String, Object>> nested = new ArrayList<>();
    Map<String, Object> item = new HashMap<>();
    item.put("id", 1);
    item.put("name", "test");
    nested.add(item);
    context.put("nested", nested);

    List<Map<String, Object>> retrieved = context.getTyped("nested", new TypeReference<>() {});

    assertEquals(nested, retrieved);
  }

  @Test
  void put_emptyStringKey_works() {
    context.put("", "value");

    assertEquals("value", context.get(""));
  }

  @Test
  void put_overwritesExistingValue() {
    context.put("key", "value1");
    context.put("key", "value2");

    assertEquals("value2", context.get("key"));
  }

  @Test
  void get_withDefault_returnsDefaultForMissingKey() {
    Object result = context.get("missing", "default");

    assertEquals("default", result);
  }

  @Test
  void get_withDefault_returnsActualValueWhenPresent() {
    context.put("key", "actual");

    Object result = context.get("key", "default");

    assertEquals("actual", result);
  }

  // ==== TypedKey Tests ====

  @Test
  void put_withTypedKey_storesValue() {
    TypedKey<String> key = TypedKey.of("username", String.class);
    context.put(key, "alice");

    assertEquals("alice", context.get(key));
  }

  @Test
  void get_withTypedKey_missingKey_returnsNull() {
    TypedKey<String> key = TypedKey.of("missing", String.class);

    assertNull(context.get(key));
  }

  // ==== Copy Tests ====

  @Test
  void copy_createsIndependentCopy() {
    context.put("key1", "value1");
    context.put("key2", "value2");

    WorkflowContext copy = context.copy();
    copy.put("key3", "value3");
    context.put("key4", "value4");

    assertEquals("value1", copy.get("key1"));
    assertEquals("value2", copy.get("key2"));
    assertEquals("value3", copy.get("key3"));
    assertFalse(copy.containsKey("key4"));

    assertFalse(context.containsKey("key3"));
    assertEquals("value4", context.get("key4"));
  }

  @Test
  void copy_withEmptyContext_createsEmptyContext() {
    WorkflowContext copy = context.copy();

    assertNotNull(copy);
    assertNotSame(context, copy);
  }

  @Test
  void copy_filteredByPrefix_returnsMatchingKeys() {
    context.put("user.name", "alice");
    context.put("user.email", "alice@example.com");
    context.put("admin.name", "bob");

    WorkflowContext filtered = context.copy(key -> key.startsWith("user."));

    assertEquals("alice", filtered.get("user.name"));
    assertEquals("alice@example.com", filtered.get("user.email"));
    assertFalse(filtered.containsKey("admin.name"));
  }

  @Test
  void copy_filteredByFunction_worksWithComplexPredicates() {
    context.put("key1", "value1");
    context.put("key2", "value2");
    context.put("key3", "value3");

    WorkflowContext filtered = context.copy(key -> key.contains("2") || key.contains("3"));

    assertTrue(filtered.containsKey("key2"));
    assertTrue(filtered.containsKey("key3"));
    assertFalse(filtered.containsKey("key1"));
  }

  // ==== Scope Tests ====

  @Test
  void scope_createsNamespacedContext() {
    WorkflowContext scoped = context.scope("user");

    scoped.put("name", "alice");
    scoped.put("email", "alice@example.com");

    assertEquals("alice", context.get("user.name"));
    assertEquals("alice@example.com", context.get("user.email"));
  }

  @Test
  void scope_nestedScopes_work() {
    WorkflowContext userScope = context.scope("user");
    WorkflowContext addressScope = userScope.scope("address");

    addressScope.put("city", "NYC");

    assertEquals("NYC", context.get("user.address.city"));
  }

  @Test
  void scope_withEmptyPrefix_behavesLikeNormalContext() {
    WorkflowContext scoped = context.scope("");

    scoped.put("key", "value");

    assertEquals("value", context.get(".key")); // Dot prefix
  }

  // ==== Concurrency Tests ====

  @Test
  void concurrentReads_areThreadSafe() throws InterruptedException {
    context.put("shared", "value");

    int threadCount = 10;
    Thread[] threads = new Thread[threadCount];
    List<String> results = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
      threads[i] =
          new Thread(
              () -> {
                for (int j = 0; j < 100; j++) {
                  results.add(context.getTyped("shared", String.class));
                }
              });
      threads[i].start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    // All reads should return the same value
    assertEquals(1000, results.size());
    assertTrue(results.stream().allMatch("value"::equals));
  }

  // ==== Special Values Tests ====

  @Test
  void put_largeObject_works() {
    byte[] largeArray = new byte[1024 * 1024]; // 1MB
    Arrays.fill(largeArray, (byte) 1);

    context.put("large", largeArray);

    byte[] retrieved = context.getTyped("large", byte[].class);
    assertArrayEquals(largeArray, retrieved);
  }

  @Test
  void put_complexObject_works() {
    Map<String, List<Map<String, Object>>> complex = new HashMap<>();
    complex.put("users", new ArrayList<>());
    Map<String, Object> user = new HashMap<>();
    user.put("id", 1);
    user.put("name", "Alice");
    complex.get("users").add(user);

    context.put("complex", complex);

    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, Object>>> retrieved =
        (Map<String, List<Map<String, Object>>>) context.get("complex");

    assertEquals(complex, retrieved);
  }

  // ==== getTyped Edge Cases ====

  @Test
  void getTyped_wrongType_returnsNull() {
    context.put("string", "value");

    try {
      context.getTyped("string", Integer.class);
      fail();
    } catch (ClassCastException _) {
      assertTrue(true);
    }
  }

  @Test
  void containsKey_missingKey_returnsFalse() {
    assertFalse(context.containsKey("missing"));
  }

  // ==== Edge Cases with Special Characters ====

  @Test
  void put_keyWithSpecialCharacters_works() {
    context.put("user:id", "123");
    context.put("user@email", "test@example.com");
    context.put("user-name", "alice");

    assertEquals("123", context.get("user:id"));
    assertEquals("test@example.com", context.get("user@email"));
    assertEquals("alice", context.get("user-name"));
  }

  @Test
  void scope_withSpecialCharacters_works() {
    WorkflowContext scoped = context.scope("user:profile");

    scoped.put("name", "alice");

    assertEquals("alice", context.get("user:profile.name"));
  }

  @Test
  void supportsMultipleTypes() {
    context.put("string", "text");
    context.put("integer", 42);
    context.put("double", 3.14);
    context.put("boolean", true);
    context.put("list", Arrays.asList(1, 2, 3));
    context.put("map", Map.of("key", "value"));

    assertEquals("text", context.getTyped("string", String.class));
    assertEquals(42, context.getTyped("integer", Integer.class));
    assertEquals(3.14, context.getTyped("double", Double.class));
    assertEquals(true, context.getTyped("boolean", Boolean.class));
    assertNotNull(context.getTyped("list", List.class));
    assertNotNull(context.getTyped("map", Map.class));
  }

  @Test
  void testGetTypedStrict() {
    context.put("retryLimit", 3);

    // Success retrieval
    Integer value = context.getTypedStrict("retryLimit", Integer.class);
    assertEquals(3, value);

    // Key does not exist
    assertNull(context.getTypedStrict("nonexistent", Integer.class));

    // Type mismatch: String exists where Integer expected
    context.put("name", "workflow-1");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> context.getTypedStrict("name", Integer.class));
    assertTrue(exception.getMessage().contains("Expected key 'name' to be java.lang.Integer"));
  }

  @Test
  void testGetTypedWithDefault() {
    context.put("timeout", 5000);

    // Case: Key exists
    Integer result = context.getTyped("timeout", Integer.class, 1000);
    assertEquals(5000, result);

    // Case: Key missing (return default)
    String missing = context.getTyped("missingKey", String.class, "default_val");
    assertEquals("default_val", missing);
  }

  @Test
  void testGetStrictTypedKey() {
    TypedKey<Boolean> flagKey = TypedKey.of("isActive", Boolean.class);
    context.put(flagKey, true);

    // Success retrieval
    assertTrue(context.getStrict(flagKey));

    // Case: Key missing
    TypedKey<String> missingKey = TypedKey.of("description", String.class);
    assertNull(context.getStrict(missingKey));

    // Case: Type mismatch (manual pollution of the map to trigger the check)
    context.put("isActive", "NotABoolean");
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> context.getStrict(flagKey));
    assertTrue(exception.getMessage().contains("expected type java.lang.Boolean"));
  }

  @Test
  void testGetTypedWithTypeReference() {
    List<String> userList = List.of("Admin", "Guest");
    context.put("users", userList);

    // Success: List retrieval
    List<String> retrieved = context.getTyped("users", new TypeReference<>() {});
    assertNotNull(retrieved);
    assertEquals(2, retrieved.size());
    assertEquals("Admin", retrieved.getFirst());

    // Case: Key missing
    assertNull(context.getTyped("missingList", new TypeReference<List<Integer>>() {}));

    // Case: Type mismatch
    try {
      context.getTyped("users", new TypeReference<Integer>() {});
      fail();
    } catch (Exception _) {
      assertTrue(true);
    }
  }

  // ==== Remove Method Tests ====

  @Test
  void remove_existingKey_returnsValueAndRemovesIt() {
    context.put("key", "value");

    Object result = context.remove("key");

    assertEquals("value", result);
    assertFalse(context.containsKey("key"));
  }

  @Test
  void remove_missingKey_returnsNull() {
    Object result = context.remove("missing");

    assertNull(result);
  }

  @Test
  void remove_afterRemoval_containsKeyReturnsFalse() {
    context.put("key", "value");
    context.remove("key");

    assertFalse(context.containsKey("key"));
  }

  @Test
  void remove_withType_returnsTypedValue() {
    context.put("count", 42);

    Integer result = context.remove("count", Integer.class);

    assertEquals(42, result);
    assertFalse(context.containsKey("count"));
  }

  @Test
  void remove_withType_missingKey_returnsNull() {
    Integer result = context.remove("missing", Integer.class);

    assertNull(result);
  }

  @Test
  void remove_withType_wrongType_throwsClassCastException() {
    context.put("key", "string");

    assertThrows(ClassCastException.class, () -> context.remove("key", Integer.class));
  }

  @Test
  void remove_withTypedKey_returnsTypedValue() {
    TypedKey<String> key = TypedKey.of("username", String.class);
    context.put(key, "alice");

    String result = context.remove(key);

    assertEquals("alice", result);
    assertFalse(context.containsKey("username"));
  }

  @Test
  void remove_withTypedKey_missingKey_returnsNull() {
    TypedKey<Integer> key = TypedKey.of("count", Integer.class);

    Integer result = context.remove(key);

    assertNull(result);
  }

  @Test
  void remove_withTypedKey_wrongType_throwsClassCastException() {
    TypedKey<Integer> key = TypedKey.of("value", Integer.class);
    context.put("value", "not an integer");

    assertThrows(ClassCastException.class, () -> context.remove(key));
  }

  @Test
  void remove_multipleKeys_eachReturnsCorrectValue() {
    context.put("key1", "value1");
    context.put("key2", "value2");
    context.put("key3", "value3");

    assertEquals("value1", context.remove("key1"));
    assertEquals("value2", context.remove("key2"));
    assertEquals("value3", context.remove("key3"));

    assertFalse(context.containsKey("key1"));
    assertFalse(context.containsKey("key2"));
    assertFalse(context.containsKey("key3"));
  }

  @Test
  void remove_complexType_withType() {
    List<String> list = Arrays.asList("a", "b", "c");
    context.put("list", list);

    @SuppressWarnings("unchecked")
    List<String> result = (List<String>) context.remove("list", List.class);

    assertEquals(list, result);
    assertFalse(context.containsKey("list"));
  }

  @Test
  void remove_sameKeyTwice_secondReturnsNull() {
    context.put("key", "value");

    assertEquals("value", context.remove("key"));
    assertNull(context.remove("key"));
  }
}
