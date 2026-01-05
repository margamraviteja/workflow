package com.workflow.helper;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.Workflow;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorkflowSupportTest {

  private static class TestWorkflow implements Workflow {
    private final String name;

    TestWorkflow(String name) {
      this.name = name;
    }

    @Override
    public WorkflowResult execute(WorkflowContext context) {
      return null;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  @Test
  void resolveName_withProvidedName_returnsProvidedName() {
    Workflow workflow = new TestWorkflow("CustomWorkflow");
    String providedName = "CustomWorkflow";

    String result = WorkflowSupport.resolveName(providedName, workflow);

    assertEquals(providedName, result);
  }

  @Test
  void resolveName_withNullName_returnsDefaultName() {
    Workflow workflow = new TestWorkflow(null);

    String result = WorkflowSupport.resolveName(null, workflow);

    assertNotNull(result);
    assertTrue(result.contains("TestWorkflow"));
  }

  @Test
  void resolveName_withEmptyName_returnsEmptyName() {
    Workflow workflow = new TestWorkflow("");
    String providedName = "";

    String result = WorkflowSupport.resolveName(providedName, workflow);

    assertEquals("", result);
  }

  @Test
  void isEmptyWorkflowList_withEmptyList_returnsTrue() {
    List<Workflow> emptyList = Collections.emptyList();
    assertTrue(WorkflowSupport.isEmptyWorkflowList(emptyList));
  }

  @Test
  void isEmptyWorkflowList_withNonEmptyList_returnsFalse() {
    List<Workflow> workflows = Arrays.asList(new TestWorkflow("W1"), new TestWorkflow("W2"));
    assertFalse(WorkflowSupport.isEmptyWorkflowList(workflows));
  }

  @Test
  void isEmptyWorkflowList_withSingleElementList_returnsFalse() {
    List<Workflow> workflows = Collections.singletonList(new TestWorkflow("W1"));
    assertFalse(WorkflowSupport.isEmptyWorkflowList(workflows));
  }

  @Test
  void formatWorkflowType_withTypeName_returnsFormattedString() {
    String result = WorkflowSupport.formatWorkflowType("Sequence");
    assertEquals("[Sequence]", result);
  }

  @Test
  void formatWorkflowType_withEmptyString_returnsEmptyBrackets() {
    String result = WorkflowSupport.formatWorkflowType("");
    assertEquals("[]", result);
  }

  @Test
  void formatWorkflowType_withSpecialCharacters_preservesCharacters() {
    String result = WorkflowSupport.formatWorkflowType("Parallel-Async");
    assertEquals("[Parallel-Async]", result);
  }

  @Test
  void formatTaskType_withTypeName_returnsFormattedString() {
    String result = WorkflowSupport.formatTaskType("Task");
    assertEquals("(Task)", result);
  }

  @Test
  void formatTaskType_withEmptyString_returnsEmptyParentheses() {
    String result = WorkflowSupport.formatTaskType("");
    assertEquals("()", result);
  }

  @Test
  void formatTaskType_withSpecialCharacters_preservesCharacters() {
    String result = WorkflowSupport.formatTaskType("HTTP-GET");
    assertEquals("(HTTP-GET)", result);
  }

  @Test
  void formatTaskType_differentFromWorkflowType() {
    String workflowType = WorkflowSupport.formatWorkflowType("Test");
    String taskType = WorkflowSupport.formatTaskType("Test");

    assertNotEquals(workflowType, taskType);
    assertEquals("[Test]", workflowType);
    assertEquals("(Test)", taskType);
  }
}
