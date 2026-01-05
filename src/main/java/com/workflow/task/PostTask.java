package com.workflow.task;

import com.workflow.context.WorkflowContext;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP POST task implementation that extends {@link AbstractHttpTask}. Supports request body via:
 *
 * <ul>
 *   <li>Explicit body via {@link Builder#body(String)} - highest precedence
 *   <li>Context-provided REQUEST_BODY - second precedence
 *   <li>Form data via {@link Builder#form(Map)} - third precedence
 *   <li>Empty body - fallback
 * </ul>
 *
 * <p>Form data (if provided) is URL-encoded with content-type application/x-www-form-urlencoded.
 * Explicit and context body default to application/json content-type.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * HttpClient httpClient = HttpClient.newHttpClient();
 * WorkflowContext context = new WorkflowContext();
 *
 * // With explicit JSON body
 * PostTask<String> postTask = new PostTask.Builder<String>(httpClient)
 *    .url("https://api.example.com/resource")
 *    .body("{\"name\":\"example\",\"value\":42}")
 *    .responseType(String.class)
 *    .build();
 *
 * postTask.execute(context);
 * String response = context.getTyped("httpResponse", String.class);
 *
 * // With form data
 * PostTask<String> formTask = new PostTask.Builder<String>(httpClient)
 *    .url("https://api.example.com/resource")
 *    .form(Map.of("username", "john", "password", "secret"))
 *    .build();
 * }</pre>
 *
 * @param <T> the type of the deserialized response
 * @see AbstractHttpTask
 * @see HttpTaskBodyHelper
 */
public class PostTask<T> extends AbstractHttpTask<T> {
  private final String body;
  private final Map<String, String> formData;

  private PostTask(Builder<T> b) {
    super(b);
    this.body = b.body;
    this.formData = b.formData != null ? Map.copyOf(b.formData) : Map.of();
  }

  @Override
  protected void prepareRequest(HttpRequest.Builder builder, WorkflowContext context) {
    HttpTaskBodyHelper.setBodyWithContentType(
        body,
        formData,
        context,
        builder::POST,
        contentType -> setContentType(builder, contentType));
  }

  /**
   * Builder for PostTask with fluent API.
   *
   * @param <T> the type of the response
   */
  public static class Builder<T> extends AbstractHttpTask.Builder<Builder<T>, PostTask<T>, T> {
    private String body;
    private Map<String, String> formData;

    /**
     * Create a new PostTask.Builder.
     *
     * @param httpClient the HttpClient to use for requests (required)
     */
    public Builder(HttpClient httpClient) {
      super(httpClient);
    }

    /**
     * Set the explicit JSON request body. Takes precedence over form data and context body.
     *
     * @param body the body string (maybe null)
     * @return this builder for chaining
     */
    public Builder<T> body(String body) {
      this.body = body;
      return this;
    }

    /**
     * Set form data to be URL-encoded. Ignored if explicit body is provided.
     *
     * @param form the form data map (maybe null)
     * @return this builder for chaining
     * @throws IllegalArgumentException if any key is null/blank or any value is null when building
     */
    public Builder<T> form(Map<String, String> form) {
      this.formData = form == null ? null : new LinkedHashMap<>(form);
      return this;
    }

    @Override
    protected Builder<T> self() {
      return this;
    }

    @Override
    public PostTask<T> build() {
      HttpTaskBodyHelper.validateFormData(formData);
      return new PostTask<>(this);
    }
  }
}
