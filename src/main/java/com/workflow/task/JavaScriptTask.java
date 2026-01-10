package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * Executes JavaScript code and stores the result in the workflow context using GraalVM JavaScript
 * engine.
 *
 * <p><b>Purpose:</b> Enables dynamic JavaScript execution within workflows. Useful for
 * transformations, calculations, and data manipulations that are easier to express in JavaScript
 * than Java. Supports GraalVM polyglot language interoperability.
 *
 * <p><b>Script Sources:</b>
 *
 * <ul>
 *   <li><b>scriptFile:</b> Read and execute from file (takes precedence)
 *   <li><b>scriptText:</b> Execute directly from string
 *   <li>Must provide at least one
 * </ul>
 *
 * <p><b>Data Flow:</b>
 *
 * <ol>
 *   <li>Read input values from context using inputBindings keys
 *   <li>Bind context values to JavaScript variables using inputBindings values
 *   <li>Execute JavaScript script
 *   <li>Capture result of last expression
 *   <li>Store result based on output mode (single key or unpack multiple values)
 * </ol>
 *
 * <p><b>Input/Output Binding:</b>
 *
 * <ul>
 *   <li><b>inputBindings:</b> Map of context-key → js-variable-name
 *   <li>Example: Map.of("userInput", "user", "configInput", "config") makes context values
 *       available as 'user' and 'config' in JavaScript
 *   <li><b>outputKey:</b> Where to store the final JavaScript result (used when unpackResult=false)
 *   <li><b>unpackResult:</b> If true, unpacks JavaScript object properties into context as multiple
 *       key-value pairs
 * </ul>
 *
 * <p><b>Output Modes:</b>
 *
 * <ul>
 *   <li><b>Single Key Mode</b> (unpackResult=false, outputKey provided): Store entire result at
 *       outputKey
 *   <li><b>Unpack Mode</b> (unpackResult=true): Extract all properties from returned JavaScript
 *       object and add them to context as separate entries
 * </ul>
 *
 * <p><b>Thread Safety:</b> GraalVM Context objects are not thread-safe. If this task is used in
 * parallel workflows, provide a new Context per invocation or synchronize access.
 *
 * <p><b>Language Features:</b>
 *
 * <ul>
 *   <li>ECMAScript 2021 compatible
 *   <li>Access to Java objects (with allowAllAccess=true)
 *   <li>Module system support
 *   <li>Promise and async/await support
 * </ul>
 *
 * <p><b>Exception Handling:</b> JavaScript runtime errors are wrapped in TaskExecutionException.
 * Syntax errors are caught at parse time.
 *
 * <p><b>Use Cases:</b>
 *
 * <ul>
 *   <li>Data transformations
 *   <li>Complex calculations
 *   <li>JSON manipulation
 *   <li>Template evaluation
 *   <li>Custom business logic
 *   <li>Returning multiple computed values
 * </ul>
 *
 * <p><b>Example Usage - Simple Calculation:</b>
 *
 * <pre>{@code
 * WorkflowContext context = new WorkflowContext();
 * context.put("a", 10);
 * context.put("b", 20);
 *
 * JavaScriptTask calcTask = JavaScriptTask.builder()
 *     .scriptText("var result = a + b; result;")
 *     .inputBindings(Map.of("a", "a", "b", "b"))
 *     .outputKey("sum")
 *     .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
 *     .build();
 *
 * calcTask.execute(context);
 * Integer sum = context.getTyped("sum", Integer.class);
 * System.out.println("Sum: " + sum);  // Output: Sum: 30
 * }</pre>
 *
 * <p><b>Example Usage - Return Multiple Values (Unpack Mode):</b>
 *
 * <pre>{@code
 * WorkflowContext context = new WorkflowContext();
 * context.put("price", 100);
 * context.put("quantity", 5);
 *
 * JavaScriptTask calcTask = JavaScriptTask.builder()
 *     .scriptText(
 *         "({ " +
 *         "  subtotal: price * quantity, " +
 *         "  tax: price * quantity * 0.1, " +
 *         "  total: price * quantity * 1.1, " +
 *         "  discount: price > 100 ? 10 : 0 " +
 *         "})"
 *     )
 *     .inputBindings(Map.of("price", "price", "quantity", "quantity"))
 *     .unpackResult(true)  // Unpack all properties into context
 *     .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
 *     .build();
 *
 * calcTask.execute(context);
 *
 * // All properties are now in context
 * System.out.println("Subtotal: " + context.get("subtotal"));  // 500
 * System.out.println("Tax: " + context.get("tax"));            // 50.0
 * System.out.println("Total: " + context.get("total"));        // 550.0
 * System.out.println("Discount: " + context.get("discount"));  // 10
 * }</pre>
 *
 * <p><b>Example Usage - Complex Data Processing:</b>
 *
 * <pre>{@code
 * WorkflowContext context = new WorkflowContext();
 * context.put("orders", List.of(
 *     Map.of("id", 1, "amount", 100, "status", "paid"),
 *     Map.of("id", 2, "amount", 200, "status", "pending"),
 *     Map.of("id", 3, "amount", 150, "status", "paid")
 * ));
 *
 * JavaScriptTask analyzeTask = JavaScriptTask.builder()
 *     .scriptText(
 *         "const paid = orders.filter(o => o.status === 'paid');" +
 *         "const pending = orders.filter(o => o.status === 'pending');" +
 *         "({ " +
 *         "  paidCount: paid.length, " +
 *         "  paidTotal: paid.reduce((sum, o) => sum + o.amount, 0), " +
 *         "  pendingCount: pending.length, " +
 *         "  pendingTotal: pending.reduce((sum, o) => sum + o.amount, 0), " +
 *         "  totalOrders: orders.length " +
 *         "})"
 *     )
 *     .inputBindings(Map.of("orders", "orders"))
 *     .unpackResult(true)
 *     .polyglot(Context.newBuilder("js").allowAllAccess(true).build())
 *     .build();
 *
 * analyzeTask.execute(context);
 *
 * System.out.println("Paid orders: " + context.get("paidCount"));        // 2
 * System.out.println("Paid total: " + context.get("paidTotal"));         // 250
 * System.out.println("Pending orders: " + context.get("pendingCount"));  // 1
 * System.out.println("Pending total: " + context.get("pendingTotal"));   // 200
 * }</pre>
 *
 * <p><b>Example Usage - Data Transformation:</b>
 *
 * <pre>{@code
 * WorkflowContext context = new WorkflowContext();
 * context.put("inputData", "{\"name\":\"John\",\"age\":30}");
 *
 * JavaScriptTask transformTask = JavaScriptTask.builder()
 *     .scriptText(
 *         "var obj = JSON.parse(inputData);" +
 *         "obj.age = obj.age + 1;" +
 *         "JSON.stringify(obj);"
 *     )
 *     .inputBindings(Map.of("inputData", "inputData"))
 *     .outputKey("transformedData")
 *     .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
 *     .build();
 *
 * transformTask.execute(context);
 * String result = context.getTyped("transformedData", String.class);
 * System.out.println("Transformed: " + result);
 * }</pre>
 *
 * <p><b>Example Usage - Script from File:</b>
 *
 * <pre>{@code
 * JavaScriptTask fileTask = JavaScriptTask.builder()
 *     .scriptFile(Path.of("scripts/transform.js"))
 *     .inputBindings(Map.of("input", "data"))
 *     .unpackResult(true)
 *     .polyglot(Context.newBuilder("js").allowAllAccess(true).build())
 *     .build();
 *
 * // Content of scripts/transform.js:
 * // function processData(data) {
 * //     return {
 * //         processedData: data.toUpperCase(),
 * //         timestamp: new Date().toISOString(),
 * //         length: data.length
 * //     };
 * // }
 * // processData(data);
 * }</pre>
 *
 * <p><b>Example Usage - In Sequential Workflow:</b>
 *
 * <pre>{@code
 * SequentialWorkflow workflow = SequentialWorkflow.builder()
 *     .name("DataProcessing")
 *     .task(new FileReadTask(Path.of("data.json"), "rawData"))
 *     .task(JavaScriptTask.builder()
 *         .scriptText("JSON.parse(rawData)")
 *         .inputBindings(Map.of("rawData", "rawData"))
 *         .outputKey("parsedData")
 *         .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
 *         .build())
 *     .task(JavaScriptTask.builder()
 *         .scriptText(
 *             "const active = parsedData.filter(x => x.active);" +
 *             "({ " +
 *             "  activeIds: active.map(x => x.id), " +
 *             "  activeCount: active.length, " +
 *             "  totalCount: parsedData.length " +
 *             "})"
 *         )
 *         .inputBindings(Map.of("parsedData", "parsedData"))
 *         .unpackResult(true)
 *         .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
 *         .build())
 *     .build();
 * }</pre>
 *
 * <p><b>Performance Notes:</b>
 *
 * <ul>
 *   <li>First execution loads and compiles the JavaScript engine (startup cost)
 *   <li>GraalVM can compile JavaScript to native code for better performance
 *   <li>Reuse Context objects for better performance
 * </ul>
 *
 * @see Context (GraalVM polyglot Context)
 * @see Task
 * @see AbstractTask
 * @see com.workflow.JavascriptWorkflow
 */
@Slf4j
@Builder
public class JavaScriptTask extends AbstractTask {
  private final String scriptText;
  private final Path scriptFile;
  private final Map<String, String> inputBindings;
  private final String outputKey;

  /**
   * If true, unpacks the returned JavaScript object's properties into the workflow context as
   * separate key-value pairs. If false, stores the entire result at the outputKey location.
   *
   * <p>When unpackResult=true:
   *
   * <ul>
   *   <li>The JavaScript must return an object (not a primitive)
   *   <li>Each property of the object becomes a context entry
   *   <li>outputKey is ignored (must not be set)
   * </ul>
   *
   * <p>When unpackResult=false (default):
   *
   * <ul>
   *   <li>The entire result is stored at outputKey
   *   <li>outputKey must be provided
   * </ul>
   *
   * <p>Default: false
   */
  @Builder.Default private final boolean unpackResult = false;

  private final Context polyglot;

  @Override
  protected void doExecute(WorkflowContext workflowContext) throws TaskExecutionException {
    // Validate configuration
    validateConfiguration();

    String script;
    try {
      if (scriptFile != null) {
        script = Files.readString(scriptFile);
      } else if (scriptText != null) {
        script = scriptText;
      } else {
        throw new TaskExecutionException("No script provided");
      }
    } catch (IOException e) {
      throw new TaskExecutionException("Failed to read script file", e);
    }

    // Create a GraalVM JS workflowContext with restricted access
    try {
      // Bind inputs: map each workflowContext value into JS global variable
      if (inputBindings != null) {
        for (Map.Entry<String, String> e : inputBindings.entrySet()) {
          Object value = workflowContext.get(e.getKey());
          // putMember accepts Java objects; graal will convert primitives and POJOs where possible
          polyglot.getBindings("js").putMember(e.getValue(), value);
        }
      }

      Value result = polyglot.eval("js", script);

      if (unpackResult) {
        unpackResultIntoContext(result, workflowContext);
      } else {
        Object javaResult = convertToJavaObject(result);
        workflowContext.put(outputKey, javaResult);
      }
    } catch (Exception ex) {
      throw new TaskExecutionException("JavaScript execution failed: " + ex.getMessage(), ex);
    }
  }

  /**
   * Validates the task configuration before execution.
   *
   * @throws TaskExecutionException if configuration is invalid
   */
  private void validateConfiguration() throws TaskExecutionException {
    if (unpackResult && outputKey != null) {
      throw new TaskExecutionException(
          "Cannot specify both unpackResult=true and outputKey. "
              + "When unpacking results, outputKey should be null.");
    }

    if (!unpackResult && outputKey == null) {
      throw new TaskExecutionException(
          "outputKey must be provided when unpackResult=false (default behavior)");
    }
  }

  /**
   * Unpacks a JavaScript object's properties into the workflow context as separate key-value pairs.
   *
   * @param result the JavaScript result value
   * @param workflowContext the workflow context to update
   * @throws TaskExecutionException if result is not an object or cannot be unpacked
   */
  private void unpackResultIntoContext(Value result, WorkflowContext workflowContext)
      throws TaskExecutionException {
    if (result == null || result.isNull()) {
      log.warn("JavaScript returned null/undefined when unpackResult=true, no values to unpack");
      return;
    }

    if (!result.hasMembers()) {
      throw new TaskExecutionException(
          "JavaScript result must be an object when unpackResult=true. "
              + "Got: "
              + result.getClass().getSimpleName()
              + ". "
              + "Example: return { key1: value1, key2: value2 };");
    }

    // Extract all members (properties) from the JavaScript object
    int unpackedCount = 0;
    for (String memberKey : result.getMemberKeys()) {
      Value memberValue = result.getMember(memberKey);
      Object javaValue = convertToJavaObject(memberValue);
      if (javaValue != null) {
        workflowContext.put(memberKey, javaValue);
      }
      unpackedCount++;
      log.debug("Unpacked: {} = {}", memberKey, javaValue);
    }

    log.debug(
        "Unpacked {} key-value pairs from JavaScript result into workflow context", unpackedCount);
  }

  /**
   * Converts a GraalVM Value to an appropriate Java object.
   *
   * @param value the GraalVM value to convert
   * @return the converted Java object
   */
  private static Object convertToJavaObject(Value value) {
    // Convert result to a Java object where possible
    if (value == null || value.isNull()) {
      return null;
    } else if (value.isBoolean()) {
      return value.asBoolean();
    } else if (value.isNumber()) {
      return convertToNumber(value);
    } else if (value.isString()) {
      return value.asString();
    } else if (value.hasArrayElements()) {
      // Handle arrays - convert to Java List
      return converToList(value);
    } else if (value.hasMembers()) {
      // Handle objects - convert to Java Map
      return convertToMap(value);
    } else {
      // fallback: expose Value itself so callers can inspect it if needed
      return value;
    }
  }

  private static Map<String, Object> convertToMap(Value value) {
    Map<String, Object> map = new HashMap<>();
    for (String key : value.getMemberKeys()) {
      Value member = value.getMember(key);
      map.put(key, convertToJavaObject(member));
    }
    return map;
  }

  private static List<Object> converToList(Value value) {
    long size = value.getArraySize();
    List<Object> list = new ArrayList<>((int) size);
    for (long i = 0; i < size; i++) {
      Value element = value.getArrayElement(i);
      list.add(convertToJavaObject(element));
    }
    return list;
  }

  private static Object convertToNumber(Value value) {
    double d = value.asDouble();
    if (d == Math.rint(d) && !Double.isInfinite(d)) {
      // Return as long if it's a whole number
      return (long) d;
    } else {
      return d;
    }
  }
}
