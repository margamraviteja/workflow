package com.workflow.helper;

import lombok.experimental.UtilityClass;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Utility class for JSON serialization and deserialization using Jackson.
 *
 * <p><b>Purpose:</b> Provides simple, reusable JSON conversion utilities for workflow tasks that
 * need to transform between JSON strings and Java objects.
 *
 * <p><b>Configuration:</b>
 *
 * <ul>
 *   <li><b>Failure Mode:</b> Does NOT fail on unknown properties (lenient deserialization)
 *   <li><b>Null Handling:</b> Returns null for null/empty input strings
 *   <li><b>Mapper:</b> Shared Jackson JsonMapper instance for efficiency
 * </ul>
 *
 * <p><b>Thread Safety:</b> This utility class is thread-safe. The shared JsonMapper is thread-safe.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Deserialize HTTP responses to Java objects
 *   <li>Serialize Java objects for HTTP requests
 *   <li>Transform JSON strings in workflows
 *   <li>Parse configuration files
 *   <li>Handle JSON data in JavaScript tasks
 * </ul>
 *
 * <p><b>Example Usage - JSON to Object:</b>
 *
 * <pre>{@code
 * String jsonStr = "{\"name\":\"John\",\"age\":30,\"email\":\"john@example.com\"}";
 * User user = JsonUtils.fromJson(jsonStr, User.class);
 * System.out.println("User: " + user.getName());
 * }</pre>
 *
 * <p><b>Example Usage - Object to JSON:</b>
 *
 * <pre>{@code
 * User user = new User("Jane", 25, "jane@example.com");
 * String jsonStr = JsonUtils.toJson(user);
 * System.out.println("JSON: " + jsonStr);  // {"name":"Jane","age":25,"email":"jane@example.com"}
 * }</pre>
 *
 * <p><b>Example Usage - In Task:</b>
 *
 * <pre>{@code
 * public class JsonTransformTask extends AbstractTask {
 *     @Override
 *     protected void doExecute(WorkflowContext context) throws TaskExecutionException {
 *         String rawJson = context.getTyped("rawJsonData", String.class);
 *         try {
 *             User user = JsonUtils.fromJson(rawJson, User.class);
 *             user.setProcessed(true);
 *             String updatedJson = JsonUtils.toJson(user);
 *             context.put("processedJson", updatedJson);
 *         } catch (IOException e) {
 *             throw new TaskExecutionException("JSON transformation failed", e);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Unknown Properties Handling:</b> The configured Jackson mapper does not fail when JSON
 * contains unknown properties. This allows parsing of new API versions that add fields.
 *
 * @see JsonMapper
 */
@UtilityClass
public final class JsonUtils {
  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .build();

  public static <T> T fromJson(String json, Class<T> clazz) throws JacksonException {
    if (json == null || json.isEmpty()) return null;
    return MAPPER.readValue(json, clazz);
  }

  public static <T> T fromJson(String json, TypeReference<T> typeRef) throws JacksonException {
    if (json == null || json.isEmpty()) return null;
    return MAPPER.readValue(json, typeRef);
  }

  public static String toJson(Object obj) throws JacksonException {
    return obj == null ? null : MAPPER.writeValueAsString(obj);
  }
}
