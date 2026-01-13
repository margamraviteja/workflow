package com.workflow.task;

import com.workflow.context.WorkflowContext;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

/**
 * HTTP GET task implementation that extends {@link AbstractHttpTask}. Performs HTTP GET requests
 * with automatic Accept header handling and response mapping.
 *
 * <p><b>Automatic Behavior:</b> If no Accept header is explicitly provided, defaults to
 * "application/json".
 *
 * <p><b>Response Handling:</b>
 *
 * <ul>
 *   <li>Responses are deserialized using provided {@link Builder#responseType(Class)} if specified
 *   <li>Otherwise responses are returned as raw Strings
 *   <li>Mapped response is stored in context under key specified by {@link
 *       AbstractHttpTask.Builder#responseContextKey(String)} (default: "httpResponse")
 * </ul>
 *
 * <p><b>Context Usage:</b>
 *
 * <ul>
 *   <li><b>Inputs:</b> Optional "queryParams" map for dynamic query parameters
 *   <li><b>Inputs:</b> Optional URL from context if using {@link Builder#urlFromContext(String)}
 *   <li><b>Outputs:</b> Response stored under configured response context key
 * </ul>
 *
 * <p><b>Example usage - Simple GET:</b>
 *
 * <pre>{@code
 * HttpClient client = HttpClient.newHttpClient();
 * WorkflowContext context = new WorkflowContext();
 *
 * GetHttpTask<String> task = new GetHttpTask.Builder<String>(client)
 *     .url("https://api.example.com/users")
 *     .build();
 *
 * task.execute(context);
 * String response = context.getTyped("httpResponse", String.class);
 * }</pre>
 *
 * <p><b>Example usage - Typed response:</b>
 *
 * <pre>{@code
 * GetHttpTask<User> task = new GetHttpTask.Builder<User>(client)
 *     .url("https://api.example.com/users/123")
 *     .responseType(User.class)
 *     .header("Authorization", "Bearer token")
 *     .build();
 *
 * task.execute(context);
 * User user = context.getTyped("httpResponse", User.class);
 * }</pre>
 *
 * <p><b>Example usage - Dynamic URL and query params:</b>
 *
 * <pre>{@code
 * context.put("apiUrl", "https://api.example.com/search");
 * context.put("queryParams", Map.of("q", "java", "limit", "10"));
 *
 * GetHttpTask<String> task = new GetHttpTask.Builder<String>(client)
 *     .urlFromContext("apiUrl")
 *     .build();
 *
 * task.execute(context);
 * }</pre>
 *
 * @param <T> the type of the deserialized response
 * @see AbstractHttpTask
 * @see PostHttpTask
 * @see PutHttpTask
 * @see DeleteHttpTask
 */
public class GetHttpTask<T> extends AbstractHttpTask<T> {

  private GetHttpTask(Builder<T> b) {
    super(b);
  }

  @Override
  protected void prepareRequest(HttpRequest.Builder builder, WorkflowContext context) {
    builder.GET();
    boolean hasAccept =
        headers.keySet().stream().anyMatch(k -> k != null && k.equalsIgnoreCase(ACCEPT));
    if (!hasAccept) {
      builder.header(ACCEPT, "application/json");
    }
  }

  public static class Builder<T> extends AbstractHttpTask.Builder<Builder<T>, GetHttpTask<T>, T> {
    public Builder(HttpClient httpClient) {
      super(httpClient);
    }

    @Override
    protected Builder<T> self() {
      return this;
    }

    @Override
    public GetHttpTask<T> build() {
      return new GetHttpTask<>(this);
    }
  }
}
