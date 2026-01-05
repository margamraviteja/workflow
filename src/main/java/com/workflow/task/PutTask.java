package com.workflow.task;

import com.workflow.context.WorkflowContext;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP PUT task that updates resources on a remote server.
 *
 * <p><b>Purpose:</b> Executes HTTP PUT requests in a workflow. Used to update or replace existing
 * resources on remote servers. PUT is idempotent - multiple identical requests have the same effect
 * as a single request.
 *
 * <p><b>Request Body Precedence (highest to lowest):</b>
 *
 * <ol>
 *   <li>Explicit body via {@link Builder#body(String)} → application/json content-type
 *   <li>Context key "requestBody" → application/json content-type
 *   <li>Form data via {@link Builder#form(Map)} → application/x-www-form-urlencoded
 *   <li>Empty body (fallback)
 * </ol>
 *
 * <p><b>PUT vs POST:</b>
 *
 * <ul>
 *   <li><b>PUT:</b> Updates or replaces entire resource at specified URI (idempotent)
 *   <li><b>POST:</b> Creates new resource or performs action (non-idempotent)
 *   <li><b>PATCH:</b> Updates specific fields (not provided here)
 * </ul>
 *
 * <p><b>Context Keys:</b>
 *
 * <ul>
 *   <li><b>Input (optional):</b> "requestBody" for dynamic PUT body
 *   <li><b>Input (optional):</b> "queryParams" map for dynamic query parameters
 *   <li><b>Input (optional):</b> Dynamic URL via urlFromContext()
 *   <li><b>Output:</b> Response stored under configured response context key
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe if the provided HttpClient is thread-safe.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Update existing resources in REST APIs
 *   <li>Replace entire object state
 *   <li>Bulk resource updates
 *   <li>Synchronize data with remote servers
 * </ul>
 *
 * <p><b>Example Usage - Simple PUT Update:</b>
 *
 * <pre>{@code
 * HttpClient client = HttpClient.newHttpClient();
 * PutTask<String> updateTask = new PutTask.Builder<String>(client)
 *     .url("https://api.example.com/users/123")
 *     .body("{\"name\":\"Jane Doe\",\"email\":\"jane@example.com\",\"status\":\"active\"}")
 *     .responseType(String.class)
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * updateTask.execute(context);
 * String result = context.getTyped("httpResponse", String.class);
 * System.out.println("Update result: " + result);
 * }</pre>
 *
 * <p><b>Example Usage - PUT with Idempotency:</b>
 *
 * <pre>{@code
 * // PUT is idempotent - calling it multiple times has same effect as once
 * PutTask<String> idempotentUpdate = new PutTask.Builder<String>(client)
 *     .url("https://api.example.com/config/appSettings")
 *     .body("{\"maxConnections\":100,\"timeout\":5000}")
 *     .responseContextKey("configResult")
 *     .build();
 *
 * // Safe to retry or call multiple times
 * idempotentUpdate.execute(context);
 * idempotentUpdate.execute(context);  // No side effects
 * idempotentUpdate.execute(context);  // Same result
 * }</pre>
 *
 * <p><b>Example Usage - PUT with Form Data:</b>
 *
 * <pre>{@code
 * PutTask<String> updateStatusTask = new PutTask.Builder<String>(client)
 *     .url("https://api.example.com/resources/456")
 *     .form(Map.of("status", "archived", "reason", "deprecated"))
 *     .responseType(String.class)
 *     .build();
 * }</pre>
 *
 * <p><b>Example Usage - In Sequential Update Workflow:</b>
 *
 * <pre>{@code
 * SequentialWorkflow updateWorkflow = SequentialWorkflow.builder()
 *     .name("ResourceUpdateWorkflow")
 *     .task(new GetTask.Builder<User>(client)
 *         .url("https://api.example.com/users/123")
 *         .responseType(User.class)
 *         .responseContextKey("currentUser")
 *         .build())
 *     .task(new PutTask.Builder<String>(client)
 *         .url("https://api.example.com/users/123")
 *         .body("{\"name\":\"New Name\"}") // Merge with fetched data
 *         .build())
 *     .build();
 * }</pre>
 *
 * @param <T> the type to deserialize the response to
 * @see AbstractHttpTask
 * @see PostTask
 * @see GetTask
 * @see DeleteTask
 */
public class PutTask<T> extends AbstractHttpTask<T> {
  private final String body;
  private final Map<String, String> formData;

  private PutTask(Builder<T> b) {
    super(b);
    this.body = b.body;
    this.formData = b.formData != null ? Map.copyOf(b.formData) : Map.of();
  }

  @Override
  protected void prepareRequest(HttpRequest.Builder builder, WorkflowContext context) {
    HttpTaskBodyHelper.setBodyWithContentType(
        body, formData, context, builder::PUT, contentType -> setContentType(builder, contentType));
  }

  /**
   * Builder for PutTask with fluent API.
   *
   * @param <T> the type of the response
   */
  public static class Builder<T> extends AbstractHttpTask.Builder<Builder<T>, PutTask<T>, T> {
    private String body;
    private Map<String, String> formData;

    /**
     * Create a new PutTask.Builder.
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
    public PutTask<T> build() {
      HttpTaskBodyHelper.validateFormData(formData);
      return new PutTask<>(this);
    }
  }
}
