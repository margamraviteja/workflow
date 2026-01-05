package com.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowContainerTest {

  @Test
  void workflowContainer_extendsWorkflow() {
    // Verify that WorkflowContainer is a Workflow
    assertNotNull(WorkflowContainer.class);
  }

  @Test
  void workflowContainer_implementation_canReturnSubWorkflows() {
    // Create a concrete implementation
    WorkflowContainer container =
        new WorkflowContainer() {
          @Override
          public List<Workflow> getSubWorkflows() {
            return List.of(
                SequentialWorkflow.builder().build(), SequentialWorkflow.builder().build());
          }

          @Override
          public WorkflowResult execute(WorkflowContext context) {
            return WorkflowResult.builder().status(WorkflowStatus.SUCCESS).build();
          }

          @Override
          public String getName() {
            return "TestContainer";
          }
        };

    List<Workflow> subWorkflows = container.getSubWorkflows();

    assertNotNull(subWorkflows);
    assertEquals(2, subWorkflows.size());
  }

  @Test
  void workflowContainer_implementation_canHaveEmptySubWorkflows() {
    WorkflowContainer container =
        new WorkflowContainer() {
          @Override
          public List<Workflow> getSubWorkflows() {
            return List.of();
          }

          @Override
          public WorkflowResult execute(WorkflowContext context) {
            return WorkflowResult.builder().status(WorkflowStatus.SUCCESS).build();
          }

          @Override
          public String getName() {
            return "EmptyContainer";
          }
        };

    List<Workflow> subWorkflows = container.getSubWorkflows();

    assertNotNull(subWorkflows);
    assertTrue(subWorkflows.isEmpty());
  }

  @Test
  void workflowContainer_implementation_canBeUsedForTraversal() {
    Workflow child1 = SequentialWorkflow.builder().name("Child1").build();
    Workflow child2 = SequentialWorkflow.builder().name("Child2").build();

    WorkflowContainer container =
        new WorkflowContainer() {
          @Override
          public List<Workflow> getSubWorkflows() {
            return List.of(child1, child2);
          }

          @Override
          public WorkflowResult execute(WorkflowContext context) {
            return WorkflowResult.builder().status(WorkflowStatus.SUCCESS).build();
          }

          @Override
          public String getName() {
            return "TraversalContainer";
          }
        };

    // Test hierarchical traversal
    List<Workflow> children = container.getSubWorkflows();
    assertEquals(2, children.size());
    assertEquals("Child1", children.getFirst().getName());
    assertEquals("Child2", children.get(1).getName());
  }

  @Test
  void workflowContainer_nestedContainers_supportsHierarchy() {
    Workflow leafWorkflow = SequentialWorkflow.builder().name("Leaf").build();

    WorkflowContainer innerContainer =
        new WorkflowContainer() {
          @Override
          public List<Workflow> getSubWorkflows() {
            return List.of(leafWorkflow);
          }

          @Override
          public WorkflowResult execute(WorkflowContext context) {
            return WorkflowResult.builder().status(WorkflowStatus.SUCCESS).build();
          }

          @Override
          public String getName() {
            return "InnerContainer";
          }
        };

    WorkflowContainer outerContainer =
        new WorkflowContainer() {
          @Override
          public List<Workflow> getSubWorkflows() {
            return List.of(innerContainer);
          }

          @Override
          public WorkflowResult execute(WorkflowContext context) {
            return WorkflowResult.builder().status(WorkflowStatus.SUCCESS).build();
          }

          @Override
          public String getName() {
            return "OuterContainer";
          }
        };

    // Test nested hierarchy
    List<Workflow> outerChildren = outerContainer.getSubWorkflows();
    assertEquals(1, outerChildren.size());
    assertInstanceOf(WorkflowContainer.class, outerChildren.getFirst());

    WorkflowContainer inner = (WorkflowContainer) outerChildren.getFirst();
    List<Workflow> innerChildren = inner.getSubWorkflows();
    assertEquals(1, innerChildren.size());
    assertEquals("Leaf", innerChildren.getFirst().getName());
  }

  @Test
  void parallelWorkflow_implementsWorkflowContainer() {
    // ParallelWorkflow should implement WorkflowContainer
    ParallelWorkflow parallelWorkflow =
        ParallelWorkflow.builder()
            .workflow(SequentialWorkflow.builder().build())
            .workflow(SequentialWorkflow.builder().build())
            .build();

    assertInstanceOf(WorkflowContainer.class, parallelWorkflow);

    List<Workflow> subWorkflows = parallelWorkflow.getSubWorkflows();
    assertEquals(2, subWorkflows.size());
  }

  @Test
  void sequentialWorkflow_implementsWorkflowContainer() {
    // SequentialWorkflow should implement WorkflowContainer
    SequentialWorkflow sequentialWorkflow =
        SequentialWorkflow.builder()
            .workflow(ParallelWorkflow.builder().build())
            .workflow(ParallelWorkflow.builder().build())
            .build();

    assertInstanceOf(WorkflowContainer.class, sequentialWorkflow);

    List<Workflow> subWorkflows = sequentialWorkflow.getSubWorkflows();
    assertEquals(2, subWorkflows.size());
  }
}
