package com.workflow.helper;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.exception.HttpResponseProcessingException;
import com.workflow.exception.JsonProcessingException;
import com.workflow.exception.TaskExecutionException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class ResponseMappersTest {

  private static class TestResponse implements HttpResponse<String> {
    private final int statusCode;
    private final String body;

    TestResponse(int statusCode, String body) {
      this.statusCode = statusCode;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return statusCode;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public java.util.Optional<HttpResponse<String>> previousResponse() {
      return java.util.Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return null;
    }

    @Override
    public String body() {
      return body;
    }

    @Override
    public java.util.Optional<SSLSession> sslSession() {
      return java.util.Optional.empty();
    }

    @Override
    public URI uri() {
      return null;
    }

    @Override
    public HttpClient.Version version() {
      return null;
    }
  }

  @Test
  void strictTypedMapper_with2xxStatus_returnsDeserializedObject() {
    String json = "{\"name\":\"test\"}";
    TestResponse response = new TestResponse(200, json);

    var mapper = ResponseMappers.strictTypedMapper(Map.class);
    assertNotNull(mapper.apply(response));
  }

  @Test
  void strictTypedMapper_withNon2xxStatus_throwsHttpResponseProcessingException() {
    TestResponse response = new TestResponse(404, "Not Found");

    var mapper = ResponseMappers.strictTypedMapper(String.class);

    HttpResponseProcessingException exception =
        assertThrows(HttpResponseProcessingException.class, () -> mapper.apply(response));
    assertTrue(exception.getMessage().contains("HTTP 404"));
  }

  @Test
  void strictTypedMapper_with5xxStatus_throwsHttpResponseProcessingException() {
    TestResponse response = new TestResponse(500, "Internal Server Error");

    var mapper = ResponseMappers.strictTypedMapper(String.class);

    HttpResponseProcessingException exception =
        assertThrows(HttpResponseProcessingException.class, () -> mapper.apply(response));
    assertTrue(exception.getMessage().contains("HTTP 500"));
  }

  @Test
  void strictTypedMapper_withStringType_returnsBodyAsString() {
    String body = "plain text";
    TestResponse response = new TestResponse(200, body);

    var mapper = ResponseMappers.strictTypedMapper(String.class);
    String result = mapper.apply(response);

    assertEquals(body, result);
  }

  @Test
  void strictTypedMapper_withNullType_returnsBodyAsString() {
    String body = "plain text";
    TestResponse response = new TestResponse(200, body);

    var mapper = ResponseMappers.strictTypedMapper(null);
    Object result = mapper.apply(response);

    assertEquals(body, result);
  }

  @Test
  void strictTypedMapper_withInvalidJson_throwsJsonProcessingException() {
    String invalidJson = "{invalid json}";
    TestResponse response = new TestResponse(200, invalidJson);

    var mapper = ResponseMappers.strictTypedMapper(Map.class);

    assertThrows(JsonProcessingException.class, () -> mapper.apply(response));
  }

  @Test
  void strictTypedMapper_withLargeErrorBody_truncatesInErrorMessage() {
    String largeBody = "x".repeat(2000);
    TestResponse response = new TestResponse(400, largeBody);

    var mapper = ResponseMappers.strictTypedMapper(String.class);

    HttpResponseProcessingException exception =
        assertThrows(HttpResponseProcessingException.class, () -> mapper.apply(response));
    assertTrue(exception.getMessage().contains("..."));
    assertTrue(exception.getMessage().length() < largeBody.length());
  }

  @Test
  void defaultResponseMapper_returnsBodyAsString() {
    String body = "test body";
    TestResponse response = new TestResponse(200, body);

    var mapper = ResponseMappers.defaultResponseMapper();
    String result = mapper.apply(response).toString();

    assertEquals(body, result);
  }

  @Test
  void defaultResponseMapper_withNon2xxStatus_stillReturnsBody() {
    String body = "error body";
    TestResponse response = new TestResponse(500, body);

    var mapper = ResponseMappers.defaultResponseMapper();
    String result = mapper.apply(response).toString();

    assertEquals(body, result);
  }

  @Test
  void defaultTypedResponseMapper_withValidJson_returnsDeserializedObject() {
    String json = "{\"name\":\"test\"}";
    TestResponse response = new TestResponse(200, json);

    var mapper = ResponseMappers.defaultTypedResponseMapper(Map.class);
    assertNotNull(mapper.apply(response));
  }

  @Test
  void defaultTypedResponseMapper_withNullBody_returnsNull() {
    TestResponse response = new TestResponse(200, null);

    var mapper = ResponseMappers.defaultTypedResponseMapper(Map.class);
    assertNull(mapper.apply(response));
  }

  @Test
  void defaultTypedResponseMapper_withBlankBody_returnsNull() {
    TestResponse response = new TestResponse(200, "   ");

    var mapper = ResponseMappers.defaultTypedResponseMapper(Map.class);
    assertNull(mapper.apply(response));
  }

  @Test
  void defaultTypedResponseMapper_withInvalidJson_throwsJsonProcessingException() {
    String invalidJson = "{invalid}";
    TestResponse response = new TestResponse(200, invalidJson);

    var mapper = ResponseMappers.defaultTypedResponseMapper(Map.class);

    assertThrows(JsonProcessingException.class, () -> mapper.apply(response));
  }

  @Test
  void wrapToTaskException_withSuccessfulMapper_returnsResult() {
    TestResponse response = new TestResponse(200, "success");

    var originalMapper = ResponseMappers.<String>defaultResponseMapper();
    var wrappedMapper = ResponseMappers.wrapToTaskException(originalMapper);

    String result = wrappedMapper.apply(response);
    assertEquals("success", result);
  }

  @Test
  void wrapToTaskException_withTaskExecutionException_propagatesAsIs() {
    TestResponse response = new TestResponse(200, "test");
    TaskExecutionException originalException = new TaskExecutionException("Original error");

    var mapper =
        ResponseMappers.<String>wrapToTaskException(
            _ -> {
              throw originalException;
            });

    TaskExecutionException thrown =
        assertThrows(TaskExecutionException.class, () -> mapper.apply(response));
    assertSame(originalException, thrown);
  }

  @Test
  void wrapToTaskException_withOtherException_wrapsInTaskExecutionException() {
    TestResponse response = new TestResponse(200, "test");
    RuntimeException originalException = new RuntimeException("Some error");

    var mapper =
        ResponseMappers.<String>wrapToTaskException(
            _ -> {
              throw originalException;
            });

    TaskExecutionException thrown =
        assertThrows(TaskExecutionException.class, () -> mapper.apply(response));
    assertSame(originalException, thrown.getCause());
  }

  @Test
  void wrapToTaskException_withNestedTaskExecutionException_unwrapsAndPropagates() {
    TestResponse response = new TestResponse(200, "test");
    TaskExecutionException innerException = new TaskExecutionException("Inner error");
    RuntimeException wrapperException = new RuntimeException("Wrapper", innerException);

    var mapper =
        ResponseMappers.<String>wrapToTaskException(
            _ -> {
              throw wrapperException;
            });

    TaskExecutionException thrown =
        assertThrows(TaskExecutionException.class, () -> mapper.apply(response));
    assertSame(innerException, thrown);
  }
}
