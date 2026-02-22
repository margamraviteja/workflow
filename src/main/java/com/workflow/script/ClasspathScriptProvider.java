package com.workflow.script;

import com.workflow.exception.ScriptLoadException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Loads script content from classpath resources. This provider is ideal for scripts that are
 * packaged within the application JAR and deployed alongside the application code.
 *
 * <p>Scripts loaded from the classpath are typically used for:
 *
 * <ul>
 *   <li>Built-in business logic that ships with the application
 *   <li>Default or fallback scripts
 *   <li>Test fixtures and example scripts
 *   <li>Immutable logic that doesn't require runtime updates
 * </ul>
 *
 * <p>The script file is loaded fresh on each invocation of {@link #loadScript()}, but since
 * classpath resources are read from JAR files or build directories, they are effectively immutable
 * during runtime.
 *
 * <h3>Features:</h3>
 *
 * <ul>
 *   <li><b>Classpath Resolution:</b> Uses standard Java resource loading mechanism
 *   <li><b>JAR Compatible:</b> Works seamlessly when application is packaged as JAR/WAR
 *   <li><b>UTF-8 Encoding:</b> Scripts are read using UTF-8 character encoding
 *   <li><b>No External Dependencies:</b> No file system access required
 *   <li><b>Version Controlled:</b> Scripts are part of application versioning
 * </ul>
 *
 * <h3>Resource Path Format:</h3>
 *
 * <p>Resource paths should be specified relative to the classpath root:
 *
 * <ul>
 *   <li>{@code "/scripts/validation.js"} - Absolute path from classpath root
 *   <li>{@code "scripts/validation.js"} - Relative path (leading slash optional)
 *   <li>{@code "com/company/rules/pricing.js"} - Package-style path
 * </ul>
 *
 * <h3>Usage Examples:</h3>
 *
 * <pre>{@code
 * // Basic usage - script in src/main/resources/scripts/validation.js
 * ClasspathScriptProvider provider = new ClasspathScriptProvider("/scripts/validation.js");
 * JavascriptWorkflow workflow = JavascriptWorkflow.builder()
 *     .name("ValidationLogic")
 *     .scriptProvider(provider)
 *     .build();
 * }</pre>
 *
 * <pre>{@code
 * // Package-style resource path
 * ClasspathScriptProvider provider = new ClasspathScriptProvider(
 *     "com/company/workflows/pricing-rules.js"
 * );
 * }</pre>
 *
 * <pre>{@code
 * // Load from test resources
 * ClasspathScriptProvider testProvider = new ClasspathScriptProvider(
 *     "/test-scripts/mock-logic.js"
 * );
 * }</pre>
 *
 * <pre>{@code
 * // Use with specific class loader (e.g., for plugin systems)
 * ClassLoader pluginLoader = getPluginClassLoader();
 * ClasspathScriptProvider provider = new ClasspathScriptProvider(
 *     "/plugin/scripts/logic.js",
 *     pluginLoader
 * );
 * }</pre>
 *
 * <h3>Project Structure Example:</h3>
 *
 * <pre>
 * src/main/resources/
 *   └── scripts/
 *       ├── validation/
 *       │   ├── user-validation.js
 *       │   └── order-validation.js
 *       ├── transformation/
 *       │   └── data-transform.js
 *       └── business-rules/
 *           ├── pricing.js
 *           └── discounts.js
 * </pre>
 *
 * <h3>Error Handling:</h3>
 *
 * <p>If the resource cannot be found or read, a {@link ScriptLoadException} is thrown with details
 * about the failure:
 *
 * <pre>{@code
 * try {
 *     String script = provider.loadScript();
 * } catch (ScriptLoadException e) {
 *     log.error("Failed to load classpath resource: {}", resourcePath, e);
 *     // Handle missing or invalid resource
 * }
 * }</pre>
 *
 * <h3>Deployment Considerations:</h3>
 *
 * <ul>
 *   <li><b>Immutable:</b> Scripts cannot be hot-reloaded without redeploying application
 *   <li><b>Versioned:</b> Script changes require new application deployment
 *   <li><b>Portable:</b> Works consistently across different environments
 *   <li><b>Fast Loading:</b> Resources are loaded from memory when JARed
 * </ul>
 *
 * <h3>When to Use:</h3>
 *
 * <p><b>Use ClasspathScriptProvider when:</b>
 *
 * <ul>
 *   <li>Scripts are part of application logic and should be versioned together
 *   <li>Scripts don't need runtime updates
 *   <li>You want consistent behavior across all environments
 *   <li>Scripts are test fixtures or examples
 * </ul>
 *
 * <p><b>Use FileScriptProvider instead when:</b>
 *
 * <ul>
 *   <li>Scripts need to be updated without redeploying
 *   <li>Hot-reloading of business rules is required
 *   <li>Scripts are managed by non-developers
 * </ul>
 *
 * @param resourcePath the classpath resource path (e.g., "/scripts/logic.js")
 * @param classLoader the class loader to use for resource loading (null uses context class loader)
 * @see ScriptProvider
 * @see FileScriptProvider
 * @see com.workflow.JavascriptWorkflow
 */
public record ClasspathScriptProvider(String resourcePath, ClassLoader classLoader)
    implements ScriptProvider {
  /**
   * Creates a ClasspathScriptProvider using the context class loader.
   *
   * @param resourcePath the classpath resource path
   */
  public ClasspathScriptProvider(String resourcePath) {
    this(resourcePath, null);
  }

  @Override
  public ScriptSource loadScript() throws ScriptLoadException {
    try {
      ClassLoader loader = classLoader != null ? classLoader : getDefaultClassLoader();
      String normalizedPath = normalizeResourcePath(resourcePath);

      // Get the URL of the resource to derive the URI
      URL resourceUrl = loader.getResource(normalizedPath);
      if (resourceUrl == null) {
        throw new ScriptLoadException("Classpath resource not found: " + resourcePath);
      }

      try (InputStream inputStream = resourceUrl.openStream()) {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        // Provide the URI so GraalVM can resolve relative imports within the classpath/JAR
        return new ScriptSource(content, resourceUrl.toURI());
      }
    } catch (ScriptLoadException e) {
      throw e;
    } catch (Exception e) {
      throw new ScriptLoadException("Failed to load classpath resource: " + resourcePath, e);
    }
  }

  private ClassLoader getDefaultClassLoader() {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    return contextClassLoader != null ? contextClassLoader : getClass().getClassLoader();
  }

  private String normalizeResourcePath(String path) {
    // Remove leading slash for getResourceAsStream
    return path.startsWith("/") ? path.substring(1) : path;
  }
}
