package com.workflow.script;

import com.workflow.exception.ScriptLoadException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads script content from a file on the filesystem. This is the most common script provider
 * implementation, suitable for scripts that are deployed alongside the application or managed
 * through configuration files.
 *
 * <p>The script file is loaded fresh on each invocation of {@link #loadScript()}, which enables
 * hot-reloading: you can modify the script file, and the next workflow execution will use the
 * updated content without requiring application restart.
 *
 * <h3>Features:</h3>
 *
 * <ul>
 *   <li><b>Hot Reloading:</b> Script changes are picked up on next execution
 *   <li><b>Simple Integration:</b> Works with any file-based deployment strategy
 *   <li><b>Version Control Friendly:</b> Scripts can be version controlled alongside code
 *   <li><b>No Caching:</b> Each execution reads from disk (consider wrapping for caching)
 * </ul>
 *
 * <h3>Usage Examples:</h3>
 *
 * <pre>{@code
 * // Basic usage
 * FileScriptProvider provider = new FileScriptProvider(Path.of("scripts/pricing.js"));
 * JavascriptWorkflow workflow = JavascriptWorkflow.builder()
 *     .name("PricingLogic")
 *     .scriptProvider(provider)
 *     .build();
 * }</pre>
 *
 * <pre>{@code
 * // Relative to working directory
 * FileScriptProvider provider = new FileScriptProvider(
 *     Path.of("config/workflows/validation.js")
 * );
 * }</pre>
 *
 * <pre>{@code
 * // Absolute path
 * FileScriptProvider provider = new FileScriptProvider(
 *     Path.of("/opt/app/scripts/business-rules.js")
 * );
 * }</pre>
 *
 * <pre>{@code
 * // Dynamic script selection based on environment
 * String env = System.getProperty("app.env", "dev");
 * FileScriptProvider provider = new FileScriptProvider(
 *     Path.of("scripts/" + env + "/rules.js")
 * );
 * }</pre>
 *
 * <pre>{@code
 * // Hot reloading example
 * FileScriptProvider provider = new FileScriptProvider(Path.of("scripts/dynamic.js"));
 * JavascriptWorkflow workflow = JavascriptWorkflow.builder()
 *     .scriptProvider(provider)
 *     .build();
 *
 * // First execution
 * workflow.execute(context1);
 *
 * // Update the script file (externally or via file watcher)
 * // Files.writeString(Path.of("scripts/dynamic.js"), newScriptContent);
 *
 * // Second execution automatically uses the new script
 * workflow.execute(context2);
 * }</pre>
 *
 * <h3>Error Handling:</h3>
 *
 * <p>If the file cannot be read (doesn't exist, permission denied, I/O error), a {@link
 * ScriptLoadException} is thrown with the underlying cause.
 *
 * <h3>Performance Considerations:</h3>
 *
 * <ul>
 *   <li>Each {@code loadScript()} call performs a file I/O operation
 *   <li>For high-frequency workflows, consider implementing a caching wrapper
 *   <li>File reads are typically fast for small to medium-sized scripts
 * </ul>
 *
 * @param filePath the path to the script file (must be readable)
 * @see ScriptProvider
 * @see ClasspathScriptProvider
 * @see com.workflow.JavascriptWorkflow
 */
public record FileScriptProvider(Path filePath) implements ScriptProvider {
  @Override
  public ScriptSource loadScript() throws ScriptLoadException {
    try {
      return new ScriptSource(Files.readString(filePath), filePath.toUri());
    } catch (Exception e) {
      throw new ScriptLoadException("Error while loading script", e);
    }
  }
}
