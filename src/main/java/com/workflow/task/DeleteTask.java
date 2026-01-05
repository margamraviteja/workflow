package com.workflow.task;

import com.workflow.context.WorkflowContext;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

/**
 * HTTP DELETE task that removes resources from a remote server.
 *
 * <p><b>Purpose:</b> Executes HTTP DELETE requests in a workflow. Removes resources from remote
 * servers. DELETE is typically idempotent - deleting a non-existent resource and deleting an
 * existing one should have the same effect.
 *
 * <p><b>Request Body:</b>
 *
 * <ul>
 *   <li><b>Explicit body:</b> Via {@link Builder#body(String)} (highest precedence)
 *   <li><b>Context body:</b> Via "requestBody" key in context (second precedence)
 *   <li><b>No body (default):</b> Standard DELETE without body
 * </ul>
 *
 * <p><b>Note on Form Data:</b> DELETE tasks do not support form-encoded bodies (unlike POST/PUT).
 * Use explicit JSON body if needed for bulk delete operations.
 *
 * <p><b>Context Keys:</b>
 *
 * <ul>
 *   <li><b>Input (optional):</b> "requestBody" for dynamic DELETE body
 *   <li><b>Input (optional):</b> "queryParams" map for dynamic query parameters
 *   <li><b>Input (optional):</b> Dynamic URL via urlFromContext()
 *   <li><b>Output:</b> Response stored under configured response context key
 * </ul>
 *
 * <p><b>Thread Safety:</b> This task is thread-safe if the provided HttpClient is thread-safe.
 *
 * <p><b>Idempotency:</b> DELETE is designed to be idempotent. Repeated DELETE requests should not
 * cause errors if the resource no longer exists.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Delete resources from REST APIs
 *   <li>Remove user accounts or data
 *   <li>Clean up temporary resources
 *   <li>Bulk deletion operations
 *   <li>Cleanup stages in workflows
 * </ul>
 *
 * <p><b>Example Usage - Simple DELETE:</b>
 *
 * <pre>{@code
 * HttpClient client = HttpClient.newHttpClient();
 * DeleteTask<String> deleteTask = new DeleteTask.Builder<String>(client)
 *     .url("https://api.example.com/users/123")
 *     .responseType(String.class)
 *     .build();
 *
 * WorkflowContext context = new WorkflowContext();
 * deleteTask.execute(context);
 * String result = context.getTyped("httpResponse", String.class);
 * System.out.println("Delete result: " + result);
 * }</pre>
 *
 * <p><b>Example Usage - DELETE with JSON Body (Bulk Delete):</b>
 *
 * <pre>{@code
 * DeleteTask<String> bulkDeleteTask = new DeleteTask.Builder<String>(client)
 *     .url("https://api.example.com/users/bulk")
 *     .body("{\"ids\": [1, 2, 3, 4, 5], \"reason\": \"cleanup\"}")
 *     .responseType(String.class)
 *     .responseContextKey("deleteResult")
 *     .build();
 *
 * bulkDeleteTask.execute(context);
 * String result = context.getTyped("deleteResult", String.class);
 * }</pre>
 *
 * <p><b>Example Usage - DELETE with Query Parameters:</b>
 *
 * <pre>{@code
 * DeleteTask<String> deleteWithFilters = new DeleteTask.Builder<String>(client)
 *     .url("https://api.example.com/logs")
 *     .queryParam("olderThan", "30d")
 *     .queryParam("status", "archived")
 *     .responseType(String.class)
 *     .build();
 * }</pre>
 *
 * <p><b>Example Usage - DELETE in Cleanup Workflow:</b>
 *
 * <pre>{@code
 * SequentialWorkflow cleanupWorkflow = SequentialWorkflow.builder()
 *     .name("ResourceCleanup")
 *     .task(new GetTask.Builder<List<Resource>>(client)
 *         .url("https://api.example.com/resources")
 *         .queryParam("status", "defunct")
 *         .responseType(String.class)
 *         .responseContextKey("defunctResources")
 *         .build())
 *     .task(new DeleteTask.Builder<String>(client)
 *         .url("https://api.example.com/resources/batch")
 *         .body("// build from defunctResources")
 *         .responseContextKey("cleanupResult")
 *         .build())
 *     .build();
 * }</pre>
 *
 * <p><b>Example Usage - Idempotent DELETE (Safe to Retry):</b>
 *
 * <pre>{@code
 * // DELETE is idempotent - safe to call multiple times
 * DeleteTask<String> safeDelete = new DeleteTask.Builder<String>(client)
 *     .url("https://api.example.com/temp/" + sessionId)
 *     .responseType(String.class)
 *     .build();
 *
 * // If session already deleted, returns success
 * // If session exists, deletes it and returns success
 * // Both outcomes are acceptable
 * safeDelete.execute(context);
 * }</pre>
 *
 * @param <T> the type to deserialize the response to
 * @see AbstractHttpTask
 * @see PostTask
 * @see PutTask
 * @see GetTask
 */
public class DeleteTask<T> extends AbstractHttpTask<T> {
  private final String body;

  private DeleteTask(Builder<T> b) {
    super(b);
    this.body = b.body;
  }

  @Override
  protected void prepareRequest(HttpRequest.Builder builder, WorkflowContext context) {
    HttpTaskBodyHelper.setBodyWithContentType(
        body,
        null, // DELETE does not support form data
        context,
        bodyPublisher -> builder.method("DELETE", bodyPublisher),
        contentType -> setContentType(builder, contentType));
  }

  /**
   * Builder for DeleteTask with fluent API.
   *
   * @param <T> the type of the response
   */
  public static class Builder<T> extends AbstractHttpTask.Builder<Builder<T>, DeleteTask<T>, T> {
    private String body;

    /**
     * Create a new DeleteTask.Builder.
     *
     * @param httpClient the HttpClient to use for requests (required)
     */
    public Builder(HttpClient httpClient) {
      super(httpClient);
    }

    /**
     * Set an optional explicit JSON request body. Takes precedence over context body. DELETE
     * requests typically don't include a body, but this is supported for API compatibility.
     *
     * @param body the optional body string (maybe null)
     * @return this builder for chaining
     */
    public Builder<T> body(String body) {
      this.body = body;
      return this;
    }

    @Override
    protected Builder<T> self() {
      return this;
    }

    @Override
    public DeleteTask<T> build() {
      return new DeleteTask<>(this);
    }
  }
}
