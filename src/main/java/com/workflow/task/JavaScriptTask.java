package com.workflow.task;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import lombok.Builder;
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
 *   <li>Store result in context at outputKey
 * </ol>
 *
 * <p><b>Input/Output Binding:</b>
 *
 * <ul>
 *   <li><b>inputBindings:</b> Map of context-key → js-variable-name
 *   <li>Example: Map.of("userInput", "user", "configInput", "config") makes context values
 *       available as 'user' and 'config' in JavaScript
 *   <li><b>outputKey:</b> Where to store the final JavaScript result
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
 *     .outputKey("output")
 *     .polyglot(Context.newBuilder("js").allowAllAccess(true).build())
 *     .build();
 *
 * // Content of scripts/transform.js:
 * // function processData(data) {
 * //     // Complex processing logic
 * //     return result;
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
 *         .build())
 *     .task(new JavaScriptTask.builder()
 *         .scriptText("parsedData.filter(x => x.active).map(x => x.id)")
 *         .inputBindings(Map.of("parsedData", "parsedData"))
 *         .outputKey("activeIds")
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
 */
@Builder
public class JavaScriptTask extends AbstractTask {
  private final String scriptText;
  private final Path scriptFile;
  private final Map<String, String> inputBindings;
  private final String outputKey;
  private final Context polyglot;

  @Override
  protected void doExecute(WorkflowContext workflowContext) throws TaskExecutionException {
    String resultOutputKey = Objects.requireNonNull(outputKey);

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
      Object javaResult = getResult(result);
      workflowContext.put(resultOutputKey, javaResult);
    } catch (Exception ex) {
      throw new TaskExecutionException("JavaScript execution failed: " + ex.getMessage(), ex);
    }
  }

  private static Object getResult(Value result) {
    // Convert result to a Java object where possible
    Object javaResult;
    if (result == null || result.isNull()) {
      javaResult = null;
    } else if (result.isBoolean()) {
      javaResult = result.asBoolean();
    } else if (result.isNumber()) {
      double d = result.asDouble();
      if (d == Math.rint(d)) {
        javaResult = (long) d;
      } else {
        javaResult = d;
      }
    } else if (result.isString()) {
      javaResult = result.asString();
    } else {
      // fallback: expose Value itself so callers can inspect it if needed
      javaResult = result;
    }
    return javaResult;
  }
}
