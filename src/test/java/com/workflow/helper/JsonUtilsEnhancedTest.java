package com.workflow.helper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

/** Comprehensive test suite for JsonUtils with edge cases and error handling. */
class JsonUtilsEnhancedTest {

  // ==== fromJson Tests ====

  @Test
  void fromJson_validJson_deserializesCorrectly() {
    String json = "{\"name\":\"John\",\"age\":30}";

    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertNotNull(result);
    assertEquals("John", result.get("name"));
    assertEquals(30, result.get("age"));
  }

  @Test
  void fromJson_null_returnsNull() {
    assertNull(JsonUtils.fromJson(null, String.class));
  }

  @Test
  void fromJson_emptyString_returnsNull() {
    Object result = JsonUtils.fromJson("", String.class);

    assertNull(result);
  }

  @Test
  void fromJson_whitespaceOnly_throwsIOException() {
    assertThrows(JacksonException.class, () -> JsonUtils.fromJson("   ", String.class));
  }

  @Test
  void fromJson_invalidJson_throwsIOException() {
    String invalidJson = "{invalid json}";

    assertThrows(JacksonException.class, () -> JsonUtils.fromJson(invalidJson, Map.class));
  }

  @Test
  void fromJson_malformedJson_throwsIOException() {
    String malformedJson = "{\"name\":\"John\",\"age\":}";

    assertThrows(JacksonException.class, () -> JsonUtils.fromJson(malformedJson, Map.class));
  }

  @Test
  void fromJson_unknownProperties_doesNotFail() {
    String json = "{\"name\":\"John\",\"age\":30,\"extra\":\"ignored\"}";

    TestPojo result = JsonUtils.fromJson(json, TestPojo.class);

    assertNotNull(result);
    assertEquals("John", result.name);
    assertEquals(30, result.age);
    // extra property is ignored (not causing failure)
  }

  @Test
  void fromJson_missingProperties_setsToNull() {
    String json = "{\"name\":\"John\"}";

    TestPojo result = JsonUtils.fromJson(json, TestPojo.class);

    assertNotNull(result);
    assertEquals("John", result.name);
    assertEquals(0, result.age); // Primitive int defaults to 0
  }

  @Test
  void fromJson_nestedObjects_deserializesCorrectly() {
    String json = "{\"user\":{\"name\":\"John\",\"age\":30}}";

    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertNotNull(result);
    @SuppressWarnings("unchecked")
    Map<String, Object> user = (Map<String, Object>) result.get("user");
    assertEquals("John", user.get("name"));
    assertEquals(30, user.get("age"));
  }

  @Test
  void fromJson_array_deserializesCorrectly() {
    String json = "[1,2,3,4,5]";

    List<Integer> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertNotNull(result);
    assertEquals(5, result.size());
    assertEquals(1, result.get(0));
    assertEquals(5, result.get(4));
  }

  @Test
  void fromJson_emptyArray_deserializesCorrectly() {
    String json = "[]";

    List<?> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void fromJson_emptyObject_deserializesCorrectly() {
    String json = "{}";

    Map<?, ?> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void fromJson_nullValue_deserializesCorrectly() {
    String json = "{\"name\":null}";

    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertNotNull(result);
    assertTrue(result.containsKey("name"));
    assertNull(result.get("name"));
  }

  @Test
  void fromJson_booleanValues_deserializesCorrectly() {
    String json = "{\"active\":true,\"deleted\":false}";

    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertEquals(true, result.get("active"));
    assertEquals(false, result.get("deleted"));
  }

  @Test
  void fromJson_numberTypes_deserializesCorrectly() {
    String json = "{\"intVal\":42,\"doubleVal\":3.14,\"longVal\":9999999999}";

    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertNotNull(result.get("intVal"));
    assertNotNull(result.get("doubleVal"));
    assertNotNull(result.get("longVal"));
  }

  @Test
  void fromJson_specialCharacters_deserializesCorrectly() {
    String json = "{\"text\":\"Hello\\nWorld\\t!\"}";

    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertEquals("Hello\nWorld\t!", result.get("text"));
  }

  @Test
  void fromJson_unicodeCharacters_deserializesCorrectly() {
    String json = "{\"emoji\":\"😊\",\"chinese\":\"你好\"}";

    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertEquals("😊", result.get("emoji"));
    assertEquals("你好", result.get("chinese"));
  }

  @Test
  void fromJson_escapedQuotes_deserializesCorrectly() {
    String json = "{\"text\":\"He said \\\"Hello\\\"\"}";

    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertEquals("He said \"Hello\"", result.get("text"));
  }

  // ==== toJson Tests ====

  @Test
  void toJson_null_returnsNull() {
    assertNull(JsonUtils.toJson(null));
  }

  @Test
  void toJson_simpleObject_serializesCorrectly() {
    TestPojo pojo = new TestPojo();
    pojo.name = "John";
    pojo.age = 30;

    String json = JsonUtils.toJson(pojo);

    assertNotNull(json);
    assertTrue(json.contains("\"name\":\"John\""));
    assertTrue(json.contains("\"age\":30"));
  }

  @Test
  void toJson_map_serializesCorrectly() {
    Map<String, Object> map = new HashMap<>();
    map.put("key1", "value1");
    map.put("key2", 42);

    String json = JsonUtils.toJson(map);

    assertNotNull(json);
    assertTrue(json.contains("\"key1\":\"value1\""));
    assertTrue(json.contains("\"key2\":42"));
  }

  @Test
  void toJson_list_serializesCorrectly() {
    List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);

    String json = JsonUtils.toJson(list);

    assertEquals("[1,2,3,4,5]", json);
  }

  @Test
  void toJson_emptyList_serializesCorrectly() {
    List<?> list = Collections.emptyList();

    String json = JsonUtils.toJson(list);

    assertEquals("[]", json);
  }

  @Test
  void toJson_emptyMap_serializesCorrectly() {
    Map<?, ?> map = Collections.emptyMap();

    String json = JsonUtils.toJson(map);

    assertEquals("{}", json);
  }

  @Test
  void toJson_nestedObjects_serializesCorrectly() {
    Map<String, Object> outer = new HashMap<>();
    Map<String, Object> inner = new HashMap<>();
    inner.put("name", "John");
    outer.put("user", inner);

    String json = JsonUtils.toJson(outer);

    assertNotNull(json);
    assertTrue(json.contains("\"user\""));
    assertTrue(json.contains("\"name\":\"John\""));
  }

  @Test
  void toJson_nullField_serializesAsNull() {
    Map<String, Object> map = new HashMap<>();
    map.put("key", null);

    String json = JsonUtils.toJson(map);

    assertTrue(json.contains("\"key\":null"));
  }

  @Test
  void toJson_booleanValues_serializesCorrectly() {
    Map<String, Boolean> map = new HashMap<>();
    map.put("active", true);
    map.put("deleted", false);

    String json = JsonUtils.toJson(map);

    assertTrue(json.contains("\"active\":true"));
    assertTrue(json.contains("\"deleted\":false"));
  }

  @Test
  void toJson_specialCharacters_escapesCorrectly() {
    Map<String, String> map = new HashMap<>();
    map.put("text", "Hello\nWorld\t!");

    String json = JsonUtils.toJson(map);

    assertTrue(json.contains("\\n"));
    assertTrue(json.contains("\\t"));
  }

  @Test
  void toJson_unicodeCharacters_serializesCorrectly() {
    Map<String, String> map = new HashMap<>();
    map.put("emoji", "😊");
    map.put("chinese", "你好");

    String json = JsonUtils.toJson(map);

    assertNotNull(json);
    // Should contain the Unicode characters or their escapes
  }

  @Test
  void toJson_quotes_escapesCorrectly() {
    Map<String, String> map = new HashMap<>();
    map.put("text", "He said \"Hello\"");

    String json = JsonUtils.toJson(map);

    assertTrue(json.contains("\\\""));
  }

  // ==== Round-trip Tests ====

  @Test
  void roundTrip_simpleObject_preservesData() {
    TestPojo original = new TestPojo();
    original.name = "Alice";
    original.age = 25;

    String json = JsonUtils.toJson(original);
    TestPojo result = JsonUtils.fromJson(json, TestPojo.class);

    assertEquals(original.name, result.name);
    assertEquals(original.age, result.age);
  }

  @Test
  void roundTrip_map_preservesData() {
    Map<String, Object> original = new HashMap<>();
    original.put("string", "value");
    original.put("number", 42);
    original.put("boolean", true);

    String json = JsonUtils.toJson(original);
    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertEquals(original.get("string"), result.get("string"));
    assertEquals(original.get("number"), result.get("number"));
    assertEquals(original.get("boolean"), result.get("boolean"));
  }

  @Test
  void roundTrip_list_preservesData() {
    List<String> original = Arrays.asList("a", "b", "c");

    String json = JsonUtils.toJson(original);
    List<String> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertEquals(original, result);
  }

  @Test
  void roundTrip_complexNested_preservesStructure() {
    Map<String, Object> original = new HashMap<>();
    original.put("users", Arrays.asList("Alice", "Bob"));
    Map<String, Integer> scores = new HashMap<>();
    scores.put("Alice", 100);
    scores.put("Bob", 95);
    original.put("scores", scores);

    String json = JsonUtils.toJson(original);
    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertNotNull(result.get("users"));
    assertNotNull(result.get("scores"));
  }

  // ==== Edge Cases ====

  @Test
  void fromJson_veryLargeJson_works() {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < 1000; i++) {
      if (i > 0) sb.append(",");
      sb.append("\"key").append(i).append("\":\"value").append(i).append("\"");
    }
    sb.append("}");

    Map<String, Object> result = JsonUtils.fromJson(sb.toString(), new TypeReference<>() {});

    assertEquals(1000, result.size());
  }

  @Test
  void toJson_veryLargeObject_works() {
    Map<String, String> large = new HashMap<>();
    for (int i = 0; i < 1000; i++) {
      large.put("key" + i, "value" + i);
    }

    String json = JsonUtils.toJson(large);

    assertNotNull(json);
    assertTrue(json.length() > 10000);
  }

  @Test
  @SuppressWarnings("unchecked")
  void fromJson_deeplyNested_works() {
    String json = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":\"value\"}}}}}";

    Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<>() {});

    assertNotNull(result);
    Map<String, Object> a = (Map<String, Object>) result.get("a");
    Map<String, Object> b = (Map<String, Object>) a.get("b");
    Map<String, Object> c = (Map<String, Object>) b.get("c");
    Map<String, Object> d = (Map<String, Object>) c.get("d");
    assertEquals("value", d.get("e"));
  }

  @Test
  void threadSafety_concurrentUsage() throws InterruptedException {
    int threadCount = 10;
    Thread[] threads = new Thread[threadCount];
    List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
      threads[i] =
          new Thread(
              () -> {
                try {
                  for (int j = 0; j < 100; j++) {
                    String json = JsonUtils.toJson(Map.of("key", "value"));
                    Map<?, ?> result = JsonUtils.fromJson(json, new TypeReference<>() {});
                    assertNotNull(result);
                  }
                } catch (Exception e) {
                  exceptions.add(e);
                }
              });
      threads[i].start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    assertTrue(exceptions.isEmpty(), "No exceptions should occur in concurrent usage");
  }

  // ==== Test POJO ====

  public static class TestPojo {
    public String name;
    public int age;
  }
}
