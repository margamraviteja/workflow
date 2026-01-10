package com.workflow.script;

import com.workflow.exception.ScriptLoadException;
import java.net.URI;

/**
 * A simple ScriptProvider that returns a pre-defined script string. This provider is ideal for
 * inline scripts, testing, and simple use cases where the script content is known at compile time
 * or constructed programmatically.
 *
 * <p>Unlike file-based or HTTP-based providers, this provider has no I/O overhead and always
 * returns the same script content immediately.
 *
 * <h3>Key Features:</h3>
 *
 * <ul>
 *   <li><b>Zero Overhead:</b> No file I/O, network calls, or database queries
 *   <li><b>Immutable:</b> Script content is set at construction and never changes
 *   <li><b>Simple:</b> Perfect for lambda expressions and simple scripts
 *   <li><b>Fast:</b> Ideal for high-frequency workflows with static logic
 *   <li><b>Testable:</b> Easy to use in unit tests
 * </ul>
 *
 * <h3>Use Cases:</h3>
 *
 * <ul>
 *   <li><b>Simple Calculations:</b> Basic arithmetic or data transformations
 *   <li><b>Testing:</b> Mock scripts for unit tests
 *   <li><b>Prototyping:</b> Quick script experimentation
 *   <li><b>Static Logic:</b> Business rules that don't change
 *   <li><b>Configuration:</b> Scripts defined in application configuration
 * </ul>
 *
 * <h3>Usage Examples:</h3>
 *
 * <pre>{@code
 * // Simple calculation
 * ScriptProvider calcProvider = new InlineScriptProvider(
 *     "var total = context.get('price') * context.get('quantity');" +
 *     "context.put('total', total);"
 * );
 *
 * JavascriptWorkflow workflow = JavascriptWorkflow.builder()
 *     .name("CalculateTotal")
 *     .scriptProvider(calcProvider)
 *     .build();
 * }</pre>
 *
 * <pre>{@code
 * // Using Java text blocks (Java 15+)
 * ScriptProvider validationProvider = new InlineScriptProvider(
 *     """
 *     var email = context.get('email');
 *     var isValid = email && email.includes('@');
 *     context.put('emailValid', isValid);
 *     """
 * );
 * }</pre>
 *
 * <pre>{@code
 * // Lambda-style inline provider
 * ScriptProvider lambdaProvider = () -> "context.put('timestamp', Date.now());";
 *
 * JavascriptWorkflow workflow = JavascriptWorkflow.builder()
 *     .scriptProvider(lambdaProvider)
 *     .build();
 * }</pre>
 *
 * <pre>{@code
 * // Testing with mock scripts
 * @Test
 * void testWorkflowWithMockScript() {
 *     ScriptProvider testScript = new InlineScriptProvider(
 *         "context.put('result', 'test-value');"
 *     );
 *
 *     JavascriptWorkflow workflow = JavascriptWorkflow.builder()
 *         .scriptProvider(testScript)
 *         .build();
 *
 *     WorkflowContext context = new WorkflowContext();
 *     workflow.execute(context);
 *
 *     assertEquals("test-value", context.get("result"));
 * }
 * }</pre>
 *
 * <pre>{@code
 * // Programmatically constructed scripts
 * public ScriptProvider createDiscountScript(double discountPercent) {
 *     String script = String.format(
 *         "var price = context.get('price');" +
 *         "var discount = price * %.2f;" +
 *         "context.put('discountedPrice', price - discount);",
 *         discountPercent
 *     );
 *     return new InlineScriptProvider(script);
 * }
 * }</pre>
 *
 * <pre>{@code
 * // Configuration-driven scripts
 * @Configuration
 * public class WorkflowConfig {
 *     @Value("${workflow.validation.script}")
 *     private String validationScript;
 *
 *     @Bean
 *     public JavascriptWorkflow validationWorkflow() {
 *         return JavascriptWorkflow.builder()
 *             .name("Validation")
 *             .scriptProvider(new InlineScriptProvider(validationScript))
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * <h3>Multi-Line Scripts:</h3>
 *
 * <pre>{@code
 * // Traditional string concatenation
 * ScriptProvider provider1 = new InlineScriptProvider(
 *     "var user = context.get('user');\n" +
 *     "var isAdmin = user.roles.includes('ADMIN');\n" +
 *     "context.put('hasAccess', isAdmin);"
 * );
 *
 * // Text blocks (Java 15+, recommended)
 * ScriptProvider provider2 = new InlineScriptProvider(
 *     """
 *     var user = context.get('user');
 *     var isAdmin = user.roles.includes('ADMIN');
 *     context.put('hasAccess', isAdmin);
 *     """
 * );
 * }</pre>
 *
 * <h3>When to Use:</h3>
 *
 * <p><b>Use InlineScriptProvider when:</b>
 *
 * <ul>
 *   <li>Script is simple and unlikely to change
 *   <li>Performance is critical (high-frequency workflows)
 *   <li>Writing unit tests
 *   <li>Prototyping or experimenting
 *   <li>Script is generated programmatically
 * </ul>
 *
 * <p><b>Use other providers when:</b>
 *
 * <ul>
 *   <li>Script needs to be updated without redeployment (FileScriptProvider)
 *   <li>Script is part of application package (ClasspathScriptProvider)
 * </ul>
 *
 * <h3>Validation Example:</h3>
 *
 * <pre>{@code
 * public ScriptProvider createValidatedInlineScript(String script) {
 *     if (script == null || script.trim().isEmpty()) {
 *         throw new IllegalArgumentException("Script cannot be empty");
 *     }
 *     if (script.length() > 10000) {
 *         throw new IllegalArgumentException("Script too large for inline provider");
 *     }
 *     return new InlineScriptProvider(script);
 * }
 * }</pre>
 *
 * @param scriptContent the JavaScript code to execute
 * @see ScriptProvider
 * @see FileScriptProvider
 * @see ClasspathScriptProvider
 */
public record InlineScriptProvider(String scriptContent, URI baseUri) implements ScriptProvider {
  /** Convenience constructor for scripts that don't require imports or use absolute URIs. */
  public InlineScriptProvider(String scriptContent) {
    this(scriptContent, URI.create("file:///inline-script.mjs"));
  }

  /**
   * Returns the pre-defined script content.
   *
   * <p>This method never fails and has no I/O overhead.
   *
   * @return the script content wrapped in a ScriptSource
   * @throws ScriptLoadException never thrown (kept for interface compatibility)
   */
  @Override
  public ScriptSource loadScript() throws ScriptLoadException {
    return new ScriptSource(scriptContent, baseUri);
  }
}
