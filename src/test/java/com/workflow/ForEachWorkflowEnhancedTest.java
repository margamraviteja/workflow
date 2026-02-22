package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.task.NoOpTask;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for ForEachWorkflow Increases coverage for ForEachWorkflow class */
@DisplayName("ForEachWorkflow - Enhanced Tests")
class ForEachWorkflowEnhancedTest {

  private WorkflowContext context;

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();
  }

  @Nested
  @DisplayName("Basic ForEach Tests")
  class BasicForEachTests {

    @Test
    @DisplayName("ForEach with list of strings")
    void testForEachWithStringList() {
      List<String> items = Arrays.asList("apple", "banana", "cherry");
      context.put("items", items);

      AtomicInteger count = new AtomicInteger(0);

      Workflow itemWorkflow =
          new TaskWorkflow(
              ctx -> {
                String item = ctx.getTyped("item", String.class);
                assertNotNull(item);
                count.incrementAndGet();
              });

      ForEachWorkflow workflow =
          ForEachWorkflow.builder()
              .name("StringForEach")
              .itemsKey("items")
              .itemVariable("item")
              .workflow(itemWorkflow)
              .build();

      WorkflowResult result = workflow.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(3, count.get());
    }

    @Test
    @DisplayName("ForEach with list of integers")
    void testForEachWithIntegerList() {
      List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
      context.put("numbers", numbers);
      List<Integer> processed = new ArrayList<>();

      Workflow numberWorkflow =
          new TaskWorkflow(
              ctx -> {
                Integer num = ctx.getTyped("number", Integer.class);
                processed.add(num * 2);
              });

      ForEachWorkflow workflow =
          ForEachWorkflow.builder()
              .name("NumberForEach")
              .itemsKey("numbers")
              .itemVariable("number")
              .workflow(numberWorkflow)
              .build();

      WorkflowResult result = workflow.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(Arrays.asList(2, 4, 6, 8, 10), processed);
    }

    @Test
    @DisplayName("ForEach with list of maps")
    void testForEachWithMapList() {
      List<Map<String, Object>> users =
          Arrays.asList(
              Map.of("id", 1, "name", "Alice"),
              Map.of("id", 2, "name", "Bob"),
              Map.of("id", 3, "name", "Charlie"));
      context.put("users", users);

      List<String> names = new ArrayList<>();

      Workflow userWorkflow =
          new TaskWorkflow(
              ctx -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> user = ctx.getTyped("user", Map.class);
                names.add((String) user.get("name"));
              });

      ForEachWorkflow workflow =
          ForEachWorkflow.builder()
              .name("UserForEach")
              .itemsKey("users")
              .itemVariable("user")
              .workflow(userWorkflow)
              .build();

      WorkflowResult result = workflow.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(Arrays.asList("Alice", "Bob", "Charlie"), names);
    }

    @Test
    @DisplayName("ForEach with empty list")
    void testForEachWithEmptyList() {
      List<String> emptyList = Collections.emptyList();
      context.put("emptyList", emptyList);

      Workflow itemWorkflow = new TaskWorkflow(_ -> fail("Should not be called for empty list"));

      ForEachWorkflow workflow =
          ForEachWorkflow.builder()
              .name("EmptyForEach")
              .itemsKey("emptyList")
              .itemVariable("item")
              .workflow(itemWorkflow)
              .build();

      WorkflowResult result = workflow.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    }

    @Test
    @DisplayName("ForEach with single item")
    void testForEachWithSingleItem() {
      List<String> singleItem = Collections.singletonList("only");
      context.put("singleItem", singleItem);
      AtomicInteger count = new AtomicInteger(0);

      Workflow itemWorkflow = new TaskWorkflow(_ -> count.incrementAndGet());

      ForEachWorkflow workflow =
          ForEachWorkflow.builder()
              .name("SingleItemForEach")
              .itemsKey("singleItem")
              .itemVariable("item")
              .workflow(itemWorkflow)
              .build();

      WorkflowResult result = workflow.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(1, count.get());
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("ForEach with one failing item")
    void testForEachWithFailingItem() {
      List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
      context.put("numbers", numbers);

      Workflow workflow =
          new TaskWorkflow(
              ctx -> {
                Integer num = ctx.getTyped("num", Integer.class);
                if (num == 3) {
                  throw new RuntimeException("Failed on 3");
                }
              });

      ForEachWorkflow forEach =
          ForEachWorkflow.builder()
              .name("FailingItemForEach")
              .itemsKey("numbers")
              .itemVariable("num")
              .workflow(workflow)
              .build();

      WorkflowResult result = forEach.execute(context);
      assertEquals(WorkflowStatus.FAILED, result.getStatus());
      assertNotNull(result.getError());
    }

    @Test
    @DisplayName("ForEach with all items failing")
    void testForEachWithAllItemsFailing() {
      List<String> items = Arrays.asList("a", "b", "c");
      context.put("items", items);

      Workflow workflow =
          new TaskWorkflow(
              _ -> {
                throw new RuntimeException("Always fails");
              });

      ForEachWorkflow forEach =
          ForEachWorkflow.builder()
              .name("AllFailingForEach")
              .itemsKey("items")
              .itemVariable("item")
              .workflow(workflow)
              .build();

      WorkflowResult result = forEach.execute(context);
      assertEquals(WorkflowStatus.FAILED, result.getStatus());
    }
  }

  @Nested
  @DisplayName("Context Tests")
  class ContextTests {

    @Test
    @DisplayName("ForEach with context data access")
    void testForEachWithContextData() {
      context.put("multiplier", 10);
      List<Integer> numbers = Arrays.asList(1, 2, 3);
      context.put("numbers", numbers);
      List<Integer> results = new ArrayList<>();

      Workflow workflow =
          new TaskWorkflow(
              ctx -> {
                Integer num = ctx.getTyped("num", Integer.class);
                Integer multiplier = ctx.getTyped("multiplier", Integer.class);
                results.add(num * multiplier);
              });

      ForEachWorkflow forEach =
          ForEachWorkflow.builder()
              .name("ContextDataForEach")
              .itemsKey("numbers")
              .itemVariable("num")
              .workflow(workflow)
              .build();

      WorkflowResult result = forEach.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(Arrays.asList(10, 20, 30), results);
    }

    @Test
    @DisplayName("ForEach with context modifications")
    void testForEachWithContextModifications() {
      List<String> items = Arrays.asList("a", "b", "c");
      context.put("items", items);

      Workflow workflow =
          new TaskWorkflow(
              ctx -> {
                String item = ctx.getTyped("item", String.class);
                ctx.put("last_processed", item);
              });

      ForEachWorkflow forEach =
          ForEachWorkflow.builder()
              .name("ContextModForEach")
              .itemsKey("items")
              .itemVariable("item")
              .workflow(workflow)
              .build();

      WorkflowResult result = forEach.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals("c", context.get("last_processed"));
    }

    @Test
    @DisplayName("ForEach with scoped context")
    void testForEachWithScopedContext() {
      List<Integer> numbers = Arrays.asList(1, 2, 3);
      context.put("numbers", numbers);

      Workflow workflow =
          new TaskWorkflow(
              ctx -> {
                Integer num = ctx.getTyped("num", Integer.class);
                WorkflowContext scoped = ctx.scope("iteration_" + num);
                scoped.put("value", num * 2);
              });

      ForEachWorkflow forEach =
          ForEachWorkflow.builder()
              .name("ScopedContextForEach")
              .itemsKey("numbers")
              .itemVariable("num")
              .workflow(workflow)
              .build();

      WorkflowResult result = forEach.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(2, context.get("iteration_1.value"));
      assertEquals(4, context.get("iteration_2.value"));
      assertEquals(6, context.get("iteration_3.value"));
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("ForEach with null items list")
    void testForEachWithNullItems() {
      TaskWorkflow taskWorkflow = new TaskWorkflow(new NoOpTask());
      ForEachWorkflow.ForEachWorkflowBuilder builder =
          ForEachWorkflow.builder()
              .name("NullItemsForEach")
              .itemsKey(null)
              .itemVariable("item")
              .workflow(taskWorkflow);
      assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    @DisplayName("ForEach with large list")
    void testForEachWithLargeList() {
      List<Integer> largeList = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        largeList.add(i);
      }
      context.put("largeList", largeList);

      AtomicInteger count = new AtomicInteger(0);

      Workflow workflow = new TaskWorkflow(_ -> count.incrementAndGet());

      ForEachWorkflow forEach =
          ForEachWorkflow.builder()
              .name("LargeListForEach")
              .itemsKey("largeList")
              .itemVariable("item")
              .workflow(workflow)
              .build();

      WorkflowResult result = forEach.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertEquals(1000, count.get());
    }

    @Test
    @DisplayName("ForEach with complex objects")
    void testForEachWithComplexObjects() {
      class User {
        final String name;
        final int age;

        User(String name, int age) {
          this.name = name;
          this.age = age;
        }
      }

      List<User> users =
          Arrays.asList(new User("Alice", 30), new User("Bob", 25), new User("Charlie", 35));
      context.put("users", users);

      List<String> processedNames = new ArrayList<>();

      Workflow workflow =
          new TaskWorkflow(
              ctx -> {
                User user = ctx.getTyped("user", User.class);
                processedNames.add(user.name + "_" + user.age);
              });

      ForEachWorkflow forEach =
          ForEachWorkflow.builder()
              .name("ComplexObjectForEach")
              .itemsKey("users")
              .itemVariable("user")
              .workflow(workflow)
              .build();

      WorkflowResult result = forEach.execute(context);
      assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
      assertTrue(processedNames.contains("Alice_30"));
      assertTrue(processedNames.contains("Bob_25"));
      assertTrue(processedNames.contains("Charlie_35"));
    }
  }
}
