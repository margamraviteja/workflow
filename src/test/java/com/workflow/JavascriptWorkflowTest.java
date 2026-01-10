package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.ScriptLoadException;
import com.workflow.script.FileScriptProvider;
import com.workflow.script.InlineScriptProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavascriptWorkflowTest {

  @TempDir Path tempDir; // Automatically managed temporary directory

  private WorkflowContext workflowContext;

  @BeforeEach
  void setUp() {
    workflowContext = new WorkflowContext();
  }

  @Test
  void testSimpleVariableMutation() {
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .name("SimpleTest")
            .scriptProvider(new InlineScriptProvider("ctx.put('status', 'active');"))
            .build();

    WorkflowResult result = workflow.execute(workflowContext);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals("active", workflowContext.get("status"));
  }

  @Test
  void testConditionalLogic() {
    // JS: Perform logic based on input
    String script =
        """
                var score = ctx.get('score');
                if (score >= 90) {
                    ctx.put('grade', 'A');
                } else {
                    ctx.put('grade', 'B');
                }
                """;

    workflowContext.put("score", 95);
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().scriptProvider(new InlineScriptProvider(script)).build();

    workflow.execute(workflowContext);

    assertEquals("A", workflowContext.get("grade"));
  }

  @Test
  void testJsonTransformation() {
    // JS: Use native JSON to parse a string and transform it
    String script =
        """
                var raw = JSON.parse(ctx.get('rawJson'));
                var total = raw.items.reduce((sum, item) => sum + item.price, 0);
                ctx.put('calculatedTotal', total);
                """;

    workflowContext.put("rawJson", "{\"items\": [{\"price\": 10}, {\"price\": 20}]}");
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().scriptProvider(new InlineScriptProvider(script)).build();

    workflow.execute(workflowContext);

    assertEquals(30, workflowContext.get("calculatedTotal"));
  }

  @Test
  void testScriptSyntaxError() {
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .scriptProvider(new InlineScriptProvider("if (true) { ctx.put('oops', 1); "))
            .build();

    WorkflowResult result = workflow.execute(workflowContext);

    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertNotNull(result.getError());
  }

  @Test
  void testSecuritySandbox() {
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder()
            .scriptProvider(
                new InlineScriptProvider(
                    "var System = Java.type('java.lang.System'); System.exit(1);"))
            .build();

    WorkflowResult result = workflow.execute(workflowContext);

    // Should fail because allowHostClassLookup is false
    assertEquals(WorkflowStatus.FAILED, result.getStatus());
  }

  @Test
  void testExecutionFromActualFile() throws IOException {
    // 1. Create a physical JS file in the temp directory
    Path scriptPath = tempDir.resolve("tax_calc.js");
    String scriptContent =
        """
    var price = ctx.get('price');
    var total = price * 1.1;
    ctx.put('total', Math.round(total * 100) / 100);
    """;
    Files.writeString(scriptPath, scriptContent);

    // 2. Setup Provider and Workflow
    FileScriptProvider provider = new FileScriptProvider(scriptPath);
    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().name("FileWorkflow").scriptProvider(provider).build();

    // 3. Execute
    workflowContext.put("price", 100.0);
    WorkflowResult result = workflow.execute(workflowContext);

    // 4. Verify
    assertTrue(result.isSuccess());
    assertEquals(110, workflowContext.get("total"));
  }

  @Test
  void testMissingFileThrowsException() {
    Path nonExistentPath = tempDir.resolve("ghost.js");
    FileScriptProvider provider = new FileScriptProvider(nonExistentPath);

    JavascriptWorkflow workflow = JavascriptWorkflow.builder().scriptProvider(provider).build();

    WorkflowResult result = workflow.execute(workflowContext);

    // The error should be caught by AbstractWorkflow and returned as a failure
    assertEquals(WorkflowStatus.FAILED, result.getStatus());
    assertInstanceOf(ScriptLoadException.class, result.getError());
  }

  @Test
  void testFileUpdateReflectedInExecution() throws IOException {
    Path scriptPath = tempDir.resolve("dynamic.js");
    FileScriptProvider provider = new FileScriptProvider(scriptPath);
    JavascriptWorkflow workflow = JavascriptWorkflow.builder().scriptProvider(provider).build();

    // Execution 1: Initial logic
    Files.writeString(scriptPath, "ctx.put('val', 1);");
    workflow.execute(workflowContext);
    assertEquals(1, workflowContext.get("val"));

    // Execution 2: Update file content (No need to recreate Workflow)
    Files.writeString(scriptPath, "ctx.put('val', 2);");
    workflow.execute(workflowContext);
    assertEquals(2, workflowContext.get("val"));
  }

  @Test
  void testComplexCollectionFiltering() throws IOException {
    Path scriptPath = tempDir.resolve("filter.js");
    // JS Logic: Filter a Java List based on an attribute
    String script =
        """
            var list = ctx.get('inputList');
            var filtered = list.stream()
                               .filter(x => x.startsWith('A'))
                               .toList();
            ctx.put('outputList', filtered);
            """;
    Files.writeString(scriptPath, script);

    List<String> input = java.util.List.of("Apple", "Banana", "Apricot", "Cherry");
    workflowContext.put("inputList", input);

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().scriptProvider(new FileScriptProvider(scriptPath)).build();

    workflow.execute(workflowContext);

    // Verify the filtered list contains only 'A' items
    assertEquals(2, ((java.util.List<?>) workflowContext.get("outputList")).size());
  }

  @Test
  void testOrderRiskScoring() throws IOException {
    Path scriptPath = tempDir.resolve("risk_engine.js");
    String script =
        """
        var order = JSON.parse(ctx.get('payload'));
        var riskScore = 0;

        // 1. High value order check
        if (order.total > 5000) riskScore += 10;

        // 2. International shipping check
        if (order.shipping.country !== 'US') riskScore += 5;

        // 3. New customer check
        if (order.customer.yearsActive < 1) riskScore += 15;

        var action = riskScore > 20 ? 'MANUAL_REVIEW' : 'AUTO_APPROVE';

        ctx.put('riskScore', riskScore);
        ctx.put('workflowAction', action);
        """;
    Files.writeString(scriptPath, script);

    // Complex nested JSON input
    String jsonInput =
        """
        {
            "total": 6000,
            "shipping": { "country": "UK" },
            "customer": { "yearsActive": 0.5 }
        }
        """;

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().scriptProvider(new FileScriptProvider(scriptPath)).build();

    workflowContext.put("payload", jsonInput);
    workflow.execute(workflowContext);

    // Expected Risk: 10 (total) + 5 (country) + 15 (years) = 30
    assertEquals(30, workflowContext.get("riskScore"));
    assertEquals("MANUAL_REVIEW", workflowContext.get("workflowAction"));
  }

  @Test
  void testDataFlattening() throws IOException {
    Path scriptPath = tempDir.resolve("flatten.js");
    String script =
        """
        var response = JSON.parse(ctx.get('apiResponse'));
        var flattened = [];

        response.data.forEach(user => {
            user.permissions.forEach(perm => {
                flattened.push({
                    userId: user.id,
                    email: user.contact.email,
                    permission: perm.name
                });
            });
        });

        ctx.put('resultList', flattened);
        """;
    Files.writeString(scriptPath, script);

    String apiResponse =
        """
        {
          "data": [{
            "id": "U1",
            "contact": { "email": "test@test.com" },
            "permissions": [{ "name": "READ" }, { "name": "WRITE" }]
          }]
        }
        """;

    JavascriptWorkflow workflow =
        JavascriptWorkflow.builder().scriptProvider(new FileScriptProvider(scriptPath)).build();

    workflowContext.put("apiResponse", apiResponse);
    workflow.execute(workflowContext);

    assertEquals(2, ((java.util.List<?>) workflowContext.get("resultList")).size());
  }

  private JavascriptWorkflow.JavascriptWorkflowBuilder createBuilder(Path scriptPath) {
    return JavascriptWorkflow.builder()
        .name(scriptPath.getFileName().toString())
        .scriptProvider(new FileScriptProvider(scriptPath));
  }

  @Test
  void shouldExecuteWithRelativeModuleImport() throws IOException {
    // 1. Create the utility module
    Path utilsPath = tempDir.resolve("utils.mjs");
    Files.writeString(
        utilsPath,
        """
        export const multiplier = 2;
        export function doubleValue(val) { return val * multiplier; }
    """);

    // 2. Create the main entry point
    Path mainPath = tempDir.resolve("main.mjs");
    Files.writeString(
        mainPath,
        """
        import { doubleValue } from './utils.mjs';
        const input = ctx.get('input');
        ctx.put('output', doubleValue(input));
    """);

    // 3. Execute
    WorkflowContext context = new WorkflowContext(); // Assuming standard constructor
    context.put("input", 21);

    JavascriptWorkflow workflow = createBuilder(mainPath).build();
    workflow.execute(context);

    assertEquals(42, context.get("output"));
  }

  @Test
  void shouldShareLibraryModuleBetweenWorkflows() throws IOException {
    // Create a 'lib' directory
    Path libDir = Files.createDirectory(tempDir.resolve("lib"));
    Files.writeString(libDir.resolve("math.mjs"), "export function square(n) { return n * n; }");

    // Workflow A
    Path flowA = tempDir.resolve("flowA.mjs");
    Files.writeString(flowA, "import { square } from './lib/math.mjs'; ctx.put('res', square(5));");

    // Workflow B
    Path flowB = tempDir.resolve("flowB.mjs");
    Files.writeString(
        flowB, "import { square } from './lib/math.mjs'; ctx.put('res', square(10));");

    WorkflowContext ctxA = new WorkflowContext();
    WorkflowContext ctxB = new WorkflowContext();

    createBuilder(flowA).build().execute(ctxA);
    createBuilder(flowB).build().execute(ctxB);

    assertEquals(25, ctxA.get("res"));
    assertEquals(100, ctxB.get("res"));
  }

  @Test
  void shouldReturnFailureWhenImportIsMissing() throws IOException {
    Path mainPath = tempDir.resolve("broken.mjs");
    Files.writeString(mainPath, "import { ghost } from './non-existent.mjs';");

    JavascriptWorkflow workflow = createBuilder(mainPath).build();
    WorkflowResult result = workflow.execute(new WorkflowContext());

    assertFalse(result.isSuccess());
    assertTrue(result.getError().getMessage().contains("Cannot find module"));
  }

  @Test
  void shouldHandleDeeplyNestedImports(@TempDir Path tempDir) throws IOException {
    // Structure: main -> service -> repository -> db-config
    Path libDir = Files.createDirectories(tempDir.resolve("infra"));

    Files.writeString(libDir.resolve("config.mjs"), "export const dbName = 'PROD_DB';");
    Files.writeString(
        libDir.resolve("repo.mjs"),
        """
        import { dbName } from './config.mjs';
        export function getData() { return "Data from " + dbName; }
    """);

    Path servicePath = tempDir.resolve("service.mjs");
    Files.writeString(
        servicePath,
        """
        import { getData } from './infra/repo.mjs';
        export function process() { return getData().toUpperCase(); }
    """);

    Path mainPath = tempDir.resolve("main.mjs");
    Files.writeString(
        mainPath,
        """
        import { process } from './service.mjs';
        ctx.put('output', process());
    """);

    WorkflowContext context = new WorkflowContext();
    createBuilder(mainPath).build().execute(context);

    assertEquals("DATA FROM PROD_DB", context.get("output"));
  }

  // `A` imports `B` and `B` imports `A`
  // GraalVM (and ESM spec) handles this by allowing the module records to link before execution
  @Test
  void shouldHandleCircularModuleImports(@TempDir Path tempDir) throws IOException {
    // A.mjs
    Path pathA = tempDir.resolve("a.mjs");
    Files.writeString(
        pathA,
        """
        import { bValue } from './b.mjs';
        export const aValue = 'A';
        export function getCombined() { return aValue + bValue; }
    """);

    // B.mjs
    Path pathB = tempDir.resolve("b.mjs");
    Files.writeString(
        pathB,
        """
        import { aValue } from './a.mjs';
        export const bValue = 'B';
    """);

    // Main script
    Path mainPath = tempDir.resolve("main.mjs");
    Files.writeString(
        mainPath,
        """
        import { getCombined } from './a.mjs';
        ctx.put('circularResult', getCombined());
    """);

    WorkflowContext context = new WorkflowContext();
    createBuilder(mainPath).build().execute(context);

    // Result should be 'AB'
    assertEquals("AB", context.get("circularResult"));
  }

  // Star (*) and Named Alias Imports
  @Test
  void shouldHandleStarAndAliasImports(@TempDir Path tempDir) throws IOException {
    Path mathPath = tempDir.resolve("math_lib.mjs");
    Files.writeString(
        mathPath,
        """
        export function add(a, b) { return a + b; }
        export function subtract(a, b) { return a - b; }
    """);

    Path mainPath = tempDir.resolve("main.mjs");
    Files.writeString(
        mainPath,
        """
        import * as MathLib from './math_lib.mjs';
        import { add as sum } from './math_lib.mjs';

        ctx.put('sumResult', sum(10, 5));
        ctx.put('libResult', MathLib.subtract(20, 5));
    """);

    WorkflowContext context = new WorkflowContext();
    createBuilder(mainPath).build().execute(context);

    assertEquals(15, context.get("sumResult"));
    assertEquals(15, context.get("libResult"));
  }

  @Test
  void shouldHandleBarrelExports(@TempDir Path tempDir) throws IOException {
    Path utilsDir = Files.createDirectories(tempDir.resolve("utils"));
    Files.writeString(
        utilsDir.resolve("stringUtils.mjs"), "export const camelCase = (s) => s.toLowerCase();");
    Files.writeString(utilsDir.resolve("index.mjs"), "export * from './stringUtils.mjs';");

    Path mainPath = tempDir.resolve("main.mjs");
    Files.writeString(
        mainPath,
        """
        import { camelCase } from './utils/index.mjs';
        ctx.put('formatted', camelCase('HELLO'));
    """);

    WorkflowContext context = new WorkflowContext();
    createBuilder(mainPath).build().execute(context);

    assertEquals("hello", context.get("formatted"));
  }

  @Test
  void shouldShowDetailedTraceForCircularReferenceError(@TempDir Path tempDir) throws IOException {
    // a.mjs tries to use b.mjs immediately at the top level
    Path pathA = tempDir.resolve("a.mjs");
    Files.writeString(
        pathA,
        """
        import { bValue } from './b.mjs';
        export const aValue = 'A';
        // This will fail because b.mjs is still waiting for a.mjs to initialize aValue
        console.log(bValue);
    """);

    Path pathB = tempDir.resolve("b.mjs");
    Files.writeString(
        pathB,
        """
        import { aValue } from './a.mjs';
        export const bValue = aValue + 'B';
    """);

    Path mainPath = tempDir.resolve("main.mjs");
    Files.writeString(mainPath, "import './a.mjs';");

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = createBuilder(mainPath).build().execute(context);

    assertFalse(result.isSuccess());
  }
}
