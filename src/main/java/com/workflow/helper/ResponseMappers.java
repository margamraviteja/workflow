package com.workflow.helper;

import com.workflow.exception.HttpResponseProcessingException;
import com.workflow.exception.JsonProcessingException;
import com.workflow.exception.TaskExecutionException;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

/**
 * Common response mappers for HTTP tasks. Provides strict typed mapper that checks for HTTP errors
 * before deserializing, default mapper that returns body as String, and typed default mapper that
 * deserializes JSON into specified type.
 */
@UtilityClass
public final class ResponseMappers {
  /**
   * Strict typed mapper that checks for HTTP errors before deserializing JSON into responseType and
   * throws TaskExecutionException on non-2xx
   */
  public static <T> Function<HttpResponse<String>, T> strictTypedMapper(Class<T> responseType) {
    return resp -> {
      int status = resp.statusCode();
      String body = resp.body();
      if (status < 200 || status >= 300) {
        // include body for debugging but avoid huge payloads
        if (body != null && body.length() > 1024) body = body.substring(0, 1024) + "...";
        throw new HttpResponseProcessingException("HTTP " + status + " returned: " + body);
      }
      if (responseType == null || responseType == String.class) {
        @SuppressWarnings("unchecked")
        T cast = (T) body;
        return cast;
      }
      try {
        return JsonUtils.fromJson(body, responseType);
      } catch (IOException e) {
        throw new JsonProcessingException(
            "Failed to deserialize response to " + responseType.getName(), e);
      }
    };
  }

  /** Default response mapper returns the response body as String (unchecked cast). */
  @SuppressWarnings("unchecked")
  public static <T> Function<HttpResponse<String>, T> defaultResponseMapper() {
    return resp -> (T) resp.body();
  }

  /** Typed default mapper that deserializes JSON into responseType using JsonUtils. */
  public static <T> Function<HttpResponse<String>, T> defaultTypedResponseMapper(
      Class<T> responseType) {
    return resp -> {
      String body = resp.body();
      if (body == null || body.isBlank()) return null;
      try {
        return JsonUtils.fromJson(body, responseType);
      } catch (IOException e) {
        throw new JsonProcessingException(
            "Failed to deserialize response to " + responseType.getName(), e);
      }
    };
  }

  /** Adapter that converts mapper exceptions into TaskExecutionException when used manually. */
  public static <T> Function<HttpResponse<String>, T> wrapToTaskException(
      Function<HttpResponse<String>, T> mapper) {
    return resp -> {
      try {
        return mapper.apply(resp);
      } catch (TaskExecutionException e) {
        throw e; // propagate as is
      } catch (Exception e) {
        if (e.getCause() instanceof TaskExecutionException taskExecutionException) {
          throw taskExecutionException;
        }
        throw new TaskExecutionException(e);
      }
    };
  }
}
