package com.workflow.helper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpResponseWrapperTest {

  @Test
  void constructor_withValidParameters_createsInstance() {
    Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
    String body = "test body";
    int status = 200;

    HttpResponseWrapper<String> wrapper = new HttpResponseWrapper<>(status, headers, body);

    assertEquals(status, wrapper.getStatus());
    assertEquals(body, wrapper.getBody());
    assertEquals(headers, wrapper.getHeaders());
  }

  @Test
  void constructor_copiesHeadersMap() {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Content-Type", List.of("application/json"));
    headers.put("Cache-Control", List.of("no-cache"));

    HttpResponseWrapper<String> wrapper = new HttpResponseWrapper<>(200, headers, "body");

    // Modify original map
    headers.put("New-Header", List.of("value"));

    // Wrapper should have immutable copy
    assertEquals(2, wrapper.getHeaders().size());
    assertFalse(wrapper.getHeaders().containsKey("New-Header"));
  }

  @Test
  void constructor_withEmptyHeaders_createsInstance() {
    HttpResponseWrapper<String> wrapper =
        new HttpResponseWrapper<>(404, Collections.emptyMap(), "not found");

    assertEquals(404, wrapper.getStatus());
    assertEquals("not found", wrapper.getBody());
    assertTrue(wrapper.getHeaders().isEmpty());
  }

  @Test
  void constructor_withNullBody_createsInstance() {
    Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));

    HttpResponseWrapper<Object> wrapper = new HttpResponseWrapper<>(204, headers, null);

    assertEquals(204, wrapper.getStatus());
    assertNull(wrapper.getBody());
    assertNotNull(wrapper.getHeaders());
  }

  @Test
  void constructor_withMultipleHeaderValues_preservesList() {
    Map<String, List<String>> headers =
        Map.of(
            "Set-Cookie",
            List.of("cookie1=value1", "cookie2=value2", "cookie3=value3"),
            "Accept",
            List.of("application/json", "text/html"));

    HttpResponseWrapper<String> wrapper = new HttpResponseWrapper<>(200, headers, "body");

    assertEquals(3, wrapper.getHeaders().get("Set-Cookie").size());
    assertEquals(2, wrapper.getHeaders().get("Accept").size());
  }

  @Test
  void constructor_withDifferentStatusCodes_createsInstance() {
    HttpResponseWrapper<String> response200 = new HttpResponseWrapper<>(200, Map.of(), "success");
    HttpResponseWrapper<String> response404 = new HttpResponseWrapper<>(404, Map.of(), "not found");
    HttpResponseWrapper<String> response500 =
        new HttpResponseWrapper<>(500, Map.of(), "server error");

    assertEquals(200, response200.getStatus());
    assertEquals(404, response404.getStatus());
    assertEquals(500, response500.getStatus());
  }

  @Test
  void constructor_withComplexBodyType_createsInstance() {
    record User(String name, String email) {}

    User user = new User("John Doe", "john@example.com");
    Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));

    HttpResponseWrapper<User> wrapper = new HttpResponseWrapper<>(200, headers, user);

    assertEquals(200, wrapper.getStatus());
    assertEquals(user, wrapper.getBody());
    assertEquals("John Doe", wrapper.getBody().name());
    assertEquals("john@example.com", wrapper.getBody().email());
  }

  @Test
  void constructor_withListBody_createsInstance() {
    List<String> bodyList = List.of("item1", "item2", "item3");
    Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));

    HttpResponseWrapper<List<String>> wrapper = new HttpResponseWrapper<>(200, headers, bodyList);

    assertEquals(200, wrapper.getStatus());
    assertEquals(bodyList, wrapper.getBody());
    assertEquals(3, wrapper.getBody().size());
  }

  @Test
  void constructor_withMapBody_createsInstance() {
    Map<String, Object> bodyMap = Map.of("key1", "value1", "key2", 123);
    Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));

    HttpResponseWrapper<Map<String, Object>> wrapper =
        new HttpResponseWrapper<>(200, headers, bodyMap);

    assertEquals(200, wrapper.getStatus());
    assertEquals(bodyMap, wrapper.getBody());
    assertEquals("value1", wrapper.getBody().get("key1"));
    assertEquals(123, wrapper.getBody().get("key2"));
  }
}
