package com.workflow.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for TypedKey covering equals, hashCode, toString, and edge cases. */
class TypedKeyTest {

  @Test
  void of_createsTypedKey() {
    TypedKey<String> key = TypedKey.of("username", String.class);

    assertNotNull(key);
    assertEquals("username", key.name());
    assertEquals(String.class, key.type());
  }

  @Test
  void of_nullName_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> TypedKey.of(null, String.class));
  }

  @Test
  void of_nullType_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> TypedKey.of("key", null));
  }

  @Test
  void equals_sameNameAndType_returnsTrue() {
    TypedKey<String> key1 = TypedKey.of("name", String.class);
    TypedKey<String> key2 = TypedKey.of("name", String.class);

    assertEquals(key1, key2);
    assertEquals(key2, key1);
  }

  @Test
  void equals_differentName_returnsFalse() {
    TypedKey<String> key1 = TypedKey.of("name1", String.class);
    TypedKey<String> key2 = TypedKey.of("name2", String.class);

    assertNotEquals(key1, key2);
  }

  @Test
  void equals_null_returnsFalse() {
    TypedKey<String> key = TypedKey.of("name", String.class);

    assertNotEquals(null, key);
  }

  @Test
  void hashCode_sameNameAndType_returnsSameHashCode() {
    TypedKey<String> key1 = TypedKey.of("name", String.class);
    TypedKey<String> key2 = TypedKey.of("name", String.class);

    assertEquals(key1.hashCode(), key2.hashCode());
  }

  @Test
  void hashCode_differentName_returnsDifferentHashCode() {
    TypedKey<String> key1 = TypedKey.of("name1", String.class);
    TypedKey<String> key2 = TypedKey.of("name2", String.class);

    assertNotEquals(key1.hashCode(), key2.hashCode());
  }

  @Test
  void hashCode_differentType_returnsDifferentHashCode() {
    TypedKey<String> key1 = TypedKey.of("key", String.class);
    TypedKey<Integer> key2 = TypedKey.of("key", Integer.class);

    assertNotEquals(key1.hashCode(), key2.hashCode());
  }

  @Test
  void hashCode_consistent() {
    TypedKey<String> key = TypedKey.of("name", String.class);

    int hashCode1 = key.hashCode();
    int hashCode2 = key.hashCode();

    assertEquals(hashCode1, hashCode2);
  }

  @Test
  void toString_includesNameAndType() {
    TypedKey<String> key = TypedKey.of("username", String.class);

    String str = key.toString();

    assertTrue(str.contains("username"));
    assertTrue(str.contains("String"));
  }

  @Test
  void toString_formatIsCorrect() {
    TypedKey<Integer> key = TypedKey.of("count", Integer.class);

    assertEquals("count<Integer>", key.toString());
  }

  @Test
  void toString_differentTypes() {
    assertEquals("name<String>", TypedKey.of("name", String.class).toString());
    assertEquals("age<Integer>", TypedKey.of("age", Integer.class).toString());
    assertEquals("flag<Boolean>", TypedKey.of("flag", Boolean.class).toString());
  }

  @Test
  void name_returnsCorrectName() {
    TypedKey<String> key = TypedKey.of("testName", String.class);

    assertEquals("testName", key.name());
  }

  @Test
  void type_returnsCorrectType() {
    TypedKey<String> key = TypedKey.of("test", String.class);

    assertEquals(String.class, key.type());
  }

  @Test
  void multipleInstances_canBeUsedInCollections() {
    TypedKey<String> key1 = TypedKey.of("key1", String.class);
    TypedKey<String> key2 = TypedKey.of("key2", String.class);
    TypedKey<String> key3 = TypedKey.of("key1", String.class); // Same as key1

    java.util.Set<TypedKey<?>> set = new java.util.HashSet<>();
    set.add(key1);
    set.add(key2);
    set.add(key3);

    // key3 is equal to key1, so set should have only 2 elements
    assertEquals(2, set.size());
  }

  @Test
  void canBeUsedAsMapKey() {
    TypedKey<String> key1 = TypedKey.of("name", String.class);
    TypedKey<Integer> key2 = TypedKey.of("age", Integer.class);

    java.util.Map<TypedKey<?>, Object> map = new java.util.HashMap<>();
    map.put(key1, "Alice");
    map.put(key2, 30);

    assertEquals("Alice", map.get(key1));
    assertEquals(30, map.get(key2));
  }

  @Test
  void equalKeys_canRetrieveSameValue() {
    TypedKey<String> key1 = TypedKey.of("name", String.class);
    TypedKey<String> key2 = TypedKey.of("name", String.class);

    java.util.Map<TypedKey<?>, Object> map = new java.util.HashMap<>();
    map.put(key1, "value");

    assertEquals("value", map.get(key2)); // key2 equals key1
  }

  @Test
  void complexTypes_work() {
    @SuppressWarnings("unchecked")
    TypedKey<java.util.List<String>> listKey =
        TypedKey.of("items", (Class<List<String>>) (Class<?>) List.class);

    assertNotNull(listKey);
    assertEquals("items", listKey.name());
  }

  @Test
  void primitiveTypes_work() {
    TypedKey<Integer> intKey = TypedKey.of("count", Integer.class);
    TypedKey<Double> doubleKey = TypedKey.of("price", Double.class);
    TypedKey<Boolean> boolKey = TypedKey.of("flag", Boolean.class);

    assertNotNull(intKey);
    assertNotNull(doubleKey);
    assertNotNull(boolKey);
  }

  @Test
  void emptyName_isAllowed() {
    TypedKey<String> key = TypedKey.of("", String.class);

    assertEquals("", key.name());
    assertEquals("<String>", key.toString());
  }

  @Test
  void longName_isAllowed() {
    String longName = "a".repeat(1000);
    TypedKey<String> key = TypedKey.of(longName, String.class);

    assertEquals(longName, key.name());
  }

  @Test
  void specialCharactersInName_work() {
    TypedKey<String> key1 = TypedKey.of("user.name", String.class);
    TypedKey<String> key2 = TypedKey.of("user:id", String.class);
    TypedKey<String> key3 = TypedKey.of("user_email", String.class);

    assertEquals("user.name", key1.name());
    assertEquals("user:id", key2.name());
    assertEquals("user_email", key3.name());
  }

  @Test
  void differentGenericTypes_areNotEqual() {
    TypedKey<?> key1 = TypedKey.of("list", java.util.List.class);
    TypedKey<?> key2 = TypedKey.of("list", java.util.ArrayList.class);

    assertNotEquals(key1, key2);
  }

  @Test
  void sameNameDifferentCase_areNotEqual() {
    TypedKey<String> key1 = TypedKey.of("Name", String.class);
    TypedKey<String> key2 = TypedKey.of("name", String.class);

    assertNotEquals(key1, key2);
  }

  @Test
  void customClass_asType() {
    class CustomClass {}

    TypedKey<CustomClass> key = TypedKey.of("custom", CustomClass.class);

    assertEquals("custom", key.name());
    assertEquals(CustomClass.class, key.type());
    assertTrue(key.toString().contains("CustomClass"));
  }

  @Test
  void interfaceType_works() {
    TypedKey<java.io.Serializable> key = TypedKey.of("serializable", java.io.Serializable.class);

    assertEquals(java.io.Serializable.class, key.type());
  }

  @Test
  void abstractClassType_works() {
    TypedKey<Number> key = TypedKey.of("number", Number.class);

    assertEquals(Number.class, key.type());
  }

  @Test
  void equalsAndHashCode_contract() {
    TypedKey<String> key1 = TypedKey.of("test", String.class);
    TypedKey<String> key2 = TypedKey.of("test", String.class);

    // Symmetric
    assertEquals(key1, key2);
    assertEquals(key2, key1);

    // Consistent
    assertEquals(key1, key2);
    assertEquals(key1, key2);

    // hashCode contract
    assertEquals(key1.hashCode(), key2.hashCode());
  }

  @Test
  void nullValue_inHashMap() {
    TypedKey<String> key = TypedKey.of("nullable", String.class);
    Map<TypedKey<?>, Object> map = new HashMap<>();

    map.put(key, null);

    assertTrue(map.containsKey(key));
    assertNull(map.get(key));
  }
}
