package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import java.nio.file.Path;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
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
    assertInstanceOf(Value.class, out);
    Value value = (Value) out;
    assertTrue(value.toString().contains("greeting: \"hello\""));
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
