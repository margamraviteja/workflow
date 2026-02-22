package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.exception.TaskValidationException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractTaskTest {

  private WorkflowContext context;

  @BeforeEach
  void setUp() {
    context = new WorkflowContext();
  }

  @Test
  void execute_withNullContext_throwsNullPointerException() {
    AbstractTask task = createSimpleTask();

    assertThrows(NullPointerException.class, () -> task.execute(null));
  }

  @Test
  void execute_withValidContext_executesSuccessfully() {
    AbstractTask task = createSimpleTask();

    assertDoesNotThrow(() -> task.execute(context));
  }

  @Test
  void execute_wrapsGenericException_inTaskExecutionException() {
    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) {
            throw new RuntimeException("Test exception");
          }
        };

    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));

    assertTrue(exception.getMessage().contains("Task failed"));
    assertTrue(exception.getMessage().contains("Test exception"));
    assertNotNull(exception.getCause());
    assertInstanceOf(RuntimeException.class, exception.getCause());
  }

  @Test
  void execute_propagatesTaskExecutionException_asIs() {
    TaskExecutionException originalException = new TaskExecutionException("Original error");
    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) throws TaskExecutionException {
            throw originalException;
          }
        };

    TaskExecutionException thrownException =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));

    assertSame(originalException, thrownException);
  }

  @Test
  void require_withExistingKey_returnsValue() {
    context.put("testKey", "testValue");

    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) {
            String value = require(context, "testKey", String.class);
            assertEquals("testValue", value);
          }
        };

    assertDoesNotThrow(() -> task.execute(context));
  }

  @Test
  void require_withMissingKey_throwsIllegalStateException() {
    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) {
            require(context, "missingKey", String.class);
          }
        };

    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));

    assertInstanceOf(TaskValidationException.class, exception);
    assertTrue(exception.getMessage().contains("Required key missing: missingKey"));
  }

  @Test
  void getOrDefault_withExistingKey_returnsValue() {
    context.put("existingKey", "actualValue");

    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) {
            String value = getOrDefault(context, "existingKey", String.class, "default");
            assertEquals("actualValue", value);
          }
        };

    assertDoesNotThrow(() -> task.execute(context));
  }

  @Test
  void getOrDefault_withMissingKey_returnsDefaultValue() {
    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) {
            String value = getOrDefault(context, "missingKey", String.class, "defaultValue");
            assertEquals("defaultValue", value);
          }
        };

    assertDoesNotThrow(() -> task.execute(context));
  }

  @Test
  void getOrDefault_withDifferentTypes_worksCorrectly() {
    context.put("intKey", 42);

    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) {
            Integer value = getOrDefault(context, "intKey", Integer.class, 0);
            assertEquals(42, value);
          }
        };

    assertDoesNotThrow(() -> task.execute(context));
  }

  @Test
  void getName_differentInstances_haveDifferentNames() {
    AbstractTask task1 = createSimpleTask();
    AbstractTask task2 = createSimpleTask();

    String name1 = task1.getName();
    String name2 = task2.getName();

    assertNotEquals(name1, name2);
  }

  @Test
  void execute_withCheckedException_wrapsInTaskExecutionException() {
    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) throws TaskExecutionException {
            try {
              throw new java.io.IOException("I/O error");
            } catch (java.io.IOException e) {
              throw new TaskExecutionException("Task failed: " + e.getMessage(), e);
            }
          }
        };

    TaskExecutionException exception =
        assertThrows(TaskExecutionException.class, () -> task.execute(context));

    assertTrue(exception.getMessage().contains("Task failed"));
    assertInstanceOf(IOException.class, exception.getCause());
  }

  @Test
  void execute_withMultipleContextValues_accessesCorrectly() {
    context.put("key1", "value1");
    context.put("key2", 123);
    context.put("key3", true);

    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) {
            String val1 = require(context, "key1", String.class);
            Integer val2 = require(context, "key2", Integer.class);
            Boolean val3 = require(context, "key3", Boolean.class);

            assertEquals("value1", val1);
            assertEquals(123, val2);
            assertTrue(val3);
          }
        };

    assertDoesNotThrow(() -> task.execute(context));
  }

  @Test
  void require_withWrongType_returnsNull() {
    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) {
            // Trying to get String as Integer should return null
            Integer value = context.getTyped("stringKey", Integer.class);
            assertNull(value);
          }
        };

    assertDoesNotThrow(() -> task.execute(context));
  }

  @Test
  void execute_complexScenario_worksCorrectly() {
    context.put("required", "mustHave");
    context.put("optional", "hasValue");

    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void doExecute(WorkflowContext context) {
            String req = require(context, "required", String.class);
            String opt = getOrDefault(context, "optional", String.class, "default");
            String missing = getOrDefault(context, "notPresent", String.class, "defaultValue");

            assertEquals("mustHave", req);
            assertEquals("hasValue", opt);
            assertEquals("defaultValue", missing);

            // Store result
            context.put("result", req + ":" + opt + ":" + missing);
          }
        };

    assertDoesNotThrow(() -> task.execute(context));
    assertEquals("mustHave:hasValue:defaultValue", context.get("result"));
  }

  private AbstractTask createSimpleTask() {
    return new AbstractTask() {
      @Override
      protected void doExecute(WorkflowContext context) {
        // Simple no-op task
      }
    };
  }
}
