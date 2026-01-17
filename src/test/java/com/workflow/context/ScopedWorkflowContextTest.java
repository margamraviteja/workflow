package com.workflow.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

class ScopedWorkflowContextTest {

  private static final String NAMESPACE = "userModule";

  private WorkflowContext parentContext;

  @BeforeEach
  void setUp() {
    parentContext = new WorkflowContext();
  }

  @Test
  void shouldScopeKeysWithPrefix() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");

    scoped.put("key", "value");

    assertEquals("value", scoped.get("key"));
    assertEquals("value", parentContext.get("task1.key"));
  }

  @Test
  void shouldAddDotToPrefix() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");

    scoped.put("key", "value");

    assertTrue(parentContext.containsKey("task1.key"));
  }

  @Test
  void shouldNotAddExtraDotIfPrefixEndsWithDot() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1.");

    scoped.put("key", "value");

    assertTrue(parentContext.containsKey("task1.key"));
    assertFalse(parentContext.containsKey("task1..key"));
  }

  @Test
  void shouldHandleNestedScopes() {
    ScopedWorkflowContext scoped1 = new ScopedWorkflowContext(parentContext, "parent");
    ScopedWorkflowContext scoped2 = new ScopedWorkflowContext(scoped1, "child");

    scoped2.put("key", "value");

    assertEquals("value", scoped2.get("key"));
    assertEquals("value", scoped1.get("child.key"));
    assertEquals("value", parentContext.get("parent.child.key"));
  }

  @Test
  void shouldHandleGetForNonexistentKey() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");

    assertNull(scoped.get("nonexistent"));
  }

  @Test
  void shouldHandleContainsKeyForNonexistentKey() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");

    assertFalse(scoped.containsKey("nonexistent"));
    assertFalse(parentContext.containsKey("task1.nonexistent"));
  }

  @Test
  void shouldHandleContainsKeyForExistingKey() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");

    scoped.put("key", "value");

    assertTrue(scoped.containsKey("key"));
    assertTrue(parentContext.containsKey("task1.key"));
  }

  @Test
  void shouldHandleComplexTypes() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");

    scoped.put("list", java.util.Arrays.asList(1, 2, 3));
    scoped.put("map", java.util.Map.of("a", 1, "b", 2));

    assertEquals(java.util.Arrays.asList(1, 2, 3), scoped.get("list"));
    assertEquals(java.util.Map.of("a", 1, "b", 2), scoped.get("map"));
  }

  @Test
  void shouldIsolateScopes() {
    ScopedWorkflowContext scoped1 = new ScopedWorkflowContext(parentContext, "task1");
    ScopedWorkflowContext scoped2 = new ScopedWorkflowContext(parentContext, "task2");

    scoped1.put("key", "value1");
    scoped2.put("key", "value2");

    assertEquals("value1", scoped1.get("key"));
    assertEquals("value2", scoped2.get("key"));
    assertEquals("value1", parentContext.get("task1.key"));
    assertEquals("value2", parentContext.get("task2.key"));
  }

  @Test
  void shouldHandleEmptyPrefix() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "");

    scoped.put("key", "value");

    assertEquals("value", scoped.get("key"));
    assertEquals("value", parentContext.get(".key"));
  }

  @Test
  void shouldHandleSpecialCharactersInPrefix() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task-1_2");

    scoped.put("key", "value");

    assertEquals("value", scoped.get("key"));
    assertEquals("value", parentContext.get("task-1_2.key"));
  }

  @Test
  void shouldHandleSpecialCharactersInKey() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");

    scoped.put("key-with_special.chars", "value");

    assertEquals("value", scoped.get("key-with_special.chars"));
    assertEquals("value", parentContext.get("task1.key-with_special.chars"));
  }

  @Test
  void shouldOverwriteExistingValue() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");

    scoped.put("key", "value1");
    scoped.put("key", "value2");

    assertEquals("value2", scoped.get("key"));
    assertEquals("value2", parentContext.get("task1.key"));
  }

  @Test
  void shouldHandleMultipleKeysInSameScope() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");

    scoped.put("key1", "value1");
    scoped.put("key2", "value2");
    scoped.put("key3", "value3");

    assertEquals("value1", scoped.get("key1"));
    assertEquals("value2", scoped.get("key2"));
    assertEquals("value3", scoped.get("key3"));

    assertTrue(parentContext.containsKey("task1.key1"));
    assertTrue(parentContext.containsKey("task1.key2"));
    assertTrue(parentContext.containsKey("task1.key3"));
  }

  @Test
  void shouldHandleTypedKeys() {
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");
    TypedKey<String> stringKey = TypedKey.of("stringKey", String.class);
    TypedKey<Integer> intKey = TypedKey.of("intKey", Integer.class);

    scoped.put(stringKey, "stringValue");
    scoped.put(intKey, 42);

    assertEquals("stringValue", scoped.get(stringKey));
    assertEquals(42, scoped.get(intKey));
    assertEquals("stringValue", parentContext.get("task1.stringKey"));
    assertEquals(42, parentContext.get("task1.intKey"));
  }

  @Test
  void shouldNotAffectParentContextDirectKeys() {
    parentContext.put("directKey", "directValue");
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, "task1");

    scoped.put("key", "value");

    assertEquals("directValue", parentContext.get("directKey"));
    assertEquals("value", parentContext.get("task1.key"));
    assertNull(scoped.get("directKey")); // Scoped context doesn't see parent's direct keys
  }

  @Test
  void shouldHandleLongPrefixes() {
    String longPrefix = "a".repeat(100);
    ScopedWorkflowContext scoped = new ScopedWorkflowContext(parentContext, longPrefix);

    scoped.put("key", "value");

    assertEquals("value", scoped.get("key"));
    assertTrue(parentContext.containsKey(longPrefix + ".key"));
  }

  @Test
  void shouldHandleDeeplyNestedScopes() {
    WorkflowContext current = parentContext;
    for (int i = 0; i < 10; i++) {
      current = new ScopedWorkflowContext(current, "level" + i);
    }

    current.put("key", "value");

    assertEquals("value", current.get("key"));
    assertTrue(
        parentContext.containsKey(
            "level0.level1.level2.level3.level4.level5.level6.level7.level8.level9.key"));
  }

  @Test
  void testGetWithDefault() {
    WorkflowContext scopedContext = parentContext.scope(NAMESPACE);
    // Setup: Put directly into root with prefix
    parentContext.put("userModule.status", "ACTIVE");

    // Verify scoped access
    assertEquals("ACTIVE", scopedContext.get("status", "INACTIVE"));

    // Verify default value if key missing in scope
    assertEquals("DEFAULT", scopedContext.get("missing", "DEFAULT"));
  }

  @Test
  void testGetTyped() {
    WorkflowContext scopedContext = parentContext.scope(NAMESPACE);
    parentContext.put("userModule.retryCount", 5);

    Integer count = scopedContext.getTyped("retryCount", Integer.class);

    assertEquals(5, count);
    assertNull(scopedContext.getTyped("unknown", Integer.class));
  }

  @Test
  void testGetTypedWithDefault() {
    WorkflowContext scopedContext = parentContext.scope(NAMESPACE);
    parentContext.put("userModule.threshold", 10.5);

    Double val = scopedContext.getTyped("threshold", Double.class, 0.0);
    Double missingVal = scopedContext.getTyped("absent", Double.class, 1.1);

    assertEquals(10.5, val);
    assertEquals(1.1, missingVal);
  }

  @Test
  void testGetStrictTypedKey() {
    WorkflowContext scopedContext = parentContext.scope(NAMESPACE);
    TypedKey<String> apiKey = TypedKey.of("apiSecret", String.class);

    // Put via scoped context
    scopedContext.put(apiKey, "shhh");

    // Verify it exists in root with prefix
    assertEquals("shhh", parentContext.get("userModule.apiSecret"));

    // Verify strict retrieval
    assertEquals("shhh", scopedContext.getStrict(apiKey));

    // Verify type mismatch throws IllegalStateException with scoped name in message
    parentContext.put("userModule.apiSecret", 12345); // Corrupt the type

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> scopedContext.getStrict(apiKey));
    assertTrue(ex.getMessage().contains("userModule.apiSecret type mismatch"));
  }

  @Test
  void testGetTypedWithTypeReference() {
    WorkflowContext scopedContext = parentContext.scope(NAMESPACE);
    List<String> roles = List.of("ADMIN", "EDITOR");
    scopedContext.put("roles", roles);

    // Success case
    List<String> retrieved = scopedContext.getTyped("roles", new TypeReference<>() {});
    assertEquals(2, retrieved.size());
    assertTrue(retrieved.contains("ADMIN"));

    // Verify prefixing in root
    assertNotNull(parentContext.get("userModule.roles"));
  }

  @Test
  void testGetTypedWithTypeReferenceAndDefault() {
    WorkflowContext scopedContext = parentContext.scope(NAMESPACE);
    List<Integer> defaultList = List.of(1, 2);

    List<Integer> result =
        scopedContext.getTyped("nonexistentList", new TypeReference<>() {}, defaultList);

    assertEquals(defaultList, result);
  }
}
