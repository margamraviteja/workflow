package com.workflow.examples.javascript;

import com.workflow.JavascriptWorkflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import com.workflow.script.FileScriptProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Examples demonstrating ECMAScript Module (ESM) support in JavascriptWorkflow.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Importing JavaScript modules using ES6 import/export syntax
 *   <li>Creating reusable utility modules
 *   <li>Module resolution and file organization
 *   <li>Sharing modules across multiple workflows
 *   <li>Namespace imports and named exports
 *   <li>Default exports and barrel exports
 * </ul>
 *
 * <h3>Module Resolution:</h3>
 *
 * <p>JavascriptWorkflow uses GraalVM's ESM support which resolves modules relative to the script's
 * URI. The script provider must provide a valid URI for module resolution to work.
 *
 * <h3>File Extensions:</h3>
 *
 * <p>Use {@code .mjs} extension for module scripts or set MIME type to {@code
 * application/javascript+module}.
 *
 * <h3>Import Syntax:</h3>
 *
 * <pre>{@code
 * // Named imports
 * import { functionName, variableName } from './module.mjs';
 *
 * // Namespace import
 * import * as Utils from './utils.mjs';
 *
 * // Default import
 * import defaultExport from './module.mjs';
 *
 * // Mixed imports
 * import defaultExport, { named1, named2 } from './module.mjs';
 * }</pre>
 *
 * <p>Run the examples with {@code main()} method.
 */
@UtilityClass
@Slf4j
public class ESMModuleExample {

  public static final String RESULT = "result";

  public static void main(String[] args) throws IOException {
    // Create temporary directory for module examples
    Path modulesDir = Files.createTempDirectory("workflow-modules-");

    try {
      log.info("=== ECMAScript Module Examples ===\n");
      log.info("Modules directory: {}\n", modulesDir);

      basicModuleImport(modulesDir);
      utilityModules(modulesDir);
      namespaceImports(modulesDir);
      nestedModules(modulesDir);
      sharedLibraries(modulesDir);
      barrelExports(modulesDir);
      circularReferences(modulesDir);

      log.info("\n=== Examples completed successfully ===");
    } finally {
      // Cleanup
      deleteDirectory(modulesDir);
    }
  }

  /**
   * Example 1: Basic module import.
   *
   * <p>Demonstrates creating a simple utility module and importing it in a workflow script.
   */
  private static void basicModuleImport(Path baseDir) throws IOException {
    log.info("Example 1: Basic Module Import");

    // Create a simple math utility module
    Path mathModule = baseDir.resolve("math.mjs");
    Files.writeString(
        mathModule,
        """
        // Export functions that can be imported by other modules
        export function add(a, b) {
            return a + b;
        }

        export function multiply(a, b) {
            return a * b;
        }

        export const PI = 3.14159;
        """);

    // Create main script that uses the math module
    Path mainScript = baseDir.resolve("calculator.mjs");
    Files.writeString(
        mainScript,
        """
        import { add, multiply, PI } from './math.mjs';

        const x = ctx.get('x');
        const y = ctx.get('y');

        const sum = add(x, y);
        const product = multiply(x, y);
        const circleArea = PI * x * x;

        ctx.put('sum', sum);
        ctx.put('product', product);
        ctx.put('circleArea', circleArea);
        """);

    // Execute workflow
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("BasicModuleCalculator")
            .scriptProvider(new FileScriptProvider(mainScript))
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("x", 5);
    context.put("y", 3);

    WorkflowResult result = workflow.execute(context);

    logStatus(result);
    log.info("Sum: {}", context.get("sum"));
    log.info("Product: {}", context.get("product"));
    log.info("Circle Area (radius={}): {}\n", context.get("x"), context.get("circleArea"));
  }

  /**
   * Example 2: Utility modules for reusable business logic.
   *
   * <p>Creates a set of utility modules for validation, formatting, and calculations that can be
   * reused across multiple workflows.
   */
  private static void utilityModules(Path baseDir) throws IOException {
    log.info("Example 2: Utility Modules");

    // Create validation utilities module
    Path validationModule = baseDir.resolve("validation.mjs");
    Files.writeString(
        validationModule,
        """
        export function isValidEmail(email) {
            return email && email.includes('@') && email.includes('.');
        }

        export function isValidPhone(phone) {
            return phone && /^\\d{10}$/.test(phone.replace(/[^\\d]/g, ''));
        }

        export function isNotEmpty(value) {
            return value !== null && value !== undefined && value !== '';
        }
        """);

    // Create formatting utilities module
    Path formatModule = baseDir.resolve("format.mjs");
    Files.writeString(
        formatModule,
        """
        export function formatCurrency(amount) {
            return '$' + amount.toFixed(2);
        }

        export function formatDate(dateStr) {
            const date = new Date(dateStr);
            return date.toLocaleDateString('en-US');
        }

        export function capitalize(str) {
            return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
        }
        """);

    // Create main workflow that uses utilities
    Path mainScript = baseDir.resolve("userProcessor.mjs");
    Files.writeString(
        mainScript,
        """
        import { isValidEmail, isValidPhone, isNotEmpty } from './validation.mjs';
        import { formatCurrency, capitalize } from './format.mjs';

        const user = ctx.get('user');

        // Validation
        const validationErrors = [];
        if (!isNotEmpty(user.name)) validationErrors.push('Name is required');
        if (!isValidEmail(user.email)) validationErrors.push('Invalid email');
        if (!isValidPhone(user.phone)) validationErrors.push('Invalid phone number');

        const isValid = validationErrors.length === 0;

        // Formatting (if valid)
        let processed = null;
        if (isValid) {
            processed = {
                name: capitalize(user.name),
                email: user.email.toLowerCase(),
                phone: user.phone,
                creditLimit: formatCurrency(user.creditLimit)
            };
        }

        ctx.put('isValid', isValid);
        ctx.put('validationErrors', validationErrors);
        ctx.put('processedUser', processed);
        """);

    // Execute with valid user
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("UserValidationProcessor")
            .scriptProvider(new FileScriptProvider(mainScript))
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put(
        "user",
        java.util.Map.of(
            "name", "john doe",
            "email", "john@example.com",
            "phone", "1234567890",
            "creditLimit", 5000.0));

    workflow.execute(context);

    log.info("Valid: {}", context.get("isValid"));
    log.info("Processed User: {}\n", context.get("processedUser"));
  }

  /**
   * Example 3: Namespace imports.
   *
   * <p>Demonstrates importing an entire module as a namespace using {@code import * as}.
   */
  private static void namespaceImports(Path baseDir) throws IOException {
    log.info("Example 3: Namespace Imports");

    // Create a math library with multiple functions
    Path mathLib = baseDir.resolve("mathLib.mjs");
    Files.writeString(
        mathLib,
        """
        export function square(n) {
            return n * n;
        }

        export function cube(n) {
            return n * n * n;
        }

        export function sqrt(n) {
            return Math.sqrt(n);
        }

        export function abs(n) {
            return Math.abs(n);
        }

        export const E = 2.71828;
        """);

    // Import entire module as namespace
    Path mainScript = baseDir.resolve("mathOperations.mjs");
    Files.writeString(
        mainScript,
        """
        import * as MathLib from './mathLib.mjs';

        const value = ctx.get('value');

        ctx.put('square', MathLib.square(value));
        ctx.put('cube', MathLib.cube(value));
        ctx.put('sqrt', MathLib.sqrt(value));
        ctx.put('abs', MathLib.abs(value));
        ctx.put('e', MathLib.E);
        """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("NamespaceImport")
            .scriptProvider(new FileScriptProvider(mainScript))
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("value", 4);

    workflow.execute(context);

    log.info("Input: {}", context.get("value"));
    log.info("Square: {}", context.get("square"));
    log.info("Cube: {}", context.get("cube"));
    log.info("Square Root: {}", context.get("sqrt"));
    log.info("Euler's number: {}\n", context.get("e"));
  }

  /**
   * Example 4: Nested modules.
   *
   * <p>Demonstrates module chains where modules import other modules.
   */
  private static void nestedModules(Path baseDir) throws IOException {
    log.info("Example 4: Nested Modules");

    // Create infrastructure directory
    Path infraDir = Files.createDirectories(baseDir.resolve("infrastructure"));

    // Database config module
    Files.writeString(
        infraDir.resolve("dbConfig.mjs"),
        """
        export const dbHost = 'localhost';
        export const dbPort = 5432;
        export const dbName = 'workflow_db';
        """);

    // Repository module that imports config
    Files.writeString(
        infraDir.resolve("repository.mjs"),
        """
        import { dbHost, dbPort, dbName } from './dbConfig.mjs';

        export function getConnectionString() {
            return `postgresql://${dbHost}:${dbPort}/${dbName}`;
        }

        export function fetchData(query) {
            // Simulated database query
            return {
                connection: getConnectionString(),
                query: query,
                results: ['record1', 'record2']
            };
        }
        """);

    // Service module that imports repository
    Path serviceScript = baseDir.resolve("service.mjs");
    Files.writeString(
        serviceScript,
        """
        import { getConnectionString, fetchData } from './infrastructure/repository.mjs';

        const query = ctx.get('query');
        const data = fetchData(query);

        ctx.put('connectionString', getConnectionString());
        ctx.put('queryResults', data);
        """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("NestedModules")
            .scriptProvider(new FileScriptProvider(serviceScript))
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("query", "SELECT * FROM users");

    workflow.execute(context);

    log.info("Connection: {}", context.get("connectionString"));
    log.info("Results: {}\n", context.get("queryResults"));
  }

  /**
   * Example 5: Shared libraries across multiple workflows.
   *
   * <p>Demonstrates how multiple workflows can import and use the same utility modules.
   */
  private static void sharedLibraries(Path baseDir) throws IOException {
    log.info("Example 5: Shared Libraries");

    // Create shared lib directory
    Path libDir = Files.createDirectories(baseDir.resolve("lib"));

    // Shared string utilities
    Files.writeString(
        libDir.resolve("stringUtils.mjs"),
        """
        export function toUpperCase(str) {
            return str.toUpperCase();
        }

        export function toLowerCase(str) {
            return str.toLowerCase();
        }

        export function truncate(str, maxLength) {
            return str.length > maxLength
                ? str.substring(0, maxLength) + '...'
                : str;
        }
        """);

    // Workflow 1: Uppercase conversion
    Path workflow1Script = baseDir.resolve("uppercaseWorkflow.mjs");
    Files.writeString(
        workflow1Script,
        """
        import { toUpperCase } from './lib/stringUtils.mjs';

        const text = ctx.get('text');
        ctx.put('result', toUpperCase(text));
        """);

    // Workflow 2: Lowercase conversion
    Path workflow2Script = baseDir.resolve("lowercaseWorkflow.mjs");
    Files.writeString(
        workflow2Script,
        """
        import { toLowerCase } from './lib/stringUtils.mjs';

        const text = ctx.get('text');
        ctx.put('result', toLowerCase(text));
        """);

    // Workflow 3: Truncation
    Path workflow3Script = baseDir.resolve("truncateWorkflow.mjs");
    Files.writeString(
        workflow3Script,
        """
        import { truncate } from './lib/stringUtils.mjs';

        const text = ctx.get('text');
        const maxLength = ctx.get('maxLength');
        ctx.put('result', truncate(text, maxLength));
        """);

    // Execute all workflows
    String testText = "Hello Workflow Engine!";

    // Workflow 1
    JavascriptWorkflow wf1 =
        JavascriptWorkflow.builder()
            .name("UpperCase")
            .scriptProvider(new FileScriptProvider(workflow1Script))
            .build();
    WorkflowContext ctx1 = new WorkflowContext();
    ctx1.put("text", testText);
    wf1.execute(ctx1);
    log.info("Uppercase: {}", ctx1.get(RESULT));

    // Workflow 2
    JavascriptWorkflow wf2 =
        JavascriptWorkflow.builder()
            .name("LowerCase")
            .scriptProvider(new FileScriptProvider(workflow2Script))
            .build();
    WorkflowContext ctx2 = new WorkflowContext();
    ctx2.put("text", testText);
    wf2.execute(ctx2);
    log.info("Lowercase: {}", ctx2.get(RESULT));

    // Workflow 3
    JavascriptWorkflow wf3 =
        JavascriptWorkflow.builder()
            .name("Truncate")
            .scriptProvider(new FileScriptProvider(workflow3Script))
            .build();
    WorkflowContext ctx3 = new WorkflowContext();
    ctx3.put("text", testText);
    ctx3.put("maxLength", 10);
    wf3.execute(ctx3);
    log.info("Truncated: {}\n", ctx3.get(RESULT));
  }

  /**
   * Example 6: Barrel exports (index.mjs pattern).
   *
   * <p>Demonstrates using index.mjs files to aggregate exports from multiple modules.
   */
  private static void barrelExports(Path baseDir) throws IOException {
    log.info("Example 6: Barrel Exports");

    // Create utilities directory
    Path utilsDir = Files.createDirectories(baseDir.resolve("utils"));

    // Array utilities
    Files.writeString(
        utilsDir.resolve("arrayUtils.mjs"),
        """
        export function first(arr) {
            return arr[0];
        }

        export function last(arr) {
            return arr[arr.length - 1];
        }
        """);

    // Object utilities
    Files.writeString(
        utilsDir.resolve("objectUtils.mjs"),
        """
        export function isEmpty(obj) {
            return Object.keys(obj).length === 0;
        }

        export function merge(obj1, obj2) {
            return { ...obj1, ...obj2 };
        }
        """);

    // Barrel export (index.mjs)
    Files.writeString(
        utilsDir.resolve("index.mjs"),
        """
        // Re-export everything from both modules
        export * from './arrayUtils.mjs';
        export * from './objectUtils.mjs';
        """);

    // Main script using barrel import
    Path mainScript = baseDir.resolve("barrelExample.mjs");
    Files.writeString(
        mainScript,
        """
        // Import from index.mjs
        import { first, last, isEmpty, merge } from './utils/index.mjs';

        const arr = ctx.get('array');
        const obj1 = ctx.get('object1');
        const obj2 = ctx.get('object2');

        ctx.put('firstElement', first(arr));
        ctx.put('lastElement', last(arr));
        ctx.put('isEmptyObject', isEmpty(obj1));
        ctx.put('mergedObject', merge(obj1, obj2));
        """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("BarrelExports")
            .scriptProvider(new FileScriptProvider(mainScript))
            .build();

    WorkflowContext context = new WorkflowContext();
    context.put("array", java.util.List.of(10, 20, 30, 40));
    context.put("object1", java.util.Map.of("a", 1, "b", 2));
    context.put("object2", java.util.Map.of("c", 3, "d", 4));

    workflow.execute(context);

    log.info("First: {}", context.get("firstElement"));
    log.info("Last: {}", context.get("lastElement"));
    log.info("Is Empty: {}", context.get("isEmptyObject"));
    log.info("Merged: {}\n", context.get("mergedObject"));
  }

  /**
   * Example 7: Circular module references.
   *
   * <p>Demonstrates that GraalVM handles circular dependencies gracefully when modules only import
   * and don't immediately execute dependent code.
   */
  private static void circularReferences(Path baseDir) throws IOException {
    log.info("Example 7: Circular Module References");

    // Module A depends on Module B
    Path moduleA = baseDir.resolve("moduleA.mjs");
    Files.writeString(
        moduleA,
        """
        import { valueB } from './moduleB.mjs';

        export const valueA = 'A';

        export function getCombined() {
            return valueA + valueB;
        }
        """);

    // Module B depends on Module A (circular reference)
    Path moduleB = baseDir.resolve("moduleB.mjs");
    Files.writeString(
        moduleB,
        """
        import { valueA } from './moduleA.mjs';

        export const valueB = 'B';

        // This works because we're not calling getCombined() during module initialization
        """);

    // Main script
    Path mainScript = baseDir.resolve("circularMain.mjs");
    Files.writeString(
        mainScript,
        """
        import { getCombined } from './moduleA.mjs';

        const result = getCombined();
        ctx.put('combined', result);
        """);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("CircularReference")
            .scriptProvider(new FileScriptProvider(mainScript))
            .build();

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    logStatus(result);
    log.info("Combined value: {}", context.get("combined"));
    log.info("Note: Circular imports work as long as code isn't executed during initialization\n");
  }

  private static void logStatus(WorkflowResult result) {
    log.info("Status: {}", result.getStatus());
  }

  // Helper method to clean up temp directory
  private static void deleteDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      try (Stream<Path> stream = Files.walk(directory)) {
        stream
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException _) {
                    // Ignore cleanup errors
                  }
                });
      }
    }
  }
}
