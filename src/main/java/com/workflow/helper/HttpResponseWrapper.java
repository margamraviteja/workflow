package com.workflow.helper;

import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * Immutable wrapper encapsulating complete HTTP response details.
 *
 * <p><b>Purpose:</b> Packages HTTP response metadata (status code, headers) together with the
 * deserialized body. Useful when you need full response details, not just the body.
 *
 * <p><b>Contents:</b>
 *
 * <ul>
 *   <li><b>Status:</b> HTTP status code (200, 404, 500, etc.)
 *   <li><b>Headers:</b> Response headers as an immutable map
 *   <li><b>Body:</b> Deserialized response body (type-generic)
 * </ul>
 *
 * <p><b>Immutability:</b> This class is immutable. Headers map is copied on construction. Safe to
 * share across threads.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Capture full HTTP response for inspection
 *   <li>Access response headers (e.g., for pagination, caching, rate limits)
 *   <li>Inspect status codes (e.g., for conditional logic)
 *   <li>Debug HTTP interactions
 * </ul>
 *
 * <p><b>Example Usage - Inspecting Status and Headers:</b>
 *
 * <pre>{@code
 * HttpResponseWrapper<User> response = new HttpResponseWrapper<>(
 *     200,
 *     Map.of("Content-Type", List.of("application/json")),
 *     new User("John", "john@example.com")
 * );
 *
 * if (response.getStatus() == 200) {
 *     User user = response.getBody();
 *     List<String> contentType = response.getHeaders().get("Content-Type");
 *     System.out.println("Retrieved user: " + user.getName());
 * }
 * }</pre>
 *
 * @param <T> type of the deserialized response body
 * @see com.workflow.helper.ResponseMappers
 */
@Getter
public final class HttpResponseWrapper<T> {
  private final int status;
  private final Map<String, List<String>> headers;
  private final T body;

  public HttpResponseWrapper(int status, Map<String, List<String>> headers, T body) {
    this.status = status;
    this.headers = Map.copyOf(headers);
    this.body = body;
  }
}
