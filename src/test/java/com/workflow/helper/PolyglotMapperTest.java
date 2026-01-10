package com.workflow.helper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PolyglotMapperTest {

  @Test
  @DisplayName("Should return null when Value is null or isNull() is true")
  void testNullHandling() {
    assertNull(PolyglotMapper.toJava(null));

    Value mockValue = mock(Value.class);
    when(mockValue.isNull()).thenReturn(true);
    assertNull(PolyglotMapper.toJava(mockValue));
  }

  @Test
  @DisplayName("Should convert primitives correctly")
  void testPrimitives() {
    Value stringVal = mock(Value.class);
    when(stringVal.isString()).thenReturn(true);
    when(stringVal.asString()).thenReturn("hello");
    assertEquals("hello", PolyglotMapper.toJava(stringVal));

    Value boolVal = mock(Value.class);
    when(boolVal.isBoolean()).thenReturn(true);
    when(boolVal.asBoolean()).thenReturn(true);
    assertEquals(true, PolyglotMapper.toJava(boolVal));
  }

  @Test
  @DisplayName("Should convert numbers based on capacity (Int -> Long -> Double)")
  void testNumbers() {
    Value intVal = mock(Value.class);
    when(intVal.isNumber()).thenReturn(true);
    when(intVal.fitsInInt()).thenReturn(true);
    when(intVal.asInt()).thenReturn(42);
    assertEquals(42, PolyglotMapper.toJava(intVal));

    Value longVal = mock(Value.class);
    when(longVal.isNumber()).thenReturn(true);
    when(longVal.fitsInInt()).thenReturn(false);
    when(longVal.fitsInLong()).thenReturn(true);
    when(longVal.asLong()).thenReturn(5000000000L);
    assertEquals(5000000000L, PolyglotMapper.toJava(longVal));

    Value doubleVal = mock(Value.class);
    when(doubleVal.isNumber()).thenReturn(true);
    when(doubleVal.fitsInInt()).thenReturn(false);
    when(doubleVal.fitsInLong()).thenReturn(false);
    when(doubleVal.asDouble()).thenReturn(3.14);
    assertEquals(3.14, PolyglotMapper.toJava(doubleVal));
  }

  @Test
  @DisplayName("Should convert Polyglot Arrays to Java Lists")
  void testArrayToList() {
    Value arrayVal = mock(Value.class);
    Value element = mock(Value.class);

    when(arrayVal.hasArrayElements()).thenReturn(true);
    when(arrayVal.getArraySize()).thenReturn(1L);
    when(arrayVal.getArrayElement(0)).thenReturn(element);

    when(element.isString()).thenReturn(true);
    when(element.asString()).thenReturn("item1");

    Object result = PolyglotMapper.toJava(arrayVal);

    assertInstanceOf(List.class, result);
    List<?> list = (List<?>) result;
    assertEquals(1, list.size());
    assertEquals("item1", list.getFirst());
  }

  @Test
  @DisplayName("Should convert Polyglot Objects to LinkedHashMap")
  @SuppressWarnings("unchecked")
  void testObjectToMap() {
    Value objVal = mock(Value.class);
    Value memberVal = mock(Value.class);

    when(objVal.hasMembers()).thenReturn(true);
    when(objVal.getMemberKeys()).thenReturn(Set.of("key1"));
    when(objVal.getMember("key1")).thenReturn(memberVal);

    when(memberVal.isString()).thenReturn(true);
    when(memberVal.asString()).thenReturn("value1");

    Object result = PolyglotMapper.toJava(objVal);

    assertInstanceOf(Map.class, result);
    Map<String, Object> map = (Map<String, Object>) result;
    assertEquals("value1", map.get("key1"));
  }

  @Test
  @DisplayName("Should handle Host Objects (Java objects passed to JS)")
  void testHostObject() {
    Value hostVal = mock(Value.class);
    Object myJavaObj = new StringBuilder("test");

    when(hostVal.isHostObject()).thenReturn(true);
    when(hostVal.asHostObject()).thenReturn(myJavaObj);

    assertEquals(myJavaObj, PolyglotMapper.toJava(hostVal));
  }

  @Test
  @DisplayName("Should handle complex nested structures")
  @SuppressWarnings("unchecked")
  void testNestedStructure() {
    Value root = mock(Value.class);
    Value listVal = mock(Value.class);
    Value numVal = mock(Value.class);

    when(root.hasMembers()).thenReturn(true);
    when(root.getMemberKeys()).thenReturn(Set.of("data"));
    when(root.getMember("data")).thenReturn(listVal);

    when(listVal.hasArrayElements()).thenReturn(true);
    when(listVal.getArraySize()).thenReturn(1L);
    when(listVal.getArrayElement(0)).thenReturn(numVal);

    when(numVal.isNumber()).thenReturn(true);
    when(numVal.fitsInInt()).thenReturn(true);
    when(numVal.asInt()).thenReturn(100);

    Map<String, Object> result = (Map<String, Object>) PolyglotMapper.toJava(root);
    List<Object> nestedList = (List<Object>) result.get("data");

    assertEquals(100, nestedList.getFirst());
  }

  @Test
  @DisplayName("Negative Case: Fallback to Object.class if no type matches")
  void testFallbackMapping() {
    Value unknownVal = mock(Value.class);
    // Ensure all specific checks return false
    when(unknownVal.isNull()).thenReturn(false);
    when(unknownVal.isHostObject()).thenReturn(false);
    when(unknownVal.hasArrayElements()).thenReturn(false);
    when(unknownVal.hasMembers()).thenReturn(false);
    when(unknownVal.isString()).thenReturn(false);
    when(unknownVal.isBoolean()).thenReturn(false);
    when(unknownVal.isNumber()).thenReturn(false);

    Object fallbackObj = new Object();
    when(unknownVal.as(Object.class)).thenReturn(fallbackObj);

    assertEquals(fallbackObj, PolyglotMapper.toJava(unknownVal));
  }

  @Test
  @DisplayName("Should handle empty Collections (Empty Map and Empty List)")
  void testEmptyCollections() {
    // Mock Empty List
    Value emptyList = mock(Value.class);
    when(emptyList.hasArrayElements()).thenReturn(true);
    when(emptyList.getArraySize()).thenReturn(0L);

    List<?> resultList = (List<?>) PolyglotMapper.toJava(emptyList);
    assertTrue(resultList.isEmpty());

    // Mock Empty Map
    Value emptyMap = mock(Value.class);
    when(emptyMap.hasMembers()).thenReturn(true);
    when(emptyMap.getMemberKeys()).thenReturn(Collections.emptySet());

    Map<?, ?> resultMap = (Map<?, ?>) PolyglotMapper.toJava(emptyMap);
    assertTrue(resultMap.isEmpty());
  }

  @Test
  @DisplayName("Should handle Deeply Nested structures (List inside Map inside List)")
  @SuppressWarnings("unchecked")
  void testDeepNesting() {
    // Structure: [ { "key": [ "value" ] } ]
    Value outerList = mock(Value.class);
    Value innerMap = mock(Value.class);
    Value innerList = mock(Value.class);
    Value leafString = mock(Value.class);

    when(outerList.hasArrayElements()).thenReturn(true);
    when(outerList.getArraySize()).thenReturn(1L);
    when(outerList.getArrayElement(0)).thenReturn(innerMap);

    when(innerMap.hasMembers()).thenReturn(true);
    when(innerMap.getMemberKeys()).thenReturn(Set.of("key"));
    when(innerMap.getMember("key")).thenReturn(innerList);

    when(innerList.hasArrayElements()).thenReturn(true);
    when(innerList.getArraySize()).thenReturn(1L);
    when(innerList.getArrayElement(0)).thenReturn(leafString);

    when(leafString.isString()).thenReturn(true);
    when(leafString.asString()).thenReturn("deepValue");

    List<Map<String, List<String>>> result =
        (List<Map<String, List<String>>>) PolyglotMapper.toJava(outerList);
    assertEquals("deepValue", result.getFirst().get("key").getFirst());
  }

  @Test
  @DisplayName("Should handle Mixed Types in a single Array")
  void testMixedTypeArray() {
    Value array = mock(Value.class);
    Value val1 = mock(Value.class); // String
    Value val2 = mock(Value.class); // Int

    when(array.hasArrayElements()).thenReturn(true);
    when(array.getArraySize()).thenReturn(2L);
    when(array.getArrayElement(0)).thenReturn(val1);
    when(array.getArrayElement(1)).thenReturn(val2);

    when(val1.isString()).thenReturn(true);
    when(val1.asString()).thenReturn("text");

    when(val2.isNumber()).thenReturn(true);
    when(val2.fitsInInt()).thenReturn(true);
    when(val2.asInt()).thenReturn(1);

    @SuppressWarnings("unchecked")
    List<Object> result = (List<Object>) PolyglotMapper.toJava(array);
    assertEquals("text", result.get(0));
    assertEquals(1, result.get(1));
  }

  @Test
  @DisplayName("Should handle Map with null values")
  void testMapWithNullValues() {
    Value mapValue = mock(Value.class);
    Value nullMember = mock(Value.class);

    when(mapValue.hasMembers()).thenReturn(true);
    when(mapValue.getMemberKeys()).thenReturn(Set.of("nullableKey"));
    when(mapValue.getMember("nullableKey")).thenReturn(nullMember);
    when(nullMember.isNull()).thenReturn(true);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) PolyglotMapper.toJava(mapValue);
    assertTrue(result.containsKey("nullableKey"));
    assertNull(result.get("nullableKey"));
  }

  @Test
  @DisplayName("Should throw exception if Polyglot Value throws on access")
  void testExceptionPropagation() {
    Value unstableValue = mock(Value.class);
    when(unstableValue.isNull()).thenReturn(false);
    when(unstableValue.isHostObject()).thenThrow(new RuntimeException("Context closed"));

    assertThrows(RuntimeException.class, () -> PolyglotMapper.toJava(unstableValue));
  }
}
