package com.workflow;

import com.workflow.context.WorkflowContext;
import com.workflow.helper.PolyglotMapper;
import com.workflow.helper.ValidationUtils;
import com.workflow.helper.WorkflowSupport;
import com.workflow.script.ScriptProvider;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.IOAccess;

/**
 * A workflow implementation that executes JavaScript logic using the GraalVM Polyglot API. This
 * class uses a shared engine for performance and retrieves script content via a {@link
 * ScriptProvider}.
 *
 * <p>This class bridges the gap between static Java infrastructure and dynamic business logic. It
 * is particularly useful for scenarios where business rules change frequently and need to be
 * updated without recompiling or redeploying the application.
 *
 * <h3>Key Features:</h3>
 *
 * <ul>
 *   <li><b>Performance:</b> Uses a shared {@link Engine} instance to enable JIT compilation and AST
 *       caching across multiple workflow instances.
 *   <li><b>Security:</b> Executes scripts in a restricted sandbox. Host class lookup is disabled by
 *       default to prevent scripts from accessing {@code java.lang.System} or reflection.
 *   <li><b>Flexibility:</b> Script content is decoupled via {@link ScriptProvider}, allowing logic
 *       to be sourced from the classpath, filesystem, database, or remote URLs.
 * </ul>
 *
 * <h3>Use Cases:</h3>
 *
 * <ul>
 *   <li><b>Dynamic Routing:</b> Determining the next step in a business process based on complex,
 *       user-defined criteria.
 *   <li><b>Data Transformation:</b> Mapping internal domain objects to external API formats using
 *       JS-native JSON handling.
 *   <li><b>Validation Rules:</b> Implementing bespoke validation logic that varies by tenant or
 *       geographic region.
 * </ul>
 *
 * <h3>Example Usages:</h3>
 *
 * <pre>{@code
 * JavascriptWorkflow workflow = JavascriptWorkflow.builder()
 *     .name("CalculateTaxWorkflow")
 *     .scriptProvider(() -> "ctx.put('tax', ctx.get('amount') * 0.15);")
 *      .build();
 * WorkflowResult result = workflow.execute(myContext);
 * result.get("tax");
 * }</pre>
 *
 * <pre>{@code
 * // script loaded from external source (eg: from a file scripts/discount.js)
 * // The 'ctx' object is injected automatically into the global scope
 * var amount = ctx.get("orderTotal");
 * if (amount > 1000) {
 *     ctx.put("discountCode", "VIP_PROMO");
 *     console.log("Applied VIP discount for order");
 * }
 * }</pre>
 *
 * <pre>{@code
 * // Using a ScriptProvider to load script
 * ScriptProvider provider = FileScriptProvider(Path.of("scripts/discount.js"));
 * JavascriptWorkflow workflow = JavascriptWorkflow.builder()
 *     .name("DiscountWorkflow")
 *     .scriptProvider(provider)
 *     .build();
 * WorkflowResult result = workflow.execute(myContext);
 * result.get("discountCode");
 * }</pre>
 *
 * @see ScriptProvider
 * @see AbstractWorkflow
 */
public class JavascriptWorkflow extends AbstractWorkflow implements WorkflowContainer {
  /**
   * Shared engine instance to optimize performance (JIT and AST caching) across all JavaScript
   * workflow executions.
   */
  private static final Engine SHARED_ENGINE = Engine.newBuilder().build();

  private final String name;
  private final ScriptProvider scriptProvider;

  private JavascriptWorkflow(JavascriptWorkflowBuilder builder) {
    this.name = builder.name;
    this.scriptProvider = builder.scriptProvider;
  }

  /**
   * Creates a new builder for {@link JavascriptWorkflow}.
   *
   * @return a new workflow builder
   */
  public static JavascriptWorkflowBuilder builder() {
    return new JavascriptWorkflowBuilder();
  }

  /**
   * Executes the JavaScript source provided by the {@link ScriptProvider}.
   *
   * <p>This method performs the following steps:
   *
   * <ol>
   *   <li>Creates a lightweight, isolated graalvm polygot {@link Context}.
   *   <li>Fetches script content from the {@link ScriptProvider}.
   *   <li>Injects the {@link WorkflowContext} into the JS global scope under the name {@code ctx}.
   *   <li>Evaluates the script and returns a success result unless an exception occurs.
   * </ol>
   *
   * @param context the shared workflow state
   * @param execContext internal execution helpers and timers
   * @return the result of the script execution
   */
  @Override
  protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
    try (Context jsContext =
        Context.newBuilder("js")
            .engine(SHARED_ENGINE)
            // Allows JS to access Java objects
            .allowHostAccess(HostAccess.ALL)
            // Restricts JS from instantiating random Java classes
            .allowHostClassLookup(_ -> false)
            // Required to resolve 'import' paths
            .allowIO(IOAccess.newBuilder().allowHostFileAccess(true).build())
            // Enable ESM module support
            .option("js.esm-eval-returns-exports", "true")
            .build()) {

      // Load script dynamically from the provider
      ScriptProvider.ScriptSource scriptSource = scriptProvider.loadScript();
      Source source =
          // URI so the engine knows the base directory for imports
          Source.newBuilder("js", scriptSource.uri().toURL())
              .content(scriptSource.content()) // Use the loaded content
              .mimeType("application/javascript+module") // Treat as ESM module
              .build();

      // Wrap the context in a proxy that intercepts 'put' calls
      jsContext.getBindings("js").putMember("ctx", new PolyglotContextProxy(context));
      jsContext.eval(source);

      return execContext.success();
    } catch (PolyglotException e) {
      // Log the JS Stack Trace which includes multiple files and line numbers
      StringBuilder errorMsg = getErrorMsg(e);

      // Return failure with the detailed JS trace
      return execContext.failure(new RuntimeException(errorMsg.toString(), e));
    } catch (Exception e) {
      return execContext.failure(e);
    }
  }

  private static StringBuilder getErrorMsg(PolyglotException e) {
    StringBuilder errorMsg = new StringBuilder("JS Error: ").append(e.getMessage()).append("\n");

    for (PolyglotException.StackFrame frame : e.getPolyglotStackTrace()) {
      boolean isGuestFrame = frame.isGuestFrame();
      SourceSection location = frame.getSourceLocation();

      // Check if this frame actually belongs to a JS source file
      if (!isGuestFrame || location == null) {
        continue;
      }

      String sourceName = location.getSource().getName();
      int line = location.getStartLine();

      String displayPath = sourceName;
      try {
        URI uri = location.getSource().getURI();
        if (uri != null) {
          String path = uri.getPath();
          if (path != null) {
            displayPath = Paths.get(path).getFileName().toString();
          } else {
            displayPath = uri.toString();
          }
        }
      } catch (Exception _) {
        // ignore
      }

      errorMsg.append(
          String.format(
              "    at %s (%s:%d)%n",
              frame.getRootName() == null ? "anonymous" : frame.getRootName(), displayPath, line));
    }
    return errorMsg;
  }

  @Override
  public String getName() {
    return WorkflowSupport.resolveName(name, this);
  }

  @Override
  public String getWorkflowType() {
    return WorkflowSupport.formatWorkflowType("JavaScript");
  }

  @Override
  public List<Workflow> getSubWorkflows() {
    try {
      ScriptProvider.ScriptSource source = scriptProvider.loadScript();
      String uriDisplay =
          (source.uri() != null && source.uri().getPath() != null)
              ? java.nio.file.Paths.get(source.uri().getPath()).getFileName().toString()
              : "inline";

      // Use the uriDisplay directly as the name of the leaf node
      return List.of(new AtomicScriptLeaf("SRC -> " + uriDisplay));
    } catch (Exception _) {
      return List.of(new AtomicScriptLeaf("SRC -> [Error]"));
    }
  }

  /**
   * A leaf node specifically for the tree representation. By returning the full label as getName(),
   * we avoid the double-space issue caused by the Wrapper/Delegate combination.
   */
  private record AtomicScriptLeaf(String label) implements Workflow {
    @Override
    public String getName() {
      return label;
    }

    @Override
    public String getWorkflowType() {
      return "(eval)";
    }

    @Override
    public WorkflowResult execute(WorkflowContext c) {
      return null;
    }
  }

  /** Fluent builder for {@link JavascriptWorkflow} instances. */
  public static class JavascriptWorkflowBuilder {
    private String name;
    private ScriptProvider scriptProvider;

    public JavascriptWorkflowBuilder name(String name) {
      this.name = name;
      return this;
    }

    public JavascriptWorkflowBuilder scriptProvider(ScriptProvider scriptProvider) {
      this.scriptProvider = scriptProvider;
      return this;
    }

    /**
     * Validates and builds the JavascriptWorkflow.
     *
     * @return a configured JavascriptWorkflow instance
     * @throws NullPointerException if scriptProvider is null
     */
    public JavascriptWorkflow build() {
      ValidationUtils.requireNonNull(scriptProvider, "scriptProvider");
      return new JavascriptWorkflow(this);
    }
  }

  /** Inner proxy to ensure any JS object put into the context is converted to Java first. */
  public record PolyglotContextProxy(WorkflowContext delegate) {
    public void put(String key, Object value) {
      // Value.asValue() handles PolyglotList, PolyglotMap, and raw JS objects
      Value polyglotValue = Value.asValue(value);

      if (polyglotValue.isProxyObject()
          || polyglotValue.hasMembers()
          || polyglotValue.hasArrayElements()) {
        // Routes PolyglotList/Map to the mapper
        delegate.put(key, PolyglotMapper.toJava(polyglotValue));
      } else {
        // For simple primitives that don't need deep mapping
        delegate.put(key, value);
      }
    }

    public Object get(String key) {
      return delegate.get(key);
    }
  }
}
