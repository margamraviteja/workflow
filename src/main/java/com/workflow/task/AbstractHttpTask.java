package com.workflow.task;

import static com.workflow.helper.ResponseMappers.defaultResponseMapper;
import static com.workflow.helper.ResponseMappers.defaultTypedResponseMapper;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.helper.HttpTaskBodyHelper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Abstract base class for HTTP tasks. Extends {@link AbstractTask} to provide common HTTP
 * functionality including:
 *
 * <ul>
 *   <li>URL resolution (static or from context)
 *   <li>Header management with automatic defaults
 *   <li>Query parameter handling (static or from context, with automatic merging)
 *   <li>HTTP request customization hooks
 *   <li>Response mapping and storage
 *   <li>Timeout configuration
 * </ul>
 *
 * <p>Concrete implementations (GetHttpTask, PostHttpTask, PutHttpTask, DeleteHttpTask) must
 * implement {@link #prepareRequest(HttpRequest.Builder, WorkflowContext)} to set the HTTP method
 * and body as needed.
 *
 * <p><b>Request Processing Flow:</b>
 *
 * <ol>
 *   <li>Resolve URL (static or from context)
 *   <li>Merge query parameters (builder + context)
 *   <li>Apply headers (with custom defaults like Accept: application/json)
 *   <li>Prepare method-specific body via {@link #prepareRequest(HttpRequest.Builder,
 *       WorkflowContext)}
 *   <li>Apply custom request customizer if provided
 *   <li>Send HTTP request
 *   <li>Map response using provided mapper or default Jackson mapper
 *   <li>Store mapped response in context
 * </ol>
 *
 * <p><b>Context Keys:</b>
 *
 * <ul>
 *   <li>{@code queryParams} (Map&lt;String,String&gt;): Additional query params from context
 *   <li>{@code requestBody} (Object): Dynamic request body for POST/PUT/DELETE
 *   <li>Response stored at key specified by {@link Builder#responseContextKey(String)} (default:
 *       "httpResponse")
 * </ul>
 *
 * @param <T> response mapped type
 * @see GetHttpTask
 * @see PostHttpTask
 * @see PutHttpTask
 * @see DeleteHttpTask
 * @see HttpTaskBodyHelper
 * @see com.workflow.helper.HttpResponseWrapper
 * @see com.workflow.helper.ResponseMappers
 */
public abstract class AbstractHttpTask<T> extends AbstractTask {
  public static final String ACCEPT = "Accept";
  public static final String CONTENT_TYPE = "Content-Type";

  public static final String QUERY_PARAMS = "queryParams";
  public static final String REQUEST_BODY = "requestBody";
  public static final String DEFAULT_HTTP_RESPONSE_KEY = "httpResponse";

  protected final HttpClient httpClient;
  protected final Duration timeout;

  protected final String url;
  protected final String urlContextKey;

  protected final Map<String, String> headers;
  protected final Map<String, String> queryParams;

  protected final BiConsumer<HttpRequest.Builder, WorkflowContext> requestCustomizer;
  protected final Function<HttpResponse<String>, T> responseMapper;
  protected final String responseContextKey;

  protected AbstractHttpTask(Builder<?, ?, T> builder) {
    this.httpClient = Objects.requireNonNull(builder.httpClient, "httpClient is required");
    this.url = builder.url;
    this.urlContextKey = builder.urlContextKey;
    this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
    this.queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(builder.queryParams));
    this.timeout = builder.timeout;

    this.requestCustomizer = builder.requestCustomizer;
    this.responseContextKey =
        builder.responseContextKey != null ? builder.responseContextKey : DEFAULT_HTTP_RESPONSE_KEY;

    if (builder.responseMapper != null) {
      this.responseMapper = builder.responseMapper;
    } else if (builder.responseType != null) {
      this.responseMapper = defaultTypedResponseMapper(builder.responseType);
    } else {
      this.responseMapper = defaultResponseMapper();
    }
  }

  @Override
  protected void doExecute(WorkflowContext context) throws TaskExecutionException {
    String resolvedUrl = resolveUrl(context);
    if (resolvedUrl == null || resolvedUrl.isBlank()) {
      throw new TaskExecutionException("No URL provided for HTTP task");
    }

    try {
      URI uri = buildUriWithQuery(resolvedUrl, mergeQueryParamsFromContext(context));
      HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(uri).timeout(timeout);
      headers.forEach(
          (k, v) -> {
            if (k == null || k.isBlank()) {
              throw new IllegalArgumentException("Header name cannot be null or blank");
            }
            if (v == null) {
              throw new IllegalArgumentException("Header value for " + k + " cannot be null");
            }
            reqBuilder.header(k, v);
          });
      prepareRequest(reqBuilder, context);
      if (requestCustomizer != null) {
        requestCustomizer.accept(reqBuilder, context);
      }
      HttpRequest request = reqBuilder.build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      T mapped = responseMapper.apply(response);
      if (mapped != null) {
        context.put(getResponseContextKey(), mapped);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TaskExecutionException("HTTP request failed: " + e.getMessage(), e);
    } catch (TaskExecutionException e) {
      throw e; // propagate as is
    } catch (Exception e) {
      if (e.getCause() instanceof TaskExecutionException taskExecutionException) {
        throw taskExecutionException;
      }
      // Otherwise wrap into TaskExecutionException for consistent handling
      throw new TaskExecutionException("HTTP request failed: " + e.getMessage(), e);
    }
  }

  /**
   * Prepare the HttpRequest.Builder with method and body as needed.
   *
   * @param builder HttpRequest.Builder to prepare
   * @param context WorkflowContext for dynamic data
   */
  protected abstract void prepareRequest(HttpRequest.Builder builder, WorkflowContext context);

  protected String getResponseContextKey() {
    return responseContextKey;
  }

  protected String resolveUrl(WorkflowContext context) {
    if (url != null && !url.isBlank()) return url;
    if (urlContextKey != null && context != null && context.containsKey(urlContextKey)) {
      Object v = context.get(urlContextKey);
      return v != null ? v.toString() : null;
    }
    return null;
  }

  protected Map<String, String> mergeQueryParamsFromContext(WorkflowContext context) {
    Map<String, String> merged = new LinkedHashMap<>(queryParams);
    if (context != null && context.containsKey(QUERY_PARAMS)) {
      Object obj = context.get(QUERY_PARAMS);
      if (obj instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, String> ctxMap = (Map<String, String>) obj;
        ctxMap.forEach(merged::putIfAbsent);
      }
    }
    return merged;
  }

  protected URI buildUriWithQuery(String baseUrl, Map<String, String> queryParams) {
    if (queryParams == null || queryParams.isEmpty()) {
      return URI.create(baseUrl);
    }
    StringBuilder sb = new StringBuilder(baseUrl);
    String sep = baseUrl.contains("?") ? "&" : "?";
    sb.append(sep);
    boolean first = true;
    for (Map.Entry<String, String> e : queryParams.entrySet()) {
      if (!first) sb.append("&");
      first = false;
      sb.append(encode(e.getKey())).append("=").append(encode(e.getValue()));
    }
    return URI.create(sb.toString());
  }

  protected static String encode(String s) {
    return HttpTaskBodyHelper.encode(s);
  }

  protected void setContentType(HttpRequest.Builder builder, String contentType) {
    boolean hasContentType =
        headers.keySet().stream().anyMatch(k -> k != null && k.equalsIgnoreCase(CONTENT_TYPE));
    if (!hasContentType) {
      builder.header(CONTENT_TYPE, contentType);
    }
  }

  /**
   * Generic builder used by concrete task builders.
   *
   * @param <B> builder self type
   * @param <R> concrete task type
   * @param <T> response mapping type
   */
  protected abstract static class Builder<
      B extends Builder<B, R, T>, R extends AbstractHttpTask<T>, T> {
    private final HttpClient httpClient;
    private String url;
    private String urlContextKey;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, String> queryParams = new LinkedHashMap<>();
    private Duration timeout = Duration.ofSeconds(30);
    private Function<HttpResponse<String>, T> responseMapper;
    private Class<T> responseType; // optional typed response target
    private BiConsumer<HttpRequest.Builder, WorkflowContext> requestCustomizer;
    private String responseContextKey;

    protected Builder(HttpClient httpClient) {
      this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
    }

    public B url(String url) {
      this.url = url;
      return self();
    }

    public B urlFromContext(String contextKey) {
      this.urlContextKey = contextKey;
      return self();
    }

    public B header(String name, String value) {
      this.headers.put(name, value);
      return self();
    }

    public B headers(Map<String, String> headers) {
      this.headers.putAll(headers);
      return self();
    }

    public B queryParam(String name, String value) {
      this.queryParams.put(name, value);
      return self();
    }

    public B queryParams(Map<String, String> params) {
      this.queryParams.putAll(params);
      return self();
    }

    public B timeout(Duration timeout) {
      this.timeout = timeout;
      return self();
    }

    public B responseMapper(Function<HttpResponse<String>, T> mapper) {
      this.responseMapper = mapper;
      return self();
    }

    /** Provide a Class<T> so the default mapper will deserialize JSON into T. */
    public B responseType(Class<T> responseType) {
      this.responseType = responseType;
      return self();
    }

    public B requestCustomizer(BiConsumer<HttpRequest.Builder, WorkflowContext> customizer) {
      this.requestCustomizer = customizer;
      return self();
    }

    /** Set the context key where the mapped response will be stored. */
    public B responseContextKey(String key) {
      this.responseContextKey = key;
      return self();
    }

    protected abstract B self();

    public abstract R build();
  }
}
