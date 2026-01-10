package com.workflow.script;

import com.workflow.exception.ScriptLoadException;
import java.net.URI;

/**
 * Provides script content for JavaScript workflows. Implementations of this interface are
 * responsible for fetching and returning the script source code, which can be loaded from various
 * sources such as files, databases, remote services, or generated dynamically.
 *
 * <p>This interface enables flexible script sourcing strategies, allowing JavaScript workflows to
 * load business logic from different sources without modifying the workflow itself.
 *
 * <h3>Built-in Implementations:</h3>
 *
 * <ul>
 *   <li>{@link InlineScriptProvider} - Provides inline script content
 *   <li>{@link FileScriptProvider} - Loads scripts from the filesystem
 *   <li>{@link ClasspathScriptProvider} - Loads scripts from classpath resources
 * </ul>
 *
 * <h3>Custom Implementation Examples:</h3>
 *
 * <pre>{@code
 * // Database-backed script provider
 * public class DatabaseScriptProvider implements ScriptProvider {
 *     private final ScriptRepository repository;
 *     private final String scriptId;
 *
 *     public DatabaseScriptProvider(ScriptRepository repo, String scriptId) {
 *         this.repository = repo;
 *         this.scriptId = scriptId;
 *     }
 *
 *     @Override
 *     public String loadScript() throws ScriptLoadException {
 *         return repository.findById(scriptId)
 *             .map(Script::getContent)
 *             .orElseThrow(() -> new ScriptLoadException("Script not found: " + scriptId));
 *     }
 * }
 * }</pre>
 *
 * <pre>{@code
 * // HTTP-backed script provider
 * public class HttpScriptProvider implements ScriptProvider {
 *     private final HttpClient client;
 *     private final String url;
 *
 *     @Override
 *     public String loadScript() throws ScriptLoadException {
 *         try {
 *             HttpRequest request = HttpRequest.newBuilder()
 *                 .uri(URI.create(url))
 *                 .build();
 *             HttpResponse<String> response = client.send(request,
 *                 HttpResponse.BodyHandlers.ofString());
 *             if (response.statusCode() != 200) {
 *                 throw new ScriptLoadException("HTTP " + response.statusCode());
 *             }
 *             return response.body();
 *         } catch (Exception e) {
 *             throw new ScriptLoadException("Failed to load script from " + url, e);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <pre>{@code
 * // Conditional script provider
 * ScriptProvider conditional = () -> {
 *     if (useNewVersion) {
 *         return Files.readString(Path.of("scripts/v2/logic.js"));
 *     } else {
 *         return Files.readString(Path.of("scripts/v1/logic.js"));
 *     }
 * };
 * }</pre>
 *
 * <h3>Usage:</h3>
 *
 * <pre>{@code
 * ScriptProvider provider = new FileScriptProvider(Path.of("scripts/business.js"));
 * JavascriptWorkflow workflow = JavascriptWorkflow.builder()
 *     .name("BusinessLogic")
 *     .scriptProvider(provider)
 *     .build();
 * }</pre>
 *
 * @see com.workflow.JavascriptWorkflow
 * @see InlineScriptProvider
 * @see FileScriptProvider
 * @see ClasspathScriptProvider
 * @see ScriptLoadException
 */
public interface ScriptProvider {
  /**
   * Represents the source of a script, including its content and optional path.
   *
   * @param content the script content
   * @param uri the optional URI of the script source (can be null)
   */
  record ScriptSource(String content, URI uri) {}

  /**
   * Fetches the script content.
   *
   * @return the script source containing the content and optional path
   * @throws ScriptLoadException if the script cannot be loaded
   */
  ScriptSource loadScript() throws ScriptLoadException;
}
