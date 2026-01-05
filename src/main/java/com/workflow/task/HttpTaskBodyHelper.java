package com.workflow.task;

import com.workflow.context.WorkflowContext;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;

/**
 * Helper utility class for HTTP task body handling. Provides reusable logic for encoding form data,
 * handling body precedence (explicit body > context body > form data > empty), and managing
 * content-type headers.
 *
 * <p>This class eliminates duplication across PostTask, PutTask, and DeleteTask by centralizing
 * common body preparation logic.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * HttpRequest.Builder builder = HttpRequest.newBuilder();
 * String body = "...";
 * Map<String, String> formData = ...;
 *
 * HttpTaskBodyHelper.setBodyWithContentType(
 *     builder,
 *     body,
 *     formData,
 *     context,
 *     method -> builder.POST(method),
 *     contentType -> task.setContentType(builder, contentType)
 * );
 * }</pre>
 */
@UtilityClass
public class HttpTaskBodyHelper {

  /**
   * Prepares the HTTP request body with appropriate content-type header based on body precedence.
   *
   * <p>Precedence order (first non-null/empty wins):
   *
   * <ol>
   *   <li>Explicit body parameter (if non-null)
   *   <li>Context-provided REQUEST_BODY (if present in context)
   *   <li>Form data (if non-empty)
   *   <li>Empty body
   * </ol>
   *
   * @param explicitBody explicit body String (maybe null)
   * @param formData form data Map (maybe null or empty)
   * @param context WorkflowContext for dynamic body lookup (maybe null)
   * @param methodSetter consumer that sets the HTTP method and body (e.g., builder::POST)
   * @param contentTypeSetter consumer that sets content-type header
   */
  public static void setBodyWithContentType(
      String explicitBody,
      Map<String, String> formData,
      WorkflowContext context,
      Consumer<HttpRequest.BodyPublisher> methodSetter,
      Consumer<String> contentTypeSetter) {

    if (explicitBody != null) {
      // Explicit body takes highest precedence
      contentTypeSetter.accept("application/json; charset=UTF-8");
      methodSetter.accept(BodyPublishers.ofString(explicitBody, StandardCharsets.UTF_8));
    } else if (context != null && context.containsKey(AbstractHttpTask.REQUEST_BODY)) {
      // Context-provided body is second priority
      Object requestBody = context.get(AbstractHttpTask.REQUEST_BODY);
      contentTypeSetter.accept("application/json; charset=UTF-8");
      methodSetter.accept(
          BodyPublishers.ofString(
              requestBody == null ? "" : requestBody.toString(), StandardCharsets.UTF_8));
    } else if (formData != null && !formData.isEmpty()) {
      // Form data is third priority
      contentTypeSetter.accept("application/x-www-form-urlencoded; charset=UTF-8");
      methodSetter.accept(BodyPublishers.ofString(encodeForm(formData), StandardCharsets.UTF_8));
    } else {
      // Empty body as fallback
      methodSetter.accept(BodyPublishers.noBody());
    }
  }

  /**
   * Encodes a map of form data into URL-encoded format (application/x-www-form-urlencoded).
   *
   * <p>Example: {name: "John", age: "30"} -&gt; "name=John&amp;age=30"
   *
   * @param formData the form data map to encode
   * @return the encoded form data string
   * @throws IllegalArgumentException if any key or value is invalid
   */
  public static String encodeForm(Map<String, String> formData) {
    if (formData == null || formData.isEmpty()) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : formData.entrySet()) {
      if (!first) {
        sb.append("&");
      }
      first = false;
      sb.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
    }
    return sb.toString();
  }

  /**
   * URL-encodes a string for use in query parameters or form data.
   *
   * @param value the string to encode
   * @return the URL-encoded string (spaces encoded as %20, not +)
   */
  public static String encode(String value) {
    String encoded = URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    // URLEncoder encodes spaces as '+', but we want '%20' for query params
    return encoded.replace("+", "%20");
  }

  /**
   * Validates form data for null or blank keys/values.
   *
   * @param formData the form data map to validate
   * @throws IllegalArgumentException if any key is null/blank or any value is null
   */
  public static void validateFormData(Map<String, String> formData) {
    if (formData == null || formData.isEmpty()) {
      return;
    }

    for (Map.Entry<String, String> entry : formData.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();

      if (key == null || key.isBlank()) {
        throw new IllegalArgumentException("Form key cannot be null or blank");
      }
      if (value == null) {
        throw new IllegalArgumentException("Form value cannot be null for key: " + key);
      }
    }
  }
}
