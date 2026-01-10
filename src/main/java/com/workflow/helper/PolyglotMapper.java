package com.workflow.helper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.graalvm.polyglot.Value;

/**
 * Utility class for converting GraalVM Polyglot {@link Value} objects to native Java types. This
 * mapper ensures that JavaScript values returned from scripts are properly converted to Java
 * collections and primitives that remain accessible after the GraalVM context is closed.
 *
 * <p>The conversion process is recursive and handles nested structures, converting JavaScript
 * arrays to Java Lists and JavaScript objects to Java Maps, while preserving primitives in their
 * appropriate Java types.
 *
 * <h3>Type Mapping:</h3>
 *
 * <table border="1">
 * <tr><th>JavaScript Type</th><th>Java Type</th><th>Notes</th></tr>
 * <tr><td>null/undefined</td><td>null</td><td>Both convert to Java null</td></tr>
 * <tr><td>string</td><td>String</td><td>Direct conversion</td></tr>
 * <tr><td>boolean</td><td>Boolean</td><td>Direct conversion</td></tr>
 * <tr><td>number (integer)</td><td>Integer or Long</td><td>Based on size</td></tr>
 * <tr><td>number (decimal)</td><td>Double</td><td>Floating point numbers</td></tr>
 * <tr><td>Array</td><td>List&lt;Object&gt;</td><td>ArrayList, recursively mapped</td></tr>
 * <tr><td>Object</td><td>Map&lt;String, Object&gt;</td><td>LinkedHashMap, preserves order</td></tr>
 * <tr><td>Host Object</td><td>Original Type</td><td>Java objects passed through</td></tr>
 * </table>
 *
 * <h3>Usage Examples:</h3>
 *
 * <pre>{@code
 * // In JavascriptWorkflow's PolyglotContextProxy
 * Value jsResult = jsContext.eval(source);
 * Object javaResult = PolyglotMapper.toJava(jsResult);
 * context.put("result", javaResult);
 * }</pre>
 *
 * <pre>{@code
 * // Converting a JavaScript array
 * Value jsArray = jsContext.eval("js", "[1, 2, 3, 'four']");
 * List<Object> javaList = (List<Object>) PolyglotMapper.toJava(jsArray);
 * // Result: [1, 2, 3, "four"] as ArrayList
 * }</pre>
 *
 * <pre>{@code
 * // Converting a JavaScript object
 * Value jsObject = jsContext.eval("js", "({ name: 'John', age: 30, active: true })");
 * Map<String, Object> javaMap = (Map<String, Object>) PolyglotMapper.toJava(jsObject);
 * // Result: {"name": "John", "age": 30, "active": true} as LinkedHashMap
 * }</pre>
 *
 * <pre>{@code
 * // Converting nested structures
 * Value jsNested = jsContext.eval("js",
 *     "({ users: [{ id: 1, name: 'Alice' }, { id: 2, name: 'Bob' }] })");
 * Map<String, Object> javaMap = (Map<String, Object>) PolyglotMapper.toJava(jsNested);
 * List<Map<String, Object>> users = (List<Map<String, Object>>) javaMap.get("users");
 * // Fully converted to native Java collections
 * }</pre>
 *
 * <h3>Why This Mapper is Needed:</h3>
 *
 * <p>GraalVM Polyglot {@link Value} objects are tied to their execution context. Once the context
 * is closed (which happens automatically in try-with-resources), these Value objects become invalid
 * and cannot be accessed. This mapper converts them to standard Java types that remain valid after
 * context closure.
 *
 * <p>Without conversion:
 *
 * <pre>{@code
 * // BAD: Value becomes invalid after context closes
 * Value jsResult;
 * try (Context ctx = Context.create("js")) {
 *     jsResult = ctx.eval("js", "({ data: [1, 2, 3] })");
 * } // Context closed here
 * jsResult.getMember("data"); // FAILS! Context is closed
 * }</pre>
 *
 * <p>With conversion:
 *
 * <pre>{@code
 * // GOOD: Converted to Java types that remain valid
 * Map<String, Object> result;
 * try (Context ctx = Context.create("js")) {
 *     Value jsResult = ctx.eval("js", "({ data: [1, 2, 3] })");
 *     result = (Map<String, Object>) PolyglotMapper.toJava(jsResult);
 * } // Context closed here
 * List<Object> data = (List<Object>) result.get("data"); // Works fine!
 * }</pre>
 *
 * <h3>Performance Considerations:</h3>
 *
 * <ul>
 *   <li>Conversion is recursive and creates new collections for arrays/objects
 *   <li>Primitives are converted efficiently
 *   <li>Deep nested structures incur proportional overhead
 *   <li>Conversion happens once, avoiding repeated context access
 * </ul>
 *
 * @see Value
 * @see com.workflow.JavascriptWorkflow
 * @see com.workflow.JavascriptWorkflow.PolyglotContextProxy
 */
@UtilityClass
public class PolyglotMapper {
  /**
   * Deeply converts a Polyglot {@link Value} into native Java collections (Map/List/Primitives).
   * This ensures the data remains accessible after the GraalVM Context is closed.
   *
   * <p>The conversion process examines the Polyglot value and recursively converts it according to
   * the type mapping rules defined in this class.
   *
   * @param value the GraalVM Polyglot value to convert
   * @return the converted Java object: null, Boolean, Number (Integer/Long/Double), String,
   *     List&lt;Object&gt;, or Map&lt;String, Object&gt;
   */
  public static Object toJava(Value value) {
    if (value == null || value.isNull()) return null;

    if (value.isHostObject()) return value.asHostObject();

    if (value.hasArrayElements()) return toList(value);

    if (value.hasMembers()) return toMap(value);

    if (value.isString()) return value.asString();

    if (value.isBoolean()) return value.asBoolean();

    if (value.isNumber()) return toNumber(value);

    return value.as(Object.class);
  }

  private static Map<String, Object> toMap(Value value) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (String key : value.getMemberKeys()) {
      map.put(key, toJava(value.getMember(key)));
    }
    return map;
  }

  private static List<Object> toList(Value value) {
    List<Object> list = new ArrayList<>();
    for (int i = 0; i < value.getArraySize(); i++) {
      list.add(toJava(value.getArrayElement(i)));
    }
    return list;
  }

  private static Object toNumber(Value value) {
    if (value.fitsInInt()) return value.asInt();
    if (value.fitsInLong()) return value.asLong();
    return value.asDouble();
  }
}
