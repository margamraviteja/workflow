package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;

class JavaScriptTaskTest {

  @Test
  void testSimpleReturn() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("x", 10);
    Map<String, String> bindings = Map.of("x", "xVar");

    JavaScriptTask t =
        JavaScriptTask.builder()
            .scriptText("xVar + 5")
            .inputBindings(bindings)
            .outputKey("out")
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();
    t.execute(ctx);

    Object out = ctx.get("out");
    assertInstanceOf(Number.class, out);
    assertEquals(15L, ((Number) out).longValue());
  }

  @Test
  void testJsonResultForObject() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("name", "alice");
    String script = "({greeting: 'hello', who: name})";

    JavaScriptTask t =
        JavaScriptTask.builder()
            .scriptText(script)
            .inputBindings(Map.of("name", "name"))
            .outputKey("outJson")
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();
    t.execute(ctx);

    Object out = ctx.get("outJson");
    assertInstanceOf(Map.class, out);
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) out;
    assertEquals("hello", map.get("greeting"));
    assertEquals("alice", map.get("who"));
  }

  @Test
  void testScriptFile1() {
    // Bindings: context key -> JS variable name
    Map<String, String> bindings = Map.of("x", "xVar", "y", "yVar");

    JavaScriptTask jsTask =
        JavaScriptTask.builder()
            .scriptFile(Path.of("src/test/resources/sample.js"))
            .inputBindings(bindings)
            .outputKey("out")
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("x", 7);
    ctx.put("y", 5);
    jsTask.execute(ctx);

    Object out = ctx.get("out");
    assertInstanceOf(Number.class, out);
    assertEquals(12L, ((Number) out).longValue());
  }

  @Test
  void testScriptFile2() {
    // Bindings: context key -> JS variable name
    Map<String, String> bindings = Map.of("price", "price", "isPremium", "isPremium");

    JavaScriptTask jsTask =
        JavaScriptTask.builder()
            .scriptFile(Path.of("src/test/resources/rules.js"))
            .inputBindings(bindings)
            .outputKey("discount")
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("price", 100);
    ctx.put("isPremium", true);
    jsTask.execute(ctx);

    Object out = ctx.get("discount");
    assertInstanceOf(Number.class, out);
    assertEquals(80L, ((Number) out).longValue());
  }

  @Test
  void testScriptFileNotFound() {
    WorkflowContext ctx = new WorkflowContext();
    JavaScriptTask t =
        JavaScriptTask.builder().scriptFile(Path.of("nonexistent.js")).outputKey("o").build();
    assertThrows(TaskExecutionException.class, () -> t.execute(ctx));
  }

  @Test
  void testWithJavaObjects() {
    User user = new User("Alex", 20);
    Map<String, String> bindings = Map.of("user", "user");

    JavaScriptTask jsTask =
        JavaScriptTask.builder()
            .scriptText("user.isAdult()")
            .inputBindings(bindings)
            .outputKey("out")
            .polyglot(Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build())
            .build();
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("user", user);
    jsTask.execute(ctx);

    Object out = ctx.get("out");
    assertInstanceOf(Boolean.class, out);
    assertTrue((Boolean) out);
  }

  // ==================== New Tests for unpackResult Feature ====================

  @Test
  void testUnpackResult_SimpleObject() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("a", 10);
    ctx.put("b", 20);

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText("({ sum: a + b, product: a * b, difference: b - a })")
            .inputBindings(Map.of("a", "a", "b", "b"))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    task.execute(ctx);

    assertEquals(30L, ctx.get("sum"));
    assertEquals(200L, ctx.get("product"));
    assertEquals(10L, ctx.get("difference"));
  }

  @Test
  void testUnpackResult_ComplexCalculations() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("price", 100);
    ctx.put("quantity", 5);

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText(
                "({ "
                    + "  subtotal: price * quantity, "
                    + "  tax: price * quantity * 0.1, "
                    + "  total: price * quantity * 1.1, "
                    + "  discount: price * quantity > 400 ? 50 : 0 "
                    + "})")
            .inputBindings(Map.of("price", "price", "quantity", "quantity"))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(true).build())
            .build();

    task.execute(ctx);

    assertEquals(500L, ctx.get("subtotal"));
    assertEquals(50L, ctx.get("tax"));
    assertEquals(550L, ctx.get("total"));
    assertEquals(50L, ctx.get("discount"));
  }

  @Test
  void testUnpackResult_StringAndNumbers() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("name", "John");
    ctx.put("age", 30);

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText(
                "({ "
                    + "  fullName: name + ' Doe', "
                    + "  ageNextYear: age + 1, "
                    + "  isAdult: age >= 18, "
                    + "  category: age >= 65 ? 'senior' : age >= 18 ? 'adult' : 'minor' "
                    + "})")
            .inputBindings(Map.of("name", "name", "age", "age"))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    task.execute(ctx);

    assertEquals("John Doe", ctx.get("fullName"));
    assertEquals(31L, ctx.get("ageNextYear"));
    assertTrue((Boolean) ctx.get("isAdult"));
    assertEquals("adult", ctx.get("category"));
  }

  @Test
  void testUnpackResult_ArraysAndObjects() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("items", List.of(1, 2, 3, 4, 5));

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText(
                "({ "
                    + "  doubled: items.map(x => x * 2), "
                    + "  sum: items.reduce((a, b) => a + b, 0), "
                    + "  count: items.length, "
                    + "  evens: items.filter(x => x % 2 === 0) "
                    + "})")
            .inputBindings(Map.of("items", "items"))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(true).build())
            .build();

    task.execute(ctx);

    @SuppressWarnings("unchecked")
    List<Object> doubled = (List<Object>) ctx.get("doubled");
    assertEquals(List.of(2L, 4L, 6L, 8L, 10L), doubled);

    assertEquals(15L, ctx.get("sum"));
    assertEquals(5L, ctx.get("count"));

    @SuppressWarnings("unchecked")
    List<Object> evens = (List<Object>) ctx.get("evens");
    assertEquals(List.of(2L, 4L), evens);
  }

  @Test
  void testUnpackResult_NestedObjects() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("user", Map.of("name", "Alice", "age", 25, "city", "NYC"));

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText(
                "({ "
                    + "  userName: user.name, "
                    + "  userAge: user.age, "
                    + "  location: user.city, "
                    + "  profile: { name: user.name, age: user.age } "
                    + "})")
            .inputBindings(Map.of("user", "user"))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(true).build())
            .build();

    task.execute(ctx);

    assertEquals("Alice", ctx.get("userName"));
    assertEquals(25L, ctx.get("userAge"));
    assertEquals("NYC", ctx.get("location"));

    @SuppressWarnings("unchecked")
    Map<String, Object> profile = (Map<String, Object>) ctx.get("profile");
    assertEquals("Alice", profile.get("name"));
    assertEquals(25L, profile.get("age"));
  }

  @Test
  void testUnpackResult_DataAnalysis() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put(
        "orders",
        List.of(
            Map.of("id", 1, "amount", 100.0, "status", "paid"),
            Map.of("id", 2, "amount", 200.0, "status", "pending"),
            Map.of("id", 3, "amount", 150.0, "status", "paid"),
            Map.of("id", 4, "amount", 75.0, "status", "cancelled")));

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText(
                "const paid = orders.filter(o => o.status === 'paid');"
                    + "const pending = orders.filter(o => o.status === 'pending');"
                    + "const cancelled = orders.filter(o => o.status === 'cancelled');"
                    + "({ "
                    + "  paidCount: paid.length, "
                    + "  paidTotal: paid.reduce((sum, o) => sum + o.amount, 0), "
                    + "  pendingCount: pending.length, "
                    + "  pendingTotal: pending.reduce((sum, o) => sum + o.amount, 0), "
                    + "  cancelledCount: cancelled.length, "
                    + "  totalOrders: orders.length, "
                    + "  averageOrderValue: orders.reduce((sum, o) => sum + o.amount, 0) / orders.length "
                    + "})")
            .inputBindings(Map.of("orders", "orders"))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(true).build())
            .build();

    task.execute(ctx);

    assertEquals(2L, ctx.get("paidCount"));
    assertEquals(250L, ctx.get("paidTotal"));
    assertEquals(1L, ctx.get("pendingCount"));
    assertEquals(200L, ctx.get("pendingTotal"));
    assertEquals(1L, ctx.get("cancelledCount"));
    assertEquals(4L, ctx.get("totalOrders"));
    assertEquals(131.25, ctx.get("averageOrderValue"));
  }

  @Test
  void testUnpackResult_NullHandling() {
    WorkflowContext ctx = new WorkflowContext();

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText("null")
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    // Should not throw, just log warning
    assertDoesNotThrow(() -> task.execute(ctx));
  }

  @Test
  void testUnpackResult_ThrowsOnPrimitive() {
    WorkflowContext ctx = new WorkflowContext();

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText("42")
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(true).build())
            .build();

    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertTrue(exception.getMessage().contains("must be an object"));
  }

  @Test
  void testUnpackResult_ThrowsOnString() {
    WorkflowContext ctx = new WorkflowContext();

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText("'hello world'")
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertTrue(exception.getMessage().contains("must be an object"));
  }

  @Test
  void testUnpackResult_WithOutputKey_ThrowsException() {
    WorkflowContext ctx = new WorkflowContext();

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText("({ a: 1, b: 2 })")
            .outputKey("result")
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertTrue(
        exception.getMessage().contains("Cannot specify both unpackResult=true and outputKey"));
  }

  @Test
  void testUnpackResult_False_RequiresOutputKey() {
    WorkflowContext ctx = new WorkflowContext();

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText("42")
            .unpackResult(false)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(ctx));
    assertTrue(exception.getMessage().contains("outputKey must be provided"));
  }

  @Test
  void testUnpackResult_FromFile() throws Exception {
    // Create a temporary script file
    Path tempScript = Path.of("src/test/resources/unpack-test.js");
    String scriptContent =
        "({ "
            + "  result1: input * 2, "
            + "  result2: input * 3, "
            + "  result3: input * 4 "
            + "})";

    java.nio.file.Files.writeString(tempScript, scriptContent);

    try {
      WorkflowContext ctx = new WorkflowContext();
      ctx.put("input", 5);

      JavaScriptTask task =
          JavaScriptTask.builder()
              .scriptFile(tempScript)
              .inputBindings(Map.of("input", "input"))
              .unpackResult(true)
              .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
              .build();

      task.execute(ctx);

      assertEquals(10L, ctx.get("result1"));
      assertEquals(15L, ctx.get("result2"));
      assertEquals(20L, ctx.get("result3"));
    } finally {
      // Clean up
      java.nio.file.Files.deleteIfExists(tempScript);
    }
  }

  @Test
  void testBackwardCompatibility_OutputKeyStillWorks() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("x", 10);

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText("({ a: x, b: x * 2 })")
            .inputBindings(Map.of("x", "x"))
            .outputKey("result")
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    task.execute(ctx);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) ctx.get("result");
    assertNotNull(result);
    assertEquals(10L, result.get("a"));
    assertEquals(20L, result.get("b"));

    // Keys should NOT be unpacked into context
    assertNull(ctx.get("a"));
    assertNull(ctx.get("b"));
  }

  @Test
  void testUnpackResult_EmptyObject() {
    WorkflowContext ctx = new WorkflowContext();

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText("({})")
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    // Should not throw, just unpack zero values
    assertDoesNotThrow(() -> task.execute(ctx));
  }

  @Test
  void testUnpackResult_ManyKeys() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("base", 10);

    JavaScriptTask task =
        JavaScriptTask.builder()
            .scriptText(
                "({ "
                    + "  key1: base + 1, "
                    + "  key2: base + 2, "
                    + "  key3: base + 3, "
                    + "  key4: base + 4, "
                    + "  key5: base + 5, "
                    + "  key6: base + 6, "
                    + "  key7: base + 7, "
                    + "  key8: base + 8, "
                    + "  key9: base + 9, "
                    + "  key10: base + 10 "
                    + "})")
            .inputBindings(Map.of("base", "base"))
            .unpackResult(true)
            .polyglot(Context.newBuilder("js").allowAllAccess(false).build())
            .build();

    task.execute(ctx);

    assertEquals(11L, ctx.get("key1"));
    assertEquals(12L, ctx.get("key2"));
    assertEquals(13L, ctx.get("key3"));
    assertEquals(14L, ctx.get("key4"));
    assertEquals(15L, ctx.get("key5"));
    assertEquals(16L, ctx.get("key6"));
    assertEquals(17L, ctx.get("key7"));
    assertEquals(18L, ctx.get("key8"));
    assertEquals(19L, ctx.get("key9"));
    assertEquals(20L, ctx.get("key10"));
  }

  public static class User {
    public String name;
    public int age;

    public User(String name, int age) {
      this.name = name;
      this.age = age;
    }

    public boolean isAdult() {
      return age >= 18;
    }
  }
}
