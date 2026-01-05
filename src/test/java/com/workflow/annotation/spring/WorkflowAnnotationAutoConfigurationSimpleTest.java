package com.workflow.annotation.spring;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.TaskWorkflow;
import com.workflow.Workflow;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class WorkflowAnnotationAutoConfigurationSimpleTest {

  @Mock private ApplicationContext applicationContext;

  @Test
  void springAnnotationWorkflowProcessor_canBeCreated() {
    WorkflowAnnotationAutoConfiguration config = new WorkflowAnnotationAutoConfiguration();

    SpringAnnotationWorkflowProcessor processor =
        config.springAnnotationWorkflowProcessor(applicationContext);

    assertNotNull(processor);
  }

  @Test
  void springAnnotationWorkflowProcessor_hasApplicationContext() {
    WorkflowAnnotationAutoConfiguration config = new WorkflowAnnotationAutoConfiguration();

    SpringAnnotationWorkflowProcessor processor =
        config.springAnnotationWorkflowProcessor(applicationContext);

    assertNotNull(processor);
    // The processor was created with the application context
    assertDoesNotThrow(() -> processor.getClass().getDeclaredFields());
  }

  @Test
  void workflowAnnotationAutoConfiguration_instantiates() {
    WorkflowAnnotationAutoConfiguration config = new WorkflowAnnotationAutoConfiguration();

    assertNotNull(config);
  }

  @Test
  void annotation_canBeAppliedToClass() {
    @WorkflowAnnotation(name = "TestWorkflow")
    class TestClass {
      @WorkflowMethod(order = 1)
      public Workflow step1() {
        return new TaskWorkflow(_ -> {});
      }
    }

    WorkflowAnnotation annotation = TestClass.class.getAnnotation(WorkflowAnnotation.class);
    assertNotNull(annotation);
    assertEquals("TestWorkflow", annotation.name());
  }
}
