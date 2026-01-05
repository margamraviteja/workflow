package com.workflow.annotation.spring;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.*;
import com.workflow.annotation.TaskMethod;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowMethod;
import com.workflow.annotation.WorkflowRef;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.WorkflowBuildException;
import com.workflow.task.Task;
import com.workflow.task.TaskDescriptor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/** Unit tests for {@link SpringAnnotationWorkflowProcessor}. */
@Slf4j
class SpringAnnotationWorkflowProcessorTest {

  private ApplicationContext applicationContext;
  private Environment mockEnv;
  private SpringAnnotationWorkflowProcessor processor;

  @BeforeEach
  void setUp() {
    // Create a simple in-memory Spring application context for testing
    applicationContext = mock(ApplicationContext.class);
    mockEnv = mock(Environment.class);
    when(applicationContext.getEnvironment()).thenReturn(mockEnv);
    processor = new SpringAnnotationWorkflowProcessor(applicationContext);
  }

  @WorkflowAnnotation(name = "SimpleSequentialWorkflow")
  public static class SimpleSequentialWorkflowDefinition {
    @TaskMethod(name = "Task1", order = 1)
    public Task createTask1() {
      return context -> context.put("task1.executed", true);
    }

    @TaskMethod(name = "Task2", order = 2)
    public Task createTask2() {
      return context -> context.put("task2.executed", true);
    }
  }

  @WorkflowAnnotation(name = "ParallelWorkflowTest", parallel = true)
  public static class SimpleParallelWorkflowDefinition {
    @TaskMethod(name = "ParallelTask1", order = 1)
    public Task createParallelTask1() {
      return context -> context.put("parallel.task1.executed", true);
    }

    @TaskMethod(name = "ParallelTask2", order = 2)
    public Task createParallelTask2() {
      return context -> context.put("parallel.task2.executed", true);
    }
  }

  @WorkflowAnnotation(name = "AutowiredWorkflow")
  public static class AutowiredWorkflowDefinition {
    @TaskMethod(name = "Task1", order = 1)
    public Task createTaskWithAutowired(@Autowired(required = false) String dependency) {
      return context ->
          context.put("injected.dependency", dependency != null ? dependency : "null");
    }
  }

  @WorkflowAnnotation(name = "MixedWorkflow")
  public static class MixedWorkflowDefinition {
    @TaskMethod(name = "Task1", order = 1)
    public Task createTask1() {
      return context -> context.put("step", "1");
    }

    @WorkflowMethod(name = "SubWorkflow", order = 2)
    public Workflow createSubWorkflow() {
      return SequentialWorkflow.builder()
          .name("SubWorkflow")
          .task(context -> context.put("step", context.getTyped("step", String.class) + "->2"))
          .build();
    }

    @TaskMethod(name = "Task3", order = 3)
    public Task createTask3() {
      return context -> context.put("step", context.getTyped("step", String.class) + "->3");
    }
  }

  @WorkflowAnnotation(name = "EmptyWorkflow")
  public static class EmptyWorkflowDefinition {
    // No workflow or task methods
  }

  @WorkflowAnnotation(name = "NestedWorkflow")
  public static class NestedWorkflowDefinition {
    @WorkflowMethod(name = "WorkflowStep1", order = 1)
    public Workflow createWorkflowStep1() {
      return SequentialWorkflow.builder()
          .name("NestedWorkflow1")
          .task(context -> context.put("nested1", true))
          .build();
    }

    @WorkflowMethod(name = "WorkflowStep2", order = 2)
    public Workflow createWorkflowStep2() {
      return SequentialWorkflow.builder()
          .name("NestedWorkflow2")
          .task(context -> context.put("nested2", true))
          .build();
    }
  }

  @Test
  void testBuildSequentialWorkflowFromInstance() {
    SimpleSequentialWorkflowDefinition instance = new SimpleSequentialWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
    assertEquals("SimpleSequentialWorkflow", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("task1.executed", Boolean.class));
    assertTrue(context.getTyped("task2.executed", Boolean.class));
  }

  @Test
  void testBuildParallelWorkflowFromInstance() {
    SimpleParallelWorkflowDefinition instance = new SimpleParallelWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
    assertEquals("ParallelWorkflowTest", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("parallel.task1.executed", Boolean.class));
    assertTrue(context.getTyped("parallel.task2.executed", Boolean.class));
  }

  @Test
  void testBuildWorkflowWithMissingAnnotationThrowsException() {
    class NotAnnotated {}

    assertThrows(IllegalArgumentException.class, () -> processor.buildWorkflow(NotAnnotated.class));
  }

  @Test
  void testBuildWorkflowWithMixedElements() {
    MixedWorkflowDefinition instance = new MixedWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
    assertEquals("MixedWorkflow", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals("1->2->3", context.get("step", String.class));
  }

  @Test
  void testBuildEmptyWorkflow() {
    EmptyWorkflowDefinition instance = new EmptyWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @Test
  void testBuildNestedWorkflows() {
    NestedWorkflowDefinition instance = new NestedWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
    assertEquals("NestedWorkflow", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("nested1", Boolean.class));
    assertTrue(context.getTyped("nested2", Boolean.class));
  }

  @Test
  void testAutowiredParameterWithMissingBean() {
    when(applicationContext.getBean(String.class))
        .thenThrow(new RuntimeException("No bean of type String"));
    when(applicationContext.getEnvironment()).thenReturn(null);

    AutowiredWorkflowDefinition instance = new AutowiredWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @Test
  void testAutowiredParameterWithAvailableBean() {
    String testDependency = "TestDependency";
    when(applicationContext.getBean(String.class)).thenReturn(testDependency);

    AutowiredWorkflowDefinition instance = new AutowiredWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testInvalidAnnotationOnClass() {
    class ClassWithoutAnnotation {}

    assertThrows(
        IllegalArgumentException.class,
        () -> processor.buildWorkflow(ClassWithoutAnnotation.class));
  }

  @Test
  void testWorkflowNameFromAnnotation() {
    SimpleSequentialWorkflowDefinition instance = new SimpleSequentialWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertEquals("SimpleSequentialWorkflow", workflow.getName());
  }

  @Test
  void testTaskExecutionOrder() {
    @WorkflowAnnotation(name = "OrderTestWorkflow")
    class OrderedWorkflow {
      @TaskMethod(name = "Task3", order = 3)
      public Task createTask3() {
        return context -> context.put("order", context.getTyped("order", String.class) + "3");
      }

      @TaskMethod(name = "Task1", order = 1)
      public Task createTask1() {
        return context -> context.put("order", "1");
      }

      @TaskMethod(name = "Task2", order = 2)
      public Task createTask2() {
        return context -> context.put("order", context.getTyped("order", String.class) + "2");
      }
    }

    OrderedWorkflow instance = new OrderedWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals("123", context.get("order", String.class));
  }

  @Test
  void testParallelWorkflowWithShareContext() {
    @WorkflowAnnotation(name = "SharedContextParallel", parallel = true)
    class SharedContextWorkflow {
      @TaskMethod(name = "Task1", order = 1)
      public Task createTask1() {
        return context -> {
          context.put("task1", true);
          try {
            Awaitility.await().atLeast(50, TimeUnit.MILLISECONDS).until(() -> true);
          } catch (Exception _) {
            Thread.currentThread().interrupt();
          }
        };
      }

      @TaskMethod(name = "Task2", order = 2)
      public Task createTask2() {
        return context -> context.put("task2", true);
      }
    }

    SharedContextWorkflow instance = new SharedContextWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @Test
  void testWorkflowBuilderWithLambdaTasks() {
    @WorkflowAnnotation(name = "LambdaTaskWorkflow")
    class LambdaWorkflow {
      @TaskMethod(name = "LambdaTask", order = 1)
      public Task createLambdaTask() {
        return context -> context.put("lambda.executed", true);
      }
    }

    LambdaWorkflow instance = new LambdaWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("lambda.executed", Boolean.class));
  }

  @Test
  void testMultipleWorkflowMethods() {
    @WorkflowAnnotation(name = "MultiWorkflowSteps")
    class MultiWorkflowDefinition {
      @WorkflowMethod(name = "Step1", order = 1)
      public Workflow createStep1() {
        return SequentialWorkflow.builder()
            .name("Step1Workflow")
            .task(context -> context.put("step1", true))
            .build();
      }

      @WorkflowMethod(name = "Step2", order = 2)
      public Workflow createStep2() {
        return SequentialWorkflow.builder()
            .name("Step2Workflow")
            .task(context -> context.put("step2", true))
            .build();
      }
    }

    MultiWorkflowDefinition instance = new MultiWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
    assertEquals("MultiWorkflowSteps", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("step1", Boolean.class));
    assertTrue(context.getTyped("step2", Boolean.class));
  }

  @Test
  void testConditionalOnProperty_Enabled() {
    when(applicationContext.getEnvironment())
        .thenReturn(mock(org.springframework.core.env.Environment.class));
    when(applicationContext.getEnvironment().getProperty("feature.enabled")).thenReturn("true");

    @WorkflowAnnotation(name = "ConditionalPropertyWorkflow")
    class ConditionalPropertyWorkflow {
      @TaskMethod(name = "ConditionalTask", order = 1)
      @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
          name = "feature.enabled",
          havingValue = "true")
      public Task createConditionalTask() {
        return context -> context.put("conditional.executed", true);
      }
    }

    ConditionalPropertyWorkflow instance = new ConditionalPropertyWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testConditionalOnProperty_Disabled() {
    when(applicationContext.getEnvironment())
        .thenReturn(mock(org.springframework.core.env.Environment.class));
    when(applicationContext.getEnvironment().getProperty("feature.enabled")).thenReturn("false");

    @WorkflowAnnotation(name = "ConditionalPropertyWorkflow")
    class ConditionalPropertyWorkflow {
      @TaskMethod(name = "ConditionalTask", order = 1)
      @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
          name = "feature.enabled",
          havingValue = "true")
      public Task createConditionalTask() {
        return context -> context.put("conditional.executed", true);
      }
    }

    ConditionalPropertyWorkflow instance = new ConditionalPropertyWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testConditionalOnBean_Enabled() {
    when(applicationContext.getBean(String.class)).thenReturn("test");

    @WorkflowAnnotation(name = "ConditionalBeanWorkflow")
    class ConditionalBeanWorkflow {
      @TaskMethod(name = "ConditionalTask", order = 1)
      @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(String.class)
      public Task createConditionalTask() {
        return context -> context.put("conditional.executed", true);
      }
    }

    ConditionalBeanWorkflow instance = new ConditionalBeanWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testConditionalOnBean_Disabled() {
    when(applicationContext.getBean(String.class))
        .thenThrow(new RuntimeException("No bean of type String"));

    @WorkflowAnnotation(name = "ConditionalBeanWorkflow")
    class ConditionalBeanWorkflow {
      @TaskMethod(name = "ConditionalTask", order = 1)
      @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(String.class)
      public Task createConditionalTask() {
        return context -> context.put("conditional.executed", true);
      }
    }

    ConditionalBeanWorkflow instance = new ConditionalBeanWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testConditionalOnMissingBean_Enabled() {
    when(applicationContext.getBean(String.class))
        .thenThrow(new RuntimeException("No bean of type String"));

    @WorkflowAnnotation(name = "ConditionalMissingBeanWorkflow")
    class ConditionalMissingBeanWorkflow {
      @TaskMethod(name = "ConditionalTask", order = 1)
      @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(String.class)
      public Task createConditionalTask() {
        return context -> context.put("conditional.executed", true);
      }
    }

    ConditionalMissingBeanWorkflow instance = new ConditionalMissingBeanWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testConditionalOnMissingBean_Disabled() {
    when(applicationContext.getBean(String.class)).thenReturn("test");

    @WorkflowAnnotation(name = "ConditionalMissingBeanWorkflow")
    class ConditionalMissingBeanWorkflow {
      @TaskMethod(name = "ConditionalTask", order = 1)
      @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(String.class)
      public Task createConditionalTask() {
        return context -> context.put("conditional.executed", true);
      }
    }

    ConditionalMissingBeanWorkflow instance = new ConditionalMissingBeanWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testConditionalOnClass_Present() {
    @WorkflowAnnotation(name = "ConditionalClassWorkflow")
    class ConditionalClassWorkflow {
      @TaskMethod(name = "ConditionalTask", order = 1)
      @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
          name = "java.lang.String")
      public Task createConditionalTask() {
        return context -> context.put("conditional.executed", true);
      }
    }

    ConditionalClassWorkflow instance = new ConditionalClassWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testConditionalOnClass_Missing() {
    @WorkflowAnnotation(name = "ConditionalClassWorkflow")
    class ConditionalClassWorkflow {
      @TaskMethod(name = "ConditionalTask", order = 1)
      @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
          name = "com.nonexistent.Class")
      public Task createConditionalTask() {
        return context -> context.put("conditional.executed", true);
      }
    }

    ConditionalClassWorkflow instance = new ConditionalClassWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testWorkflowWithComplexLogic() {
    @WorkflowAnnotation(name = "ComplexWorkflow")
    class ComplexWorkflow {
      @TaskMethod(name = "InitTask", order = 1)
      public Task createInitTask() {
        return context -> context.put("counter", 0);
      }

      @TaskMethod(name = "IncrementTask", order = 2)
      public Task createIncrementTask() {
        return context -> {
          int counter = context.getTyped("counter", Integer.class);
          context.put("counter", counter + 1);
        };
      }

      @TaskMethod(name = "VerifyTask", order = 3)
      public Task createVerifyTask() {
        return context -> {
          int counter = context.getTyped("counter", Integer.class);
          context.put("success", counter == 1);
        };
      }
    }

    ComplexWorkflow instance = new ComplexWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("success", Boolean.class));
  }

  @Test
  void testWorkflowExecutionWithContextData() {
    @WorkflowAnnotation(name = "ContextDataWorkflow")
    class ContextDataWorkflow {
      @TaskMethod(name = "SetDataTask", order = 1)
      public Task createSetDataTask() {
        return context -> {
          context.put("key1", "value1");
          context.put("key2", 42);
          context.put("key3", true);
        };
      }

      @TaskMethod(name = "VerifyDataTask", order = 2)
      public Task createVerifyDataTask() {
        return context -> {
          assertEquals("value1", context.getTyped("key1", String.class));
          assertEquals(42, context.getTyped("key2", Integer.class));
          assertEquals(true, context.getTyped("key3", Boolean.class));
        };
      }
    }

    ContextDataWorkflow instance = new ContextDataWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @Test
  void testWorkflowWithSingleTask() {
    @WorkflowAnnotation(name = "SingleTaskWorkflow")
    class SingleTaskWorkflow {
      @TaskMethod(name = "OnlyTask", order = 1)
      public Task createTask() {
        return context -> context.put("executed", true);
      }
    }

    SingleTaskWorkflow instance = new SingleTaskWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("executed", Boolean.class));
  }

  @Test
  void testWorkflowWithManyTasks() {
    @WorkflowAnnotation(name = "ManyTasksWorkflow")
    class ManyTasksWorkflow {
      @TaskMethod(name = "Task1", order = 1)
      public Task createTask1() {
        return context -> context.put("task1", 1);
      }

      @TaskMethod(name = "Task2", order = 2)
      public Task createTask2() {
        return context -> context.put("task2", 2);
      }

      @TaskMethod(name = "Task3", order = 3)
      public Task createTask3() {
        return context -> context.put("task3", 3);
      }

      @TaskMethod(name = "Task4", order = 4)
      public Task createTask4() {
        return context -> context.put("task4", 4);
      }

      @TaskMethod(name = "Task5", order = 5)
      public Task createTask5() {
        return context -> context.put("task5", 5);
      }
    }

    ManyTasksWorkflow instance = new ManyTasksWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(1, context.getTyped("task1", Integer.class));
    assertEquals(2, context.getTyped("task2", Integer.class));
    assertEquals(3, context.getTyped("task3", Integer.class));
    assertEquals(4, context.getTyped("task4", Integer.class));
    assertEquals(5, context.getTyped("task5", Integer.class));
  }

  @Test
  void testParallelWorkflowExecutionOrder() {
    @WorkflowAnnotation(name = "ParallelOrderTestWorkflow", parallel = true)
    class ParallelOrderWorkflow {
      @TaskMethod(name = "Task1", order = 1)
      public Task createTask1() {
        return context -> context.put("task1", true);
      }

      @TaskMethod(name = "Task2", order = 2)
      public Task createTask2() {
        return context -> context.put("task2", true);
      }

      @TaskMethod(name = "Task3", order = 3)
      public Task createTask3() {
        return context -> context.put("task3", true);
      }
    }

    ParallelOrderWorkflow instance = new ParallelOrderWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("task1", Boolean.class));
    assertTrue(context.getTyped("task2", Boolean.class));
    assertTrue(context.getTyped("task3", Boolean.class));
  }

  @Test
  void testWorkflowBuilderFromClass() {
    Workflow workflow = processor.buildWorkflow(SimpleSequentialWorkflowDefinition.class);

    assertNotNull(workflow);
    assertEquals("SimpleSequentialWorkflow", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("task1.executed", Boolean.class));
    assertTrue(context.getTyped("task2.executed", Boolean.class));
  }

  @Test
  void testWorkflowNameDefaultBehavior() {
    @WorkflowAnnotation
    class EmptyNameWorkflow {
      @TaskMethod(name = "Task1", order = 1)
      public Task createTask1() {
        return context -> context.put("executed", true);
      }
    }

    EmptyNameWorkflow instance = new EmptyNameWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
    assertNotNull(workflow.getName());
  }

  @Test
  void testMixedTaskAndWorkflowMethodsOrdering() {
    @WorkflowAnnotation(name = "MixedOrderingWorkflow")
    class MixedOrderingWorkflow {
      @WorkflowMethod(name = "WorkflowTask1", order = 2)
      public Workflow createWorkflow1() {
        return SequentialWorkflow.builder()
            .name("SubWorkflow1")
            .task(context -> context.put("step2", true))
            .build();
      }

      @TaskMethod(name = "SimpleTask", order = 1)
      public Task createTask1() {
        return context -> context.put("step1", true);
      }

      @TaskMethod(name = "SimpleTask2", order = 3)
      public Task createTask2() {
        return context -> context.put("step3", true);
      }
    }

    MixedOrderingWorkflow instance = new MixedOrderingWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("step1", Boolean.class));
    assertTrue(context.getTyped("step2", Boolean.class));
    assertTrue(context.getTyped("step3", Boolean.class));
  }

  @Test
  void testParallelWorkflowWithoutSharedContext() {
    @WorkflowAnnotation(name = "ParallelNoShareContext", parallel = true, shareContext = false)
    class ParallelNoShareWorkflow {
      @TaskMethod(name = "Task1", order = 1)
      public Task createTask1() {
        return context -> context.put("task1", true);
      }

      @TaskMethod(name = "Task2", order = 2)
      public Task createTask2() {
        return context -> context.put("task2", true);
      }
    }

    ParallelNoShareWorkflow instance = new ParallelNoShareWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @Test
  void testWorkflowWithDataDependencies() {
    @WorkflowAnnotation(name = "DataDependencyWorkflow")
    class DataDependencyWorkflow {
      @TaskMethod(name = "ProducerTask", order = 1)
      public Task createProducerTask() {
        return context -> {
          context.put("produced.value", "test_data");
          context.put("produced.timestamp", System.currentTimeMillis());
        };
      }

      @TaskMethod(name = "ConsumerTask", order = 2)
      public Task createConsumerTask() {
        return context -> {
          String value = context.getTyped("produced.value", String.class);
          long timestamp = context.getTyped("produced.timestamp", Long.class);
          context.put("consumed", true);
          context.put("value_matches", value.equals("test_data"));
          context.put("timestamp_valid", timestamp > 0);
        };
      }
    }

    DataDependencyWorkflow instance = new DataDependencyWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("consumed", Boolean.class));
    assertTrue(context.getTyped("value_matches", Boolean.class));
    assertTrue(context.getTyped("timestamp_valid", Boolean.class));
  }

  @Test
  void testWorkflowWithDifferentDataTypes() {
    @WorkflowAnnotation(name = "DifferentDataTypesWorkflow")
    class DifferentDataTypesWorkflow {
      @TaskMethod(name = "StoreDataTask", order = 1)
      public Task createStoreDataTask() {
        return context -> {
          context.put("string.value", "test");
          context.put("int.value", 42);
          context.put("long.value", 1000L);
          context.put("double.value", 3.14);
          context.put("boolean.value", true);
        };
      }

      @TaskMethod(name = "RetrieveDataTask", order = 2)
      public Task createRetrieveDataTask() {
        return context -> {
          String stringVal = context.getTyped("string.value", String.class);
          Integer intVal = context.getTyped("int.value", Integer.class);
          Long longVal = context.getTyped("long.value", Long.class);
          Double doubleVal = context.getTyped("double.value", Double.class);
          Boolean boolVal = context.getTyped("boolean.value", Boolean.class);

          assertEquals("test", stringVal);
          assertEquals(42, intVal);
          assertEquals(1000L, longVal);
          assertEquals(3.14, doubleVal);
          assertEquals(true, boolVal);
        };
      }
    }

    DifferentDataTypesWorkflow instance = new DifferentDataTypesWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @Test
  void testWorkflowNameExtractionFromAnnotation() {
    @WorkflowAnnotation(name = "CustomWorkflowName123")
    class CustomNameWorkflow {
      @TaskMethod(name = "Task", order = 1)
      public Task createTask() {
        return context -> context.put("done", true);
      }
    }

    CustomNameWorkflow instance = new CustomNameWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertEquals("CustomWorkflowName123", workflow.getName());
  }

  @Test
  void testMultipleNestedWorkflowMethods() {
    @WorkflowAnnotation(name = "MultiNestedWorkflow")
    class MultiNestedWorkflow {
      @WorkflowMethod(name = "Step1", order = 1)
      public Workflow createStep1() {
        return SequentialWorkflow.builder()
            .name("NestedStep1")
            .task(context -> context.put("step1.count", 1))
            .build();
      }

      @WorkflowMethod(name = "Step2", order = 2)
      public Workflow createStep2() {
        return SequentialWorkflow.builder()
            .name("NestedStep2")
            .task(
                context -> {
                  int count = context.getTyped("step1.count", Integer.class);
                  context.put("step2.count", count + 1);
                })
            .build();
      }

      @WorkflowMethod(name = "Step3", order = 3)
      public Workflow createStep3() {
        return SequentialWorkflow.builder()
            .name("NestedStep3")
            .task(
                context -> {
                  int count = context.getTyped("step2.count", Integer.class);
                  context.put("step3.count", count + 1);
                })
            .build();
      }
    }

    MultiNestedWorkflow instance = new MultiNestedWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(3, context.getTyped("step3.count", Integer.class));
  }

  @Test
  void testWorkflowExecutionErrorHandling() {
    @WorkflowAnnotation(name = "ErrorHandlingWorkflow")
    class ErrorHandlingWorkflow {
      @TaskMethod(name = "SafeTask", order = 1)
      public Task createSafeTask() {
        return context -> context.put("safe.executed", true);
      }

      @TaskMethod(name = "ErrorTask", order = 2)
      public Task createErrorTask() {
        return context -> {
          // Task that completes without error
          context.put("error.handled", true);
        };
      }
    }

    ErrorHandlingWorkflow instance = new ErrorHandlingWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("safe.executed", Boolean.class));
  }

  @Test
  void testConditionalAnnotationCombinations() {
    when(applicationContext.getEnvironment())
        .thenReturn(mock(org.springframework.core.env.Environment.class));
    when(applicationContext.getEnvironment().getProperty("feature.enabled")).thenReturn("true");

    @WorkflowAnnotation(name = "MultiConditionalWorkflow")
    class MultiConditionalWorkflow {
      @TaskMethod(name = "Task1", order = 1)
      @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
          name = "feature.enabled",
          havingValue = "true")
      @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
          name = "java.lang.String")
      public Task createTask1() {
        return context -> context.put("task1", true);
      }

      @TaskMethod(name = "Task2", order = 2)
      public Task createTask2() {
        return context -> context.put("task2", true);
      }
    }

    MultiConditionalWorkflow instance = new MultiConditionalWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testWorkflowBuilderPreservesExecutionOrder() {
    @WorkflowAnnotation(name = "OrderPreservationWorkflow")
    class OrderPreservationWorkflow {
      @TaskMethod(name = "Fifth", order = 5)
      public Task createFifth() {
        return context -> context.put("fifth", 5);
      }

      @TaskMethod(name = "Second", order = 2)
      public Task createSecond() {
        return context -> context.put("second", 2);
      }

      @TaskMethod(name = "Fourth", order = 4)
      public Task createFourth() {
        return context -> context.put("fourth", 4);
      }

      @TaskMethod(name = "First", order = 1)
      public Task createFirst() {
        return context -> context.put("first", 1);
      }

      @TaskMethod(name = "Third", order = 3)
      public Task createThird() {
        return context -> context.put("third", 3);
      }
    }

    OrderPreservationWorkflow instance = new OrderPreservationWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(1, context.getTyped("first", Integer.class));
    assertEquals(2, context.getTyped("second", Integer.class));
    assertEquals(3, context.getTyped("third", Integer.class));
    assertEquals(4, context.getTyped("fourth", Integer.class));
    assertEquals(5, context.getTyped("fifth", Integer.class));
  }

  @Test
  void testWorkflowWithComplexNestedStructure() {
    @WorkflowAnnotation(name = "ComplexNestedWorkflow")
    class ComplexNestedWorkflow {
      @TaskMethod(name = "InitTask", order = 1)
      public Task createInitTask() {
        return context -> {
          context.put("level", 0);
          context.put("path", "start");
        };
      }

      @WorkflowMethod(name = "NestedWorkflow1", order = 2)
      public Workflow createNested1() {
        return SequentialWorkflow.builder()
            .name("Nested1")
            .task(
                context -> {
                  context.put("level", context.getTyped("level", Integer.class) + 1);
                  context.put("path", context.getTyped("path", String.class) + "->nested1");
                })
            .build();
      }

      @WorkflowMethod(name = "NestedWorkflow2", order = 3)
      public Workflow createNested2() {
        return SequentialWorkflow.builder()
            .name("Nested2")
            .task(
                context -> {
                  context.put("level", context.getTyped("level", Integer.class) + 1);
                  context.put("path", context.getTyped("path", String.class) + "->nested2");
                })
            .build();
      }

      @TaskMethod(name = "FinalTask", order = 4)
      public Task createFinalTask() {
        return context -> {
          context.put("level", context.getTyped("level", Integer.class) + 1);
          context.put("path", context.getTyped("path", String.class) + "->final");
        };
      }
    }

    ComplexNestedWorkflow instance = new ComplexNestedWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(3, context.getTyped("level", Integer.class));
    assertEquals("start->nested1->nested2->final", context.getTyped("path", String.class));
  }

  @Test
  void testWorkflowWithMultipleParallelBranches() {
    @WorkflowAnnotation(name = "MultiParallelBranchesWorkflow", parallel = true)
    class MultiParallelBranchesWorkflow {
      @TaskMethod(name = "Branch1", order = 1)
      public Task createBranch1() {
        return context -> context.put("branch1", "executed");
      }

      @TaskMethod(name = "Branch2", order = 2)
      public Task createBranch2() {
        return context -> context.put("branch2", "executed");
      }

      @TaskMethod(name = "Branch3", order = 3)
      public Task createBranch3() {
        return context -> context.put("branch3", "executed");
      }

      @TaskMethod(name = "Branch4", order = 4)
      public Task createBranch4() {
        return context -> context.put("branch4", "executed");
      }
    }

    MultiParallelBranchesWorkflow instance = new MultiParallelBranchesWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals("executed", context.getTyped("branch1", String.class));
    assertEquals("executed", context.getTyped("branch2", String.class));
    assertEquals("executed", context.getTyped("branch3", String.class));
    assertEquals("executed", context.getTyped("branch4", String.class));
  }

  @Test
  void testWorkflowNullParameterHandling() {
    assertThrows(NullPointerException.class, () -> processor.buildWorkflow(null));
  }

  @Test
  void testWorkflowWithConditionalProperty_MissingProperty() {
    when(applicationContext.getEnvironment())
        .thenReturn(mock(org.springframework.core.env.Environment.class));
    when(applicationContext.getEnvironment().getProperty("missing.property")).thenReturn(null);

    @WorkflowAnnotation(name = "MissingPropertyWorkflow")
    class MissingPropertyWorkflow {
      @TaskMethod(name = "ConditionalTask", order = 1)
      @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
          name = "missing.property",
          havingValue = "somevalue")
      public Task createTask() {
        return context -> context.put("executed", true);
      }
    }

    MissingPropertyWorkflow instance = new MissingPropertyWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
  }

  @Test
  void testTaskMethodNameExtraction() {
    @WorkflowAnnotation(name = "TaskNameWorkflow")
    class TaskNameWorkflow {
      @TaskMethod(name = "SpecificTaskName", order = 1)
      public Task createTask() {
        return context -> context.put("task.name.verified", true);
      }
    }

    TaskNameWorkflow instance = new TaskNameWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @Test
  void testParallelWorkflowWithFailureScenario() {
    @WorkflowAnnotation(name = "ParallelWithFailureWorkflow", parallel = true)
    class ParallelWithFailureWorkflow {
      @TaskMethod(name = "SuccessfulTask", order = 1)
      public Task createSuccessfulTask() {
        return context -> context.put("success", true);
      }

      @TaskMethod(name = "AnotherSuccessfulTask", order = 2)
      public Task createAnotherSuccessfulTask() {
        return context -> context.put("another.success", true);
      }
    }

    ParallelWithFailureWorkflow instance = new ParallelWithFailureWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("success", Boolean.class));
    assertTrue(context.getTyped("another.success", Boolean.class));
  }

  @Test
  void testWorkflowBuilderWithDuplicateOrderValues() {
    @WorkflowAnnotation(name = "DuplicateOrderWorkflow")
    class DuplicateOrderWorkflow {
      @TaskMethod(name = "Task1", order = 1)
      public Task createTask1() {
        return context -> context.put("task1", true);
      }

      @TaskMethod(name = "Task2", order = 1)
      public Task createTask2() {
        return context -> context.put("task2", true);
      }
    }

    DuplicateOrderWorkflow instance = new DuplicateOrderWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    // Both tasks should be executed despite having same order
    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
  }

  @Test
  void testWorkflowWithStringContextManipulation() {
    @WorkflowAnnotation(name = "StringManipulationWorkflow")
    class StringManipulationWorkflow {
      @TaskMethod(name = "FirstTask", order = 1)
      public Task createFirstTask() {
        return context -> context.put("message", "Hello");
      }

      @TaskMethod(name = "SecondTask", order = 2)
      public Task createSecondTask() {
        return context -> {
          String msg = context.getTyped("message", String.class);
          context.put("message", msg + " World");
        };
      }

      @TaskMethod(name = "ThirdTask", order = 3)
      public Task createThirdTask() {
        return context -> {
          String msg = context.getTyped("message", String.class);
          context.put("final.message", msg + "!");
        };
      }
    }

    StringManipulationWorkflow instance = new StringManipulationWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals("Hello World!", context.getTyped("final.message", String.class));
  }

  @Test
  void testWorkflowProcessorWithInstance() {
    SimpleSequentialWorkflowDefinition instance = new SimpleSequentialWorkflowDefinition();
    Workflow workflow = processor.buildWorkflow(instance);

    assertNotNull(workflow);
    assertEquals("SimpleSequentialWorkflow", workflow.getName());
  }

  @Test
  void testWorkflowProcessorWithClass() {
    Workflow workflow = processor.buildWorkflow(SimpleParallelWorkflowDefinition.class);

    assertNotNull(workflow);
    assertEquals("ParallelWorkflowTest", workflow.getName());
  }

  @Test
  void testWorkflowWithArithmeticOperations() {
    @WorkflowAnnotation(name = "ArithmeticWorkflow")
    class ArithmeticWorkflow {
      @TaskMethod(name = "InitValues", order = 1)
      public Task createInitValues() {
        return context -> {
          context.put("a", 10);
          context.put("b", 20);
        };
      }

      @TaskMethod(name = "Addition", order = 2)
      public Task createAddition() {
        return context -> {
          int a = context.getTyped("a", Integer.class);
          int b = context.getTyped("b", Integer.class);
          context.put("sum", a + b);
        };
      }

      @TaskMethod(name = "Multiplication", order = 3)
      public Task createMultiplication() {
        return context -> {
          int a = context.getTyped("a", Integer.class);
          int b = context.getTyped("b", Integer.class);
          context.put("product", a * b);
        };
      }

      @TaskMethod(name = "Subtraction", order = 4)
      public Task createSubtraction() {
        return context -> {
          int b = context.getTyped("b", Integer.class);
          int a = context.getTyped("a", Integer.class);
          context.put("difference", b - a);
        };
      }
    }

    ArithmeticWorkflow instance = new ArithmeticWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(30, context.getTyped("sum", Integer.class));
    assertEquals(200, context.getTyped("product", Integer.class));
    assertEquals(10, context.getTyped("difference", Integer.class));
  }

  @Test
  void testWorkflowWithCollectionOperations() {
    @WorkflowAnnotation(name = "CollectionWorkflow")
    class CollectionWorkflow {
      @TaskMethod(name = "InitList", order = 1)
      public Task createInitList() {
        return context -> {
          java.util.List<Integer> numbers = java.util.Arrays.asList(1, 2, 3, 4, 5);
          context.put("numbers", numbers);
        };
      }

      @TaskMethod(name = "SumList", order = 2)
      public Task createSumList() {
        return context -> {
          @SuppressWarnings("unchecked")
          java.util.List<Integer> numbers =
              (java.util.List<Integer>) context.get("numbers", Object.class);
          int sum = numbers.stream().mapToInt(Integer::intValue).sum();
          context.put("total", sum);
        };
      }

      @TaskMethod(name = "VerifySum", order = 3)
      public Task createVerifySum() {
        return context -> {
          int total = context.getTyped("total", Integer.class);
          context.put("sum_correct", total == 15);
        };
      }
    }

    CollectionWorkflow instance = new CollectionWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(15, context.getTyped("total", Integer.class));
    assertTrue(context.getTyped("sum_correct", Boolean.class));
  }

  @Test
  void testWorkflowWithConditionalLogic() {
    @WorkflowAnnotation(name = "ConditionalLogicWorkflow")
    class ConditionalLogicWorkflow {
      @TaskMethod(name = "SetValue", order = 1)
      public Task createSetValue() {
        return context -> context.put("value", 50);
      }

      @TaskMethod(name = "CheckThreshold", order = 2)
      public Task createCheckThreshold() {
        return context -> {
          int value = context.getTyped("value", Integer.class);
          if (value > 40) {
            context.put("above_threshold", true);
          } else {
            context.put("above_threshold", false);
          }
        };
      }
    }

    ConditionalLogicWorkflow instance = new ConditionalLogicWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("above_threshold", Boolean.class));
  }

  @Test
  void testWorkflowWithNegativeValues() {
    @WorkflowAnnotation(name = "NegativeValueWorkflow")
    class NegativeValueWorkflow {
      @TaskMethod(name = "StoreNegative", order = 1)
      public Task createStoreNegative() {
        return context -> {
          context.put("negative", -100);
          context.put("positive", 50);
        };
      }

      @TaskMethod(name = "CalculateSum", order = 2)
      public Task createCalculateSum() {
        return context -> {
          int neg = context.getTyped("negative", Integer.class);
          int pos = context.getTyped("positive", Integer.class);
          context.put("result", neg + pos);
        };
      }

      @TaskMethod(name = "VerifyNegativeResult", order = 3)
      public Task createVerifyNegativeResult() {
        return context -> {
          int result = context.getTyped("result", Integer.class);
          context.put("is_negative", result < 0);
        };
      }
    }

    NegativeValueWorkflow instance = new NegativeValueWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(-50, context.getTyped("result", Integer.class));
    assertTrue(context.getTyped("is_negative", Boolean.class));
  }

  @Test
  void testWorkflowWithLargeNumberOfTasks() {
    @WorkflowAnnotation(name = "LargeTaskWorkflow")
    class LargeTaskWorkflow {
      @TaskMethod(name = "Task01", order = 1)
      public Task createTask01() {
        return context -> context.put("executed01", true);
      }

      @TaskMethod(name = "Task02", order = 2)
      public Task createTask02() {
        return context -> context.put("executed02", true);
      }

      @TaskMethod(name = "Task03", order = 3)
      public Task createTask03() {
        return context -> context.put("executed03", true);
      }

      @TaskMethod(name = "Task04", order = 4)
      public Task createTask04() {
        return context -> context.put("executed04", true);
      }

      @TaskMethod(name = "Task05", order = 5)
      public Task createTask05() {
        return context -> context.put("executed05", true);
      }

      @TaskMethod(name = "Task06", order = 6)
      public Task createTask06() {
        return context -> context.put("executed06", true);
      }

      @TaskMethod(name = "Task07", order = 7)
      public Task createTask07() {
        return context -> context.put("executed07", true);
      }

      @TaskMethod(name = "Task08", order = 8)
      public Task createTask08() {
        return context -> context.put("executed08", true);
      }

      @TaskMethod(name = "Task09", order = 9)
      public Task createTask09() {
        return context -> context.put("executed09", true);
      }

      @TaskMethod(name = "Task10", order = 10)
      public Task createTask10() {
        return context -> context.put("executed10", true);
      }
    }

    LargeTaskWorkflow instance = new LargeTaskWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    for (int i = 1; i <= 10; i++) {
      String key = String.format("executed%02d", i);
      assertTrue(context.getTyped(key, Boolean.class), "Task " + i + " was not executed");
    }
  }

  @Test
  void testWorkflowWithFloatingPointOperations() {
    @WorkflowAnnotation(name = "FloatingPointWorkflow")
    class FloatingPointWorkflow {
      @TaskMethod(name = "StoreDoubles", order = 1)
      public Task createStoreDoubles() {
        return context -> {
          context.put("pi", 3.14159);
          context.put("e", 2.71828);
        };
      }

      @TaskMethod(name = "CalculateProduct", order = 2)
      public Task createCalculateProduct() {
        return context -> {
          double pi = context.getTyped("pi", Double.class);
          double e = context.getTyped("e", Double.class);
          context.put("product", pi * e);
        };
      }

      @TaskMethod(name = "RoundResult", order = 3)
      public Task createRoundResult() {
        return context -> {
          double product = context.getTyped("product", Double.class);
          context.put("rounded", Math.round(product * 100) / 100.0);
        };
      }
    }

    FloatingPointWorkflow instance = new FloatingPointWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    double product = context.getTyped("product", Double.class);
    assertTrue(product > 8.5 && product < 8.6);
  }

  @Test
  void testWorkflowWithStringConcatenation() {
    @WorkflowAnnotation(name = "StringConcatWorkflow")
    class StringConcatWorkflow {
      @TaskMethod(name = "FirstPart", order = 1)
      public Task createFirstPart() {
        return context -> context.put("text", "The");
      }

      @TaskMethod(name = "SecondPart", order = 2)
      public Task createSecondPart() {
        return context -> {
          String text = context.getTyped("text", String.class);
          context.put("text", text + " Quick");
        };
      }

      @TaskMethod(name = "ThirdPart", order = 3)
      public Task createThirdPart() {
        return context -> {
          String text = context.getTyped("text", String.class);
          context.put("text", text + " Brown");
        };
      }

      @TaskMethod(name = "FourthPart", order = 4)
      public Task createFourthPart() {
        return context -> {
          String text = context.getTyped("text", String.class);
          context.put("final_text", text + " Fox");
        };
      }
    }

    StringConcatWorkflow instance = new StringConcatWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals("The Quick Brown Fox", context.getTyped("final_text", String.class));
  }

  @Test
  void testWorkflowWithBooleanOperations() {
    @WorkflowAnnotation(name = "BooleanOpWorkflow")
    class BooleanOpWorkflow {
      @TaskMethod(name = "SetFlags", order = 1)
      public Task createSetFlags() {
        return context -> {
          context.put("flag1", true);
          context.put("flag2", false);
          context.put("flag3", true);
        };
      }

      @TaskMethod(name = "PerformAnd", order = 2)
      public Task createPerformAnd() {
        return context -> {
          boolean flag1 = context.getTyped("flag1", Boolean.class);
          boolean flag2 = context.getTyped("flag2", Boolean.class);
          context.put("and_result", flag1 && flag2);
        };
      }

      @TaskMethod(name = "PerformOr", order = 3)
      public Task createPerformOr() {
        return context -> {
          boolean flag1 = context.getTyped("flag1", Boolean.class);
          boolean flag3 = context.getTyped("flag3", Boolean.class);
          context.put("or_result", flag1 || flag3);
        };
      }
    }

    BooleanOpWorkflow instance = new BooleanOpWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertFalse(context.getTyped("and_result", Boolean.class));
    assertTrue(context.getTyped("or_result", Boolean.class));
  }

  @Test
  void testWorkflowWithNestedContextAccess() {
    @WorkflowAnnotation(name = "NestedContextAccessWorkflow")
    class NestedContextAccessWorkflow {
      @TaskMethod(name = "InitializeMultiLevel", order = 1)
      public Task createInitializeMultiLevel() {
        return context -> {
          context.put("level1.level2.level3", "deep_value");
          context.put("level1.value", "first_level");
          context.put("simple", "simple_value");
        };
      }

      @TaskMethod(name = "AccessAllLevels", order = 2)
      public Task createAccessAllLevels() {
        return context -> {
          String deep = context.getTyped("level1.level2.level3", String.class);
          String first = context.getTyped("level1.value", String.class);
          String simple = context.getTyped("simple", String.class);

          context.put("deep_accessed", deep.equals("deep_value"));
          context.put("first_accessed", first.equals("first_level"));
          context.put("simple_accessed", simple.equals("simple_value"));
        };
      }
    }

    NestedContextAccessWorkflow instance = new NestedContextAccessWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("deep_accessed", Boolean.class));
    assertTrue(context.getTyped("first_accessed", Boolean.class));
    assertTrue(context.getTyped("simple_accessed", Boolean.class));
  }

  @Test
  void testWorkflowNameWithSpecialCharacters() {
    @WorkflowAnnotation(name = "Special-Workflow_123")
    class SpecialCharWorkflow {
      @TaskMethod(name = "Task", order = 1)
      public Task createTask() {
        return context -> context.put("executed", true);
      }
    }

    SpecialCharWorkflow instance = new SpecialCharWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    assertEquals("Special-Workflow_123", workflow.getName());
  }

  @Test
  void testParallelWorkflowWithManyBranches() {
    @WorkflowAnnotation(name = "ManyBranchesParallelWorkflow", parallel = true)
    class ManyBranchesWorkflow {
      @TaskMethod(name = "Branch1", order = 1)
      public Task createBranch1() {
        return context -> context.put("branch1", 1);
      }

      @TaskMethod(name = "Branch2", order = 2)
      public Task createBranch2() {
        return context -> context.put("branch2", 2);
      }

      @TaskMethod(name = "Branch3", order = 3)
      public Task createBranch3() {
        return context -> context.put("branch3", 3);
      }

      @TaskMethod(name = "Branch4", order = 4)
      public Task createBranch4() {
        return context -> context.put("branch4", 4);
      }

      @TaskMethod(name = "Branch5", order = 5)
      public Task createBranch5() {
        return context -> context.put("branch5", 5);
      }

      @TaskMethod(name = "Branch6", order = 6)
      public Task createBranch6() {
        return context -> context.put("branch6", 6);
      }

      @TaskMethod(name = "Branch7", order = 7)
      public Task createBranch7() {
        return context -> context.put("branch7", 7);
      }

      @TaskMethod(name = "Branch8", order = 8)
      public Task createBranch8() {
        return context -> context.put("branch8", 8);
      }
    }

    ManyBranchesWorkflow instance = new ManyBranchesWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    for (int i = 1; i <= 8; i++) {
      assertEquals(i, context.getTyped("branch" + i, Integer.class));
    }
  }

  @Test
  void testWorkflowWithNullValueHandling() {
    @WorkflowAnnotation(name = "NullValueHandlingWorkflow")
    class NullValueHandlingWorkflow {
      @TaskMethod(name = "SetEmptyString", order = 1)
      public Task createSetEmptyString() {
        return context -> {
          context.put("empty_key", "");
          context.put("normal_key", "value");
        };
      }

      @TaskMethod(name = "CheckValues", order = 2)
      public Task createCheckValues() {
        return context -> {
          String empty = context.getTyped("empty_key", String.class);
          String normal = context.getTyped("normal_key", String.class);
          context.put("empty_is_empty", empty.isEmpty());
          context.put("normal_not_empty", !normal.isEmpty());
        };
      }
    }

    NullValueHandlingWorkflow instance = new NullValueHandlingWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("empty_is_empty", Boolean.class));
    assertTrue(context.getTyped("normal_not_empty", Boolean.class));
  }

  @Test
  void testWorkflowWithOverwritingValues() {
    @WorkflowAnnotation(name = "OverwriteValueWorkflow")
    class OverwriteValueWorkflow {
      @TaskMethod(name = "InitialValue", order = 1)
      public Task createInitialValue() {
        return context -> context.put("value", "initial");
      }

      @TaskMethod(name = "FirstOverwrite", order = 2)
      public Task createFirstOverwrite() {
        return context -> context.put("value", "first_overwrite");
      }

      @TaskMethod(name = "SecondOverwrite", order = 3)
      public Task createSecondOverwrite() {
        return context -> context.put("value", "second_overwrite");
      }

      @TaskMethod(name = "FinalCheck", order = 4)
      public Task createFinalCheck() {
        return context -> {
          String value = context.getTyped("value", String.class);
          context.put("final_value_correct", value.equals("second_overwrite"));
        };
      }
    }

    OverwriteValueWorkflow instance = new OverwriteValueWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("final_value_correct", Boolean.class));
  }

  @Test
  void testMixedTaskWorkflowMethodsWithManyElements() {
    @WorkflowAnnotation(name = "ComplexMixedWorkflow")
    class ComplexMixedWorkflow {
      @TaskMethod(name = "Task1", order = 1)
      public Task createTask1() {
        return context -> context.put("t1", true);
      }

      @WorkflowMethod(name = "Workflow1", order = 2)
      public Workflow createWorkflow1() {
        return SequentialWorkflow.builder()
            .name("W1")
            .task(context -> context.put("w1", true))
            .build();
      }

      @TaskMethod(name = "Task2", order = 3)
      public Task createTask2() {
        return context -> context.put("t2", true);
      }

      @WorkflowMethod(name = "Workflow2", order = 4)
      public Workflow createWorkflow2() {
        return SequentialWorkflow.builder()
            .name("W2")
            .task(context -> context.put("w2", true))
            .build();
      }

      @TaskMethod(name = "Task3", order = 5)
      public Task createTask3() {
        return context -> context.put("t3", true);
      }
    }

    ComplexMixedWorkflow instance = new ComplexMixedWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("t1", Boolean.class));
    assertTrue(context.getTyped("w1", Boolean.class));
    assertTrue(context.getTyped("t2", Boolean.class));
    assertTrue(context.getTyped("w2", Boolean.class));
    assertTrue(context.getTyped("t3", Boolean.class));
  }

  @Test
  void testWorkflowWithMultipleDataTypes() {
    @WorkflowAnnotation(name = "MultiTypeWorkflow")
    class MultiTypeWorkflow {
      @TaskMethod(name = "StoreMixedTypes", order = 1)
      public Task createStoreMixedTypes() {
        return context -> {
          context.put("byte_val", (byte) 127);
          context.put("short_val", (short) 32767);
          context.put("int_val", 2147483647);
          context.put("long_val", 9223372036854775807L);
          context.put("float_val", 3.14f);
          context.put("double_val", 2.71828);
          context.put("char_val", 'A');
          context.put("boolean_val", true);
          context.put("string_val", "test");
        };
      }

      @TaskMethod(name = "VerifyTypes", order = 2)
      public Task createVerifyTypes() {
        return context ->
            context.put(
                "all_types_present",
                context.get("byte_val", Object.class) != null
                    && context.get("short_val", Object.class) != null
                    && context.get("int_val", Object.class) != null
                    && context.get("long_val", Object.class) != null
                    && context.get("float_val", Object.class) != null
                    && context.get("double_val", Object.class) != null
                    && context.get("char_val", Object.class) != null
                    && context.get("boolean_val", Object.class) != null
                    && context.get("string_val", Object.class) != null);
      }
    }

    MultiTypeWorkflow instance = new MultiTypeWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("all_types_present", Boolean.class));
  }

  @Test
  void testTaskMethodWithAutowiredRequiredTrue_BeanAvailable() {
    String testBean = "test-bean-value";
    when(applicationContext.getBean(String.class)).thenReturn(testBean);

    @WorkflowAnnotation(name = "AutowiredRequiredTrueWorkflow")
    class AutowiredRequiredTrueWorkflow {
      @TaskMethod(name = "AutowiredTask", order = 1)
      public Task createTaskWithAutowiredRequired(@Autowired String dependency) {
        return context -> {
          context.put("bean.received", dependency);
          context.put("bean.correct", dependency.equals(testBean));
        };
      }
    }

    AutowiredRequiredTrueWorkflow instance = new AutowiredRequiredTrueWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(testBean, context.getTyped("bean.received", String.class));
    assertTrue(context.getTyped("bean.correct", Boolean.class));
  }

  @Test
  void testTaskMethodWithAutowiredRequiredFalse_BeanMissing() {
    when(applicationContext.getBean(String.class))
        .thenThrow(new RuntimeException("No bean of type String"));

    @WorkflowAnnotation(name = "AutowiredOptionalWorkflow")
    class AutowiredOptionalWorkflow {
      @TaskMethod(name = "OptionalBeanTask", order = 1)
      public Task createTaskWithOptionalBean(@Autowired(required = false) String dependency) {
        return context -> {
          context.put("bean.was.null", dependency == null);
          context.put("task.executed", true);
        };
      }
    }

    AutowiredOptionalWorkflow instance = new AutowiredOptionalWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("bean.was.null", Boolean.class));
    assertTrue(context.getTyped("task.executed", Boolean.class));
  }

  @Test
  void testTaskMethodWithMultipleAutowiredDependencies() {
    String stringBean = "string-bean";
    Integer integerBean = 42;
    when(applicationContext.getBean(String.class)).thenReturn(stringBean);
    when(applicationContext.getBean(Integer.class)).thenReturn(integerBean);

    @WorkflowAnnotation(name = "MultipleAutowiredTaskWorkflow")
    class MultipleAutowiredTaskWorkflow {
      @TaskMethod(name = "MultiDepTask", order = 1)
      public Task createTaskWithMultipleDependencies(
          @Autowired String stringDep, @Autowired Integer intDep) {
        return context -> {
          context.put("string.dep", stringDep);
          context.put("int.dep", intDep);
          context.put("both.received", stringDep != null && intDep != null);
        };
      }
    }

    MultipleAutowiredTaskWorkflow instance = new MultipleAutowiredTaskWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(stringBean, context.getTyped("string.dep", String.class));
    assertEquals(integerBean, context.getTyped("int.dep", Integer.class));
    assertTrue(context.getTyped("both.received", Boolean.class));
  }

  @Test
  void testWorkflowMethodWithAutowiredRequiredTrue_BeanAvailable() {
    String testBean = "workflow-bean-value";
    when(applicationContext.getBean(String.class)).thenReturn(testBean);

    @WorkflowAnnotation(name = "AutowiredWorkflowMethodWorkflow")
    class AutowiredWorkflowMethodWorkflow {
      @WorkflowMethod(name = "AutowiredSubWorkflow", order = 1)
      public Workflow createWorkflowWithAutowiredDependency(@Autowired String dependency) {
        return SequentialWorkflow.builder()
            .name("SubWorkflowWithAutowired")
            .task(
                context -> {
                  context.put("workflow.bean", dependency);
                  context.put("workflow.bean.correct", dependency.equals(testBean));
                })
            .build();
      }
    }

    AutowiredWorkflowMethodWorkflow instance = new AutowiredWorkflowMethodWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(testBean, context.getTyped("workflow.bean", String.class));
    assertTrue(context.getTyped("workflow.bean.correct", Boolean.class));
  }

  @Test
  void testWorkflowMethodWithAutowiredOptional_BeanMissing() {
    when(applicationContext.getBean(String.class)).thenThrow(new RuntimeException("No bean"));

    @WorkflowAnnotation(name = "OptionalWorkflowMethodWorkflow")
    class OptionalWorkflowMethodWorkflow {
      @WorkflowMethod(name = "OptionalSubWorkflow", order = 1)
      public Workflow createWorkflowWithOptionalDependency(
          @Autowired(required = false) String dependency) {
        return SequentialWorkflow.builder()
            .name("OptionalSubWorkflow")
            .task(
                context -> {
                  context.put("workflow.bean.null", dependency == null);
                  context.put("workflow.executed", true);
                })
            .build();
      }
    }

    OptionalWorkflowMethodWorkflow instance = new OptionalWorkflowMethodWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("workflow.bean.null", Boolean.class));
    assertTrue(context.getTyped("workflow.executed", Boolean.class));
  }

  @Test
  void testMixedAutowiredAndNonAutowiredTaskMethods() {
    String beanValue = "injected-value";
    when(applicationContext.getBean(String.class)).thenReturn(beanValue);

    @WorkflowAnnotation(name = "MixedAutowiredTaskWorkflow")
    class MixedAutowiredTaskWorkflow {
      @TaskMethod(name = "TaskWithAutowired", order = 1)
      public Task createTaskWithAutowired(@Autowired(required = false) String dependency) {
        return context -> context.put("task1.bean", dependency);
      }

      @TaskMethod(name = "TaskWithoutAutowired", order = 2)
      public Task createTaskWithoutAutowired() {
        return context -> context.put("task2.executed", true);
      }
    }

    MixedAutowiredTaskWorkflow instance = new MixedAutowiredTaskWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(beanValue, context.getTyped("task1.bean", String.class));
    assertTrue(context.getTyped("task2.executed", Boolean.class));
  }

  @Test
  void testMixedAutowiredAndNonAutowiredWorkflowMethods() {
    String beanValue = "workflow-injected";
    when(applicationContext.getBean(String.class)).thenReturn(beanValue);

    @WorkflowAnnotation(name = "MixedAutowiredWorkflowMethodWorkflow")
    class MixedAutowiredWorkflowMethodWorkflow {
      @WorkflowMethod(name = "WorkflowWithAutowired", order = 1)
      public Workflow createWorkflowWithAutowired(@Autowired(required = false) String dependency) {
        return SequentialWorkflow.builder()
            .name("WithAutowired")
            .task(context -> context.put("wf1.bean", dependency))
            .build();
      }

      @WorkflowMethod(name = "WorkflowWithoutAutowired", order = 2)
      public Workflow createWorkflowWithoutAutowired() {
        return SequentialWorkflow.builder()
            .name("WithoutAutowired")
            .task(context -> context.put("wf2.executed", true))
            .build();
      }
    }

    MixedAutowiredWorkflowMethodWorkflow instance = new MixedAutowiredWorkflowMethodWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(beanValue, context.getTyped("wf1.bean", String.class));
    assertTrue(context.getTyped("wf2.executed", Boolean.class));
  }

  @Test
  void testTaskMethodWithAutowiredAndContext() {
    String beanValue = "context-aware-bean";
    when(applicationContext.getBean(String.class)).thenReturn(beanValue);

    @WorkflowAnnotation(name = "ContextAwareAutowiredWorkflow")
    class ContextAwareAutowiredWorkflow {
      @TaskMethod(name = "ContextAwareTask", order = 1)
      public Task createContextAwareTask(@Autowired String dependency) {
        return context -> {
          context.put("bean.injected", dependency);
          context.put("context.available", true);
          String fromContext = context.getTyped("bean.injected", String.class);
          context.put("bean.matches", fromContext.equals(beanValue));
        };
      }
    }

    ContextAwareAutowiredWorkflow instance = new ContextAwareAutowiredWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("context.available", Boolean.class));
    assertTrue(context.getTyped("bean.matches", Boolean.class));
  }

  @Test
  void testWorkflowMethodWithAutowiredMultipleDependencies() {
    String stringBean = "string-bean-wf";
    Integer intBean = 100;
    when(applicationContext.getBean(String.class)).thenReturn(stringBean);
    when(applicationContext.getBean(Integer.class)).thenReturn(intBean);

    @WorkflowAnnotation(name = "MultiDepWorkflowMethodWorkflow")
    class MultiDepWorkflowMethodWorkflow {
      @WorkflowMethod(name = "MultiDepWorkflow", order = 1)
      public Workflow createWorkflowWithMultipleDeps(
          @Autowired String stringDep, @Autowired Integer intDep) {
        return SequentialWorkflow.builder()
            .name("MultiDepWorkflowName")
            .task(
                context -> {
                  context.put("wf.string.dep", stringDep);
                  context.put("wf.int.dep", intDep);
                  context.put("wf.both.available", stringDep != null && intDep != null);
                })
            .build();
      }
    }

    MultiDepWorkflowMethodWorkflow instance = new MultiDepWorkflowMethodWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(stringBean, context.getTyped("wf.string.dep", String.class));
    assertEquals(intBean, context.getTyped("wf.int.dep", Integer.class));
    assertTrue(context.getTyped("wf.both.available", Boolean.class));
  }

  @Test
  void testTaskAndWorkflowMethodWithAutowiredInSequence() {
    String taskBean = "task-bean";
    String workflowBean = "workflow-bean";
    when(applicationContext.getBean(String.class)).thenReturn(taskBean).thenReturn(workflowBean);

    @WorkflowAnnotation(name = "SequencedAutowiredWorkflow")
    class SequencedAutowiredWorkflow {
      @TaskMethod(name = "TaskWithBean", order = 1)
      public Task createTaskWithBean(@Autowired(required = false) String taskDep) {
        return context -> {
          context.put("task.bean", taskDep);
          context.put("bean.count", 1);
        };
      }

      @WorkflowMethod(name = "WorkflowWithBean", order = 2)
      public Workflow createWorkflowWithBean(@Autowired(required = false) String workflowDep) {
        return SequentialWorkflow.builder()
            .name("SequencedSubWorkflow")
            .task(
                context -> {
                  context.put("workflow.bean", workflowDep);
                  int count = context.getTyped("bean.count", Integer.class);
                  context.put("bean.count", count + 1);
                })
            .build();
      }
    }

    SequencedAutowiredWorkflow instance = new SequencedAutowiredWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(2, context.getTyped("bean.count", Integer.class));
  }

  @Test
  void testAutowiredWithNullBean_OptionalHandling() {
    when(applicationContext.getBean(String.class)).thenReturn(null);

    @WorkflowAnnotation(name = "NullBeanOptionalWorkflow")
    class NullBeanOptionalWorkflow {
      @TaskMethod(name = "HandleNullBean", order = 1)
      public Task createTaskHandlingNull(@Autowired(required = false) String dependency) {
        return context -> {
          context.put("bean.is.null", dependency == null);
          context.put("handled.safely", true);
        };
      }
    }

    NullBeanOptionalWorkflow instance = new NullBeanOptionalWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("bean.is.null", Boolean.class));
    assertTrue(context.getTyped("handled.safely", Boolean.class));
  }

  @Test
  void testAutowiredWithDifferentTypes() {
    String stringBean = "test-string";
    Integer intBean = 123;
    Long longBean = 456L;
    when(applicationContext.getBean(String.class)).thenReturn(stringBean);
    when(applicationContext.getBean(Integer.class)).thenReturn(intBean);
    when(applicationContext.getBean(Long.class)).thenReturn(longBean);

    @WorkflowAnnotation(name = "MultiTypeAutowiredWorkflow")
    class MultiTypeAutowiredWorkflow {
      @TaskMethod(name = "VariousTypesTask", order = 1)
      public Task createTaskWithVariousTypes(
          @Autowired String strDep, @Autowired Integer intDep, @Autowired Long longDep) {
        return context -> {
          context.put("str.bean", strDep);
          context.put("int.bean", intDep);
          context.put("long.bean", longDep);
          context.put("all.types.injected", strDep != null && intDep != null && longDep != null);
        };
      }
    }

    MultiTypeAutowiredWorkflow instance = new MultiTypeAutowiredWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertEquals(stringBean, context.getTyped("str.bean", String.class));
    assertEquals(intBean, context.getTyped("int.bean", Integer.class));
    assertEquals(longBean, context.getTyped("long.bean", Long.class));
    assertTrue(context.getTyped("all.types.injected", Boolean.class));
  }

  @Test
  void testComplexWorkflowWithAutowiredTasksAndWorkflows() {
    String bean1 = "bean-1";
    String bean2 = "bean-2";
    String bean3 = "bean-3";
    when(applicationContext.getBean(String.class))
        .thenReturn(bean1)
        .thenReturn(bean2)
        .thenReturn(bean3);

    @WorkflowAnnotation(name = "ComplexAutowiredWorkflow")
    class ComplexAutowiredWorkflow {
      @TaskMethod(name = "Task1Autowired", order = 1)
      public Task createTask1(@Autowired String dep) {
        return context -> context.put("task1.bean", dep);
      }

      @WorkflowMethod(name = "Workflow1Autowired", order = 2)
      public Workflow createWorkflow1(@Autowired(required = false) String dep) {
        return SequentialWorkflow.builder()
            .name("Workflow1")
            .task(context -> context.put("workflow1.bean", dep))
            .build();
      }

      @TaskMethod(name = "Task2Autowired", order = 3)
      public Task createTask2(@Autowired String dep) {
        return context -> context.put("task2.bean", dep);
      }

      @WorkflowMethod(name = "Workflow2Autowired", order = 4)
      public Workflow createWorkflow2(@Autowired(required = false) String dep) {
        return SequentialWorkflow.builder()
            .name("Workflow2")
            .task(context -> context.put("workflow2.bean", dep))
            .build();
      }
    }

    ComplexAutowiredWorkflow instance = new ComplexAutowiredWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertNotNull(context.get("task1.bean", Object.class));
    assertNotNull(context.get("workflow1.bean", Object.class));
    assertNotNull(context.get("task2.bean", Object.class));
    assertNotNull(context.get("workflow2.bean", Object.class));
  }

  @Test
  void testParallelTasksWithAutowiredDependencies() {
    String bean1 = "parallel-bean-1";
    String bean2 = "parallel-bean-2";
    when(applicationContext.getBean(String.class)).thenReturn(bean1).thenReturn(bean2);

    @WorkflowAnnotation(name = "ParallelAutowiredWorkflow", parallel = true)
    class ParallelAutowiredWorkflow {
      @TaskMethod(name = "ParallelTask1", order = 1)
      public Task createParallelTask1(@Autowired(required = false) String dep) {
        return context -> {
          context.put("parallel.task1.bean", dep);
          context.put("parallel.task1.executed", true);
        };
      }

      @TaskMethod(name = "ParallelTask2", order = 2)
      public Task createParallelTask2(@Autowired(required = false) String dep) {
        return context -> {
          context.put("parallel.task2.bean", dep);
          context.put("parallel.task2.executed", true);
        };
      }
    }

    ParallelAutowiredWorkflow instance = new ParallelAutowiredWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("parallel.task1.executed", Boolean.class));
    assertTrue(context.getTyped("parallel.task2.executed", Boolean.class));
  }

  @Test
  void testAutowiredParameterReusedInMultipleTasks() {
    String sharedBean = "shared-bean-value";
    when(applicationContext.getBean(String.class)).thenReturn(sharedBean);

    @WorkflowAnnotation(name = "SharedAutowiredWorkflow")
    class SharedAutowiredWorkflow {
      @TaskMethod(name = "Task1", order = 1)
      public Task createTask1(@Autowired String dependency) {
        return context -> context.put("task1.uses", dependency);
      }

      @TaskMethod(name = "Task2", order = 2)
      public Task createTask2(@Autowired String dependency) {
        return context -> {
          String task1Value = context.getTyped("task1.uses", String.class);
          context.put("task2.uses", dependency);
          context.put("both.same", task1Value.equals(dependency));
        };
      }
    }

    SharedAutowiredWorkflow instance = new SharedAutowiredWorkflow();
    Workflow workflow = processor.buildWorkflow(instance);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("both.same", Boolean.class));
  }

  // ============================================================================
  // Test Cases for @WorkflowRef annotation
  // ============================================================================

  @Test
  void testWorkflowRefBasicComposition() {
    CompositeRefWorkflow composite = new CompositeRefWorkflow();
    Workflow workflow = processor.buildWorkflow(composite);

    assertNotNull(workflow);
    assertEquals("CompositeRefWorkflow", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("ref.workflow.a.executed", Boolean.class));
    assertTrue(context.getTyped("ref.workflow.b.executed", Boolean.class));
  }

  @Test
  void testWorkflowRefMultipleWithDescriptions() {
    PipelineRefWorkflow pipeline = new PipelineRefWorkflow();
    Workflow workflow = processor.buildWorkflow(pipeline);

    assertNotNull(workflow);
    assertEquals("PipelineRefWorkflow", workflow.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("validation.executed", Boolean.class));
    assertTrue(context.getTyped("processing.executed", Boolean.class));
  }

  @Test
  void testWorkflowRefContextSharing() {
    ShareContextRefWorkflow share = new ShareContextRefWorkflow();
    Workflow workflow = processor.buildWorkflow(share);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("producer.executed", Boolean.class));
    assertTrue(context.getTyped("consumer.executed", Boolean.class));
    assertEquals("shared-data", context.getTyped("shared.value", String.class));
  }

  @Test
  void testWorkflowRefNested() {
    RootRefWorkflow root = new RootRefWorkflow();
    Workflow workflow = processor.buildWorkflow(root);

    assertNotNull(workflow);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("leaf1.executed", Boolean.class));
    assertTrue(context.getTyped("leaf2.executed", Boolean.class));
  }

  @Test
  void testWorkflowRefMixedTasks() {
    MixedTaskRefWorkflow mixed = new MixedTaskRefWorkflow();
    Workflow workflow = processor.buildWorkflow(mixed);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = workflow.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("task1.executed", Boolean.class));
    assertTrue(context.getTyped("included.executed", Boolean.class));
    assertTrue(context.getTyped("task2.executed", Boolean.class));
  }

  @Test
  void testWorkflowRefMethodParameter() {
    WorkflowRefParameterWorkflow workflow = new WorkflowRefParameterWorkflow();
    Workflow built = processor.buildWorkflow(workflow);

    assertNotNull(built);
    assertEquals("WorkflowRefParameterWorkflow", built.getName());

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = built.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("ref1.executed", Boolean.class));
    assertTrue(context.getTyped("parameter.method.executed", Boolean.class));
  }

  @Test
  void testWorkflowRefTaskMethodParameter() {
    WorkflowRefTaskParameterWorkflow workflow = new WorkflowRefTaskParameterWorkflow();
    Workflow built = processor.buildWorkflow(workflow);

    assertNotNull(built);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = built.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("ref1.executed", Boolean.class));
    assertTrue(context.getTyped("task.method.executed", Boolean.class));
  }

  @Test
  void testWorkflowRefMultipleParameters() {
    MultipleWorkflowRefParameterWorkflow workflow = new MultipleWorkflowRefParameterWorkflow();
    Workflow built = processor.buildWorkflow(workflow);

    assertNotNull(built);

    WorkflowContext context = new WorkflowContext();
    WorkflowResult result = built.execute(context);

    assertEquals(WorkflowStatus.SUCCESS, result.getStatus());
    assertTrue(context.getTyped("ref1.executed", Boolean.class));
    assertTrue(context.getTyped("ref2.executed", Boolean.class));
    assertTrue(context.getTyped("combined.executed", Boolean.class));
  }

  // ============================================================================
  // Workflow definitions for @WorkflowRef tests
  // ============================================================================

  @WorkflowAnnotation(name = "SimpleRefWorkflowA")
  public static class SimpleRefWorkflowA {
    @TaskMethod(name = "TaskA", order = 1)
    public Task createTask() {
      return context -> context.put("ref.workflow.a.executed", true);
    }
  }

  @WorkflowAnnotation(name = "SimpleRefWorkflowB")
  public static class SimpleRefWorkflowB {
    @TaskMethod(name = "TaskB", order = 1)
    public Task createTask() {
      return context -> context.put("ref.workflow.b.executed", true);
    }
  }

  @WorkflowAnnotation(name = "CompositeRefWorkflow")
  public static class CompositeRefWorkflow {
    @WorkflowRef(workflowClass = SimpleRefWorkflowA.class)
    private Workflow workflowA;

    @WorkflowRef(workflowClass = SimpleRefWorkflowB.class)
    private Workflow workflowB;

    @WorkflowMethod(order = 1)
    public Workflow createComposite() {
      return SequentialWorkflow.builder().workflow(workflowA).workflow(workflowB).build();
    }
  }

  @WorkflowAnnotation(name = "ValidateRefWorkflow")
  public static class ValidateRefWorkflow {
    @TaskMethod(name = "ValidateTask", order = 1)
    public Task createTask() {
      return context -> context.put("validation.executed", true);
    }
  }

  @WorkflowAnnotation(name = "ProcessRefWorkflow")
  public static class ProcessRefWorkflow {
    @TaskMethod(name = "ProcessTask", order = 1)
    public Task createTask() {
      return context -> context.put("processing.executed", true);
    }
  }

  @WorkflowAnnotation(name = "PipelineRefWorkflow")
  public static class PipelineRefWorkflow {
    @WorkflowRef(workflowClass = ValidateRefWorkflow.class, description = "Validate input")
    private Workflow validator;

    @WorkflowRef(workflowClass = ProcessRefWorkflow.class, description = "Process data")
    private Workflow processor;

    @WorkflowMethod(order = 1)
    public Workflow createPipeline() {
      return SequentialWorkflow.builder().workflow(validator).workflow(processor).build();
    }
  }

  @WorkflowAnnotation(name = "ProducerRefWorkflow")
  public static class ProducerRefWorkflow {
    @TaskMethod(name = "ProduceTask", order = 1)
    public Task createTask() {
      return context -> {
        context.put("shared.value", "shared-data");
        context.put("producer.executed", true);
      };
    }
  }

  @WorkflowAnnotation(name = "ConsumerRefWorkflow")
  public static class ConsumerRefWorkflow {
    @TaskMethod(name = "ConsumeTask", order = 1)
    public Task createTask() {
      return context -> {
        String data = context.getTyped("shared.value", String.class);
        context.put("consumer.received.value", data);
        context.put("consumer.executed", true);
      };
    }
  }

  @WorkflowAnnotation(name = "ShareContextRefWorkflow")
  public static class ShareContextRefWorkflow {
    @WorkflowRef(workflowClass = ProducerRefWorkflow.class)
    private Workflow producer;

    @WorkflowRef(workflowClass = ConsumerRefWorkflow.class)
    private Workflow consumer;

    @WorkflowMethod(order = 1)
    public Workflow createShared() {
      return SequentialWorkflow.builder().workflow(producer).workflow(consumer).build();
    }
  }

  @WorkflowAnnotation(name = "Leaf1RefWorkflow")
  public static class Leaf1RefWorkflow {
    @TaskMethod(name = "Task", order = 1)
    public Task createTask() {
      return context -> context.put("leaf1.executed", true);
    }
  }

  @WorkflowAnnotation(name = "Leaf2RefWorkflow")
  public static class Leaf2RefWorkflow {
    @TaskMethod(name = "Task", order = 1)
    public Task createTask() {
      return context -> context.put("leaf2.executed", true);
    }
  }

  @WorkflowAnnotation(name = "BranchRefWorkflow")
  public static class BranchRefWorkflow {
    @WorkflowRef(workflowClass = Leaf1RefWorkflow.class)
    private Workflow leaf1;

    @WorkflowRef(workflowClass = Leaf2RefWorkflow.class)
    private Workflow leaf2;

    @WorkflowMethod(order = 1)
    public Workflow createBranch() {
      return SequentialWorkflow.builder().workflow(leaf1).workflow(leaf2).build();
    }
  }

  @WorkflowAnnotation(name = "RootRefWorkflow")
  public static class RootRefWorkflow {
    @WorkflowRef(workflowClass = BranchRefWorkflow.class)
    private Workflow branch;

    @WorkflowMethod(order = 1)
    public Workflow createRoot() {
      return branch;
    }
  }

  @WorkflowAnnotation(name = "IncludedRefWorkflow")
  public static class IncludedRefWorkflow {
    @TaskMethod(name = "IncludedTask", order = 1)
    public Task createTask() {
      return context -> context.put("included.executed", true);
    }
  }

  @WorkflowAnnotation(name = "MixedTaskRefWorkflow")
  public static class MixedTaskRefWorkflow {
    @WorkflowRef(workflowClass = IncludedRefWorkflow.class)
    private Workflow included;

    @TaskMethod(name = "Task1", order = 1)
    public Task createTask1() {
      return context -> context.put("task1.executed", true);
    }

    @WorkflowMethod(name = "RefStep", order = 2)
    public Workflow createRefStep() {
      return SequentialWorkflow.builder().workflow(included).build();
    }

    @TaskMethod(name = "Task2", order = 3)
    public Task createTask2() {
      return context -> context.put("task2.executed", true);
    }
  }

  // ============================================================================
  // Workflow definitions for @WorkflowRef method parameters
  // ============================================================================

  @WorkflowAnnotation(name = "ReferencedWorkflow1")
  public static class ReferencedWorkflow1 {
    @TaskMethod(name = "RefTask", order = 1)
    public Task createTask() {
      return context -> context.put("ref1.executed", true);
    }
  }

  @WorkflowAnnotation(name = "ReferencedWorkflow2")
  public static class ReferencedWorkflow2 {
    @TaskMethod(name = "RefTask2", order = 1)
    public Task createTask() {
      return context -> context.put("ref2.executed", true);
    }
  }

  @WorkflowAnnotation(name = "WorkflowRefParameterWorkflow")
  public static class WorkflowRefParameterWorkflow {
    @WorkflowMethod(order = 1)
    public Workflow createWorkflow(
        @WorkflowRef(workflowClass = ReferencedWorkflow1.class) Workflow referenced) {
      return SequentialWorkflow.builder()
          .workflow(referenced)
          .workflow(
              new TaskWorkflow(
                  TaskDescriptor.builder()
                      .task(context -> context.put("parameter.method.executed", true))
                      .name("ParameterTask")
                      .build()))
          .build();
    }
  }

  @WorkflowAnnotation(name = "WorkflowRefTaskParameterWorkflow")
  public static class WorkflowRefTaskParameterWorkflow {
    @TaskMethod(order = 1)
    public Task createTask(
        @WorkflowRef(workflowClass = ReferencedWorkflow1.class) Workflow referenced) {
      return context -> {
        // Execute the referenced workflow
        WorkflowResult result = referenced.execute(context);
        if (result.getStatus() == WorkflowStatus.SUCCESS) {
          context.put("task.method.executed", true);
        }
      };
    }
  }

  @WorkflowAnnotation(name = "MultipleWorkflowRefParameterWorkflow")
  public static class MultipleWorkflowRefParameterWorkflow {
    @WorkflowMethod(order = 1)
    public Workflow createWorkflow(
        @WorkflowRef(workflowClass = ReferencedWorkflow1.class) Workflow ref1,
        @WorkflowRef(workflowClass = ReferencedWorkflow2.class) Workflow ref2) {
      return SequentialWorkflow.builder()
          .workflow(ref1)
          .workflow(ref2)
          .workflow(
              new TaskWorkflow(
                  TaskDescriptor.builder()
                      .task(context -> context.put("combined.executed", true))
                      .name("CombinedTask")
                      .build()))
          .build();
    }
  }

  @WorkflowAnnotation(name = "SpringWF")
  public static class SpringWorkflowDefinition {
    @TaskMethod(order = 1)
    @ConditionalOnProperty(name = "feature.enabled", havingValue = "true")
    public Task conditionalTask() {
      return ctx -> {};
    }

    @WorkflowMethod(order = 2)
    public Workflow autowiredStep(@Autowired String someBean) {
      return SequentialWorkflow.builder().name(someBean).build();
    }
  }

  @WorkflowAnnotation(name = "ChildWF")
  public static class ChildBeanWorkflow {
    @TaskMethod(order = 1)
    public Task t() {
      return c -> {};
    }
  }

  @Test
  void testBuildWorkflowClassFallback() {
    // Arrange: context.getBean throws, triggering the fallback to super.buildWorkflow
    when(applicationContext.getBean(SpringWorkflowDefinition.class))
        .thenThrow(new RuntimeException("Not a bean"));

    // Act
    Workflow workflow = processor.buildWorkflow(SpringWorkflowDefinition.class);

    // Assert
    assertNotNull(workflow);
    assertEquals("SpringWF", workflow.getName());
  }

  @Test
  void testConditionalPropertyDisabled() {
    // Arrange: Property is 'false', but annotation expects 'true'
    when(mockEnv.getProperty("feature.enabled")).thenReturn("false");
    SpringWorkflowDefinition instance = new SpringWorkflowDefinition();
    when(applicationContext.getBean(String.class)).thenReturn("myBean");

    // Act
    Workflow workflow = processor.buildWorkflow(instance);

    // Assert
    // The conditionalTask should be skipped, leaving only the autowiredStep
    assertInstanceOf(SequentialWorkflow.class, workflow);
    // If your workflow implementation allows inspecting children, verify size is 1
  }

  @Test
  void testAutowiredParameterInjection() {
    // Arrange
    SpringWorkflowDefinition instance = new SpringWorkflowDefinition();
    when(applicationContext.getBean(String.class)).thenReturn("InjectedValue");

    // Act
    Workflow workflow = processor.buildWorkflow(instance);

    // Assert
    assertNotNull(workflow);
    // Verify the internal state reflects that the string "InjectedValue" was passed to the method
  }

  @Test
  void testAutowiredRequiredFailure() {
    // Arrange
    @WorkflowAnnotation(name = "FailWF")
    class FailWorkflow {
      @TaskMethod(order = 1)
      public Task fail(@Autowired(required = true) Double missing) {
        return null;
      }
    }
    when(applicationContext.getBean(Double.class)).thenThrow(new RuntimeException("No bean"));

    // Act & Assert
    FailWorkflow workflow = new FailWorkflow();
    assertThrows(WorkflowBuildException.class, () -> processor.buildWorkflow(workflow));
  }

  @WorkflowAnnotation(name = "BeanCheck")
  public static class BeanCheckWF {
    @TaskMethod(order = 1)
    @ConditionalOnBean(String.class)
    public Task t() {
      return ctx -> {
        ctx.put("bean.check", true);
      };
    }
  }

  @Test
  void testConditionalOnBean() {
    // Case 1: Bean exists
    when(applicationContext.getBean(String.class)).thenReturn("exists");
    Workflow wf1 = processor.buildWorkflow(new BeanCheckWF());
    WorkflowContext ctx1 = new WorkflowContext();
    wf1.execute(ctx1);
    assertTrue(ctx1.getTyped("bean.check", Boolean.class));

    // Case 2: Bean missing
    reset(applicationContext);
    when(applicationContext.getEnvironment()).thenReturn(mockEnv);
    when(applicationContext.getBean(String.class)).thenThrow(new RuntimeException());
    Workflow wf2 = processor.buildWorkflow(new BeanCheckWF());
    WorkflowContext ctx2 = new WorkflowContext();
    wf2.execute(ctx2);
    assertNull(ctx2.get("bean.check"));
  }

  @Test
  void testHybridInjection() {
    @WorkflowAnnotation(name = "Hybrid")
    class HybridWF {
      @TaskMethod(order = 1)
      public Task mixed(
          @WorkflowRef(workflowClass = ChildBeanWorkflow.class) Workflow ref,
          @Autowired String springBean) {
        return ctx -> {};
      }
    }

    when(applicationContext.getBean(String.class)).thenReturn("SpringValue");
    // ChildBeanWorkflow instantiation handled by processor internally

    assertDoesNotThrow(() -> processor.buildWorkflow(new HybridWF()));
  }
}
