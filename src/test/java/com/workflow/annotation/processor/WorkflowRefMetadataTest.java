package com.workflow.annotation.processor;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.Workflow;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.WorkflowRef;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;

class WorkflowRefMetadataTest {

  @Test
  void shouldCreateFromField() throws NoSuchFieldException {
    Field field = TestClass.class.getDeclaredField("workflowField");
    WorkflowRef annotation = field.getAnnotation(WorkflowRef.class);

    WorkflowRefMetadata metadata = WorkflowRefMetadata.from(field, annotation);

    assertEquals("workflowField", metadata.name());
    assertEquals(field, metadata.field());
    assertNull(metadata.parameter());
    assertEquals(annotation, metadata.annotation());
    assertEquals(TestWorkflow.class, metadata.workflowClass());
  }

  @Test
  void shouldCreateFromParameter() throws NoSuchMethodException {
    Method method = TestClass.class.getDeclaredMethod("methodWithParameter", Workflow.class);
    Parameter parameter = method.getParameters()[0];
    WorkflowRef annotation = parameter.getAnnotation(WorkflowRef.class);

    WorkflowRefMetadata metadata = WorkflowRefMetadata.from(parameter, annotation);

    assertEquals("arg0", metadata.name());
    assertNull(metadata.field());
    assertEquals(parameter, metadata.parameter());
    assertEquals(annotation, metadata.annotation());
    assertEquals(TestWorkflow.class, metadata.workflowClass());
  }

  @Test
  void shouldCreateWithConstructor() throws NoSuchFieldException {
    Field field = TestClass.class.getDeclaredField("workflowField");
    WorkflowRef annotation = field.getAnnotation(WorkflowRef.class);

    WorkflowRefMetadata metadata =
        new WorkflowRefMetadata("customName", field, null, annotation, TestWorkflow.class);

    assertEquals("customName", metadata.name());
    assertEquals(field, metadata.field());
    assertNull(metadata.parameter());
    assertEquals(annotation, metadata.annotation());
    assertEquals(TestWorkflow.class, metadata.workflowClass());
  }

  @Test
  void shouldCreateWithoutFieldOrParameter() {
    WorkflowRefMetadata metadata =
        new WorkflowRefMetadata("name", null, null, null, TestWorkflow.class);

    assertEquals("name", metadata.name());
    assertNull(metadata.field());
    assertNull(metadata.parameter());
    assertNull(metadata.annotation());
    assertEquals(TestWorkflow.class, metadata.workflowClass());
  }

  @Test
  void shouldSupportRecordEquality() throws NoSuchFieldException {
    Field field = TestClass.class.getDeclaredField("workflowField");
    WorkflowRef annotation = field.getAnnotation(WorkflowRef.class);

    WorkflowRefMetadata metadata1 = WorkflowRefMetadata.from(field, annotation);
    WorkflowRefMetadata metadata2 = WorkflowRefMetadata.from(field, annotation);

    assertEquals(metadata1, metadata2);
    assertEquals(metadata1.hashCode(), metadata2.hashCode());
  }

  @Test
  void shouldNotEqualWithDifferentFields() throws NoSuchFieldException {
    Field field1 = TestClass.class.getDeclaredField("workflowField");
    Field field2 = TestClass.class.getDeclaredField("anotherWorkflowField");
    WorkflowRef annotation1 = field1.getAnnotation(WorkflowRef.class);
    WorkflowRef annotation2 = field2.getAnnotation(WorkflowRef.class);

    WorkflowRefMetadata metadata1 = WorkflowRefMetadata.from(field1, annotation1);
    WorkflowRefMetadata metadata2 = WorkflowRefMetadata.from(field2, annotation2);

    assertNotEquals(metadata1, metadata2);
  }

  @Test
  void shouldHaveWorkingToString() throws NoSuchFieldException {
    Field field = TestClass.class.getDeclaredField("workflowField");
    WorkflowRef annotation = field.getAnnotation(WorkflowRef.class);

    WorkflowRefMetadata metadata = WorkflowRefMetadata.from(field, annotation);

    String toString = metadata.toString();
    assertTrue(toString.contains("workflowField"));
    assertTrue(toString.contains("WorkflowRefMetadata"));
  }

  @Test
  void shouldExtractWorkflowClassFromAnnotation() throws NoSuchFieldException {
    Field field = TestClass.class.getDeclaredField("workflowField");
    WorkflowRef annotation = field.getAnnotation(WorkflowRef.class);

    WorkflowRefMetadata metadata = WorkflowRefMetadata.from(field, annotation);

    assertEquals(TestWorkflow.class, metadata.workflowClass());
    assertEquals(annotation.workflowClass(), metadata.workflowClass());
  }

  @Test
  void shouldHandleMultipleFieldsInSameClass() throws NoSuchFieldException {
    Field field1 = TestClass.class.getDeclaredField("workflowField");
    Field field2 = TestClass.class.getDeclaredField("anotherWorkflowField");
    WorkflowRef annotation1 = field1.getAnnotation(WorkflowRef.class);
    WorkflowRef annotation2 = field2.getAnnotation(WorkflowRef.class);

    WorkflowRefMetadata metadata1 = WorkflowRefMetadata.from(field1, annotation1);
    WorkflowRefMetadata metadata2 = WorkflowRefMetadata.from(field2, annotation2);

    assertEquals("workflowField", metadata1.name());
    assertEquals("anotherWorkflowField", metadata2.name());
    assertNotEquals(metadata1.field(), metadata2.field());
  }

  @Test
  void shouldHandleParameterName() throws NoSuchMethodException {
    Method method = TestClass.class.getDeclaredMethod("methodWithParameter", Workflow.class);
    Parameter parameter = method.getParameters()[0];
    WorkflowRef annotation = parameter.getAnnotation(WorkflowRef.class);

    WorkflowRefMetadata metadata = WorkflowRefMetadata.from(parameter, annotation);

    assertNotNull(metadata.name());
    assertNotNull(metadata.parameter());
  }

  @Test
  void shouldDifferentiateBetweenFieldAndParameterMetadata() throws Exception {
    Field field = TestClass.class.getDeclaredField("workflowField");
    WorkflowRef fieldAnnotation = field.getAnnotation(WorkflowRef.class);

    Method method = TestClass.class.getDeclaredMethod("methodWithParameter", Workflow.class);
    Parameter parameter = method.getParameters()[0];
    WorkflowRef paramAnnotation = parameter.getAnnotation(WorkflowRef.class);

    WorkflowRefMetadata fieldMetadata = WorkflowRefMetadata.from(field, fieldAnnotation);
    WorkflowRefMetadata paramMetadata = WorkflowRefMetadata.from(parameter, paramAnnotation);

    assertNotNull(fieldMetadata.field());
    assertNull(fieldMetadata.parameter());

    assertNull(paramMetadata.field());
    assertNotNull(paramMetadata.parameter());
  }

  @Test
  void shouldCreateWithCustomName() {
    String customName = "customWorkflowName";
    WorkflowRefMetadata metadata =
        new WorkflowRefMetadata(customName, null, null, null, TestWorkflow.class);

    assertEquals(customName, metadata.name());
  }

  @Test
  void shouldHandleNullName() {
    WorkflowRefMetadata metadata =
        new WorkflowRefMetadata(null, null, null, null, TestWorkflow.class);

    assertNull(metadata.name());
  }

  // Test classes
  @WorkflowAnnotation(name = "testWorkflow")
  static class TestWorkflow {}

  @WorkflowAnnotation(name = "anotherWorkflow")
  static class AnotherWorkflow {}

  static class TestClass {
    @WorkflowRef(workflowClass = TestWorkflow.class)
    private Workflow workflowField;

    @WorkflowRef(workflowClass = AnotherWorkflow.class)
    private Workflow anotherWorkflowField;

    void methodWithParameter(@WorkflowRef(workflowClass = TestWorkflow.class) Workflow workflow) {
      // no-op
    }
  }
}
