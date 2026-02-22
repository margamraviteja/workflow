package com.workflow.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.exception.WorkflowBuildException;
import com.workflow.registry.WorkflowRegistry;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Comprehensive test suite for DatabaseWorkflowProcessor database component.
 *
 * <p>Tests cover workflow building with parallel and sequential execution, error handling, database
 * connection failures, workflow resolution, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseWorkflowProcessor Robustness Tests")
class DatabaseWorkflowProcessorRobustnessTest {

  @Mock private DataSource mockDataSource;

  @Mock private WorkflowConfigRepository mockRepository;

  @Mock private WorkflowRegistry mockRegistry;

  @Mock private Workflow mockWorkflow1;

  @Mock private Workflow mockWorkflow2;

  @Mock private Workflow mockWorkflow3;

  private DatabaseWorkflowProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new DatabaseWorkflowProcessor(mockRepository, mockRegistry);
  }

  @Nested
  @DisplayName("Workflow Building with Parallel Execution")
  class ParallelWorkflowTests {

    @Test
    @DisplayName("Should build parallel workflow successfully")
    void testBuildParallelWorkflow() throws SQLException {
      // Given
      String workflowName = "ParallelDataPipeline";
      WorkflowMetadata metadata =
          new WorkflowMetadata(
              workflowName, "Parallel data processing pipeline", true, true, false);
      List<WorkflowStepMetadata> steps =
          List.of(
              new WorkflowStepMetadata("Step1", "First step", "ValidationWorkflow", 1),
              new WorkflowStepMetadata("Step2", "Second step", "ProcessingWorkflow", 2));

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenReturn(steps);
      when(mockRegistry.getWorkflow("ValidationWorkflow")).thenReturn(Optional.of(mockWorkflow1));
      when(mockRegistry.getWorkflow("ProcessingWorkflow")).thenReturn(Optional.of(mockWorkflow2));

      // When
      Workflow result = processor.buildWorkflow(workflowName);

      // Then
      assertNotNull(result);
      assertInstanceOf(ParallelWorkflow.class, result);
      assertEquals(workflowName, result.getName());

      verify(mockRepository).getWorkflow(workflowName);
      verify(mockRepository).getWorkflowSteps(workflowName);
      verify(mockRegistry).getWorkflow("ValidationWorkflow");
      verify(mockRegistry).getWorkflow("ProcessingWorkflow");
    }

    @Test
    @DisplayName("Should build parallel workflow with failFast and shareContext settings")
    void testBuildParallelWorkflowWithSettings() throws SQLException {
      // Given
      String workflowName = "ParallelPipelineWithSettings";
      WorkflowMetadata metadata =
          new WorkflowMetadata(
              workflowName, "Parallel pipeline with custom settings", true, false, true);
      List<WorkflowStepMetadata> steps =
          List.of(new WorkflowStepMetadata("Step1", "First step", "TaskWorkflow1", 1));

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenReturn(steps);
      when(mockRegistry.getWorkflow("TaskWorkflow1")).thenReturn(Optional.of(mockWorkflow1));

      // When
      Workflow result = processor.buildWorkflow(workflowName);

      // Then
      assertNotNull(result);
      assertInstanceOf(ParallelWorkflow.class, result);
      assertEquals(workflowName, result.getName());
    }

    @Test
    @DisplayName("Should handle parallel workflow with multiple steps")
    void testBuildParallelWorkflowWithMultipleSteps() throws SQLException {
      // Given
      String workflowName = "ComplexParallelWorkflow";
      WorkflowMetadata metadata =
          new WorkflowMetadata(workflowName, "Complex parallel workflow", true, true, true);
      List<WorkflowStepMetadata> steps =
          List.of(
              new WorkflowStepMetadata("Step1", "Validation", "ValidationWorkflow", 1),
              new WorkflowStepMetadata("Step2", "Processing", "ProcessingWorkflow", 2),
              new WorkflowStepMetadata("Step3", "Transformation", "TransformWorkflow", 3),
              new WorkflowStepMetadata("Step4", "Notification", "NotificationWorkflow", 4));

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenReturn(steps);
      when(mockRegistry.getWorkflow("ValidationWorkflow")).thenReturn(Optional.of(mockWorkflow1));
      when(mockRegistry.getWorkflow("ProcessingWorkflow")).thenReturn(Optional.of(mockWorkflow2));
      when(mockRegistry.getWorkflow("TransformWorkflow")).thenReturn(Optional.of(mockWorkflow3));
      when(mockRegistry.getWorkflow("NotificationWorkflow")).thenReturn(Optional.of(mockWorkflow3));

      // When
      Workflow result = processor.buildWorkflow(workflowName);

      // Then
      assertNotNull(result);
      assertInstanceOf(ParallelWorkflow.class, result);
      assertEquals(workflowName, result.getName());

      verify(mockRegistry, times(4)).getWorkflow(anyString());
    }
  }

  @Nested
  @DisplayName("Workflow Building with Sequential Execution")
  class SequentialWorkflowTests {

    @Test
    @DisplayName("Should build sequential workflow successfully")
    void testBuildSequentialWorkflow() throws SQLException {
      // Given
      String workflowName = "SequentialDataPipeline";
      WorkflowMetadata metadata =
          new WorkflowMetadata(
              workflowName, "Sequential data processing pipeline", false, true, false);
      List<WorkflowStepMetadata> steps =
          List.of(
              new WorkflowStepMetadata("Step1", "First step", "ValidationWorkflow", 1),
              new WorkflowStepMetadata("Step2", "Second step", "ProcessingWorkflow", 2));

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenReturn(steps);
      when(mockRegistry.getWorkflow("ValidationWorkflow")).thenReturn(Optional.of(mockWorkflow1));
      when(mockRegistry.getWorkflow("ProcessingWorkflow")).thenReturn(Optional.of(mockWorkflow2));

      // When
      Workflow result = processor.buildWorkflow(workflowName);

      // Then
      assertNotNull(result);
      assertInstanceOf(SequentialWorkflow.class, result);
      assertEquals(workflowName, result.getName());

      verify(mockRepository).getWorkflow(workflowName);
      verify(mockRepository).getWorkflowSteps(workflowName);
      verify(mockRegistry).getWorkflow("ValidationWorkflow");
      verify(mockRegistry).getWorkflow("ProcessingWorkflow");
    }

    @Test
    @DisplayName("Should build sequential workflow with single step")
    void testBuildSequentialWorkflowWithSingleStep() throws SQLException {
      // Given
      String workflowName = "SingleStepWorkflow";
      WorkflowMetadata metadata =
          new WorkflowMetadata(workflowName, "Single step workflow", false, false, false);
      List<WorkflowStepMetadata> steps =
          List.of(new WorkflowStepMetadata("Step1", "Only step", "SingleTaskWorkflow", 1));

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenReturn(steps);
      when(mockRegistry.getWorkflow("SingleTaskWorkflow")).thenReturn(Optional.of(mockWorkflow1));

      // When
      Workflow result = processor.buildWorkflow(workflowName);

      // Then
      assertNotNull(result);
      assertInstanceOf(SequentialWorkflow.class, result);
      assertEquals(workflowName, result.getName());
    }
  }

  @Nested
  @DisplayName("Error Handling and Robustness")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should throw exception when workflow not found")
    void testMissingWorkflowHandling() throws SQLException {
      // Given
      String workflowName = "NonExistentWorkflow";

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.empty());

      // When & Then
      WorkflowBuildException exception =
          assertThrows(WorkflowBuildException.class, () -> processor.buildWorkflow(workflowName));

      assertEquals("Workflow not found in database: " + workflowName, exception.getMessage());
      assertInstanceOf(IllegalArgumentException.class, exception.getCause());

      verify(mockRepository).getWorkflow(workflowName);
      verify(mockRepository, never()).getWorkflowSteps(anyString());
    }

    @Test
    @DisplayName("Should throw exception when workflow steps not found")
    void testMissingWorkflowStepsHandling() throws SQLException {
      // Given
      String workflowName = "WorkflowWithoutSteps";
      WorkflowMetadata metadata =
          new WorkflowMetadata(workflowName, "Workflow without steps", false, true, false);

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenReturn(List.of());

      // When & Then
      WorkflowBuildException exception =
          assertThrows(WorkflowBuildException.class, () -> processor.buildWorkflow(workflowName));

      assertEquals("No workflow steps found for workflow: " + workflowName, exception.getMessage());
      assertInstanceOf(IllegalArgumentException.class, exception.getCause());

      verify(mockRepository).getWorkflow(workflowName);
      verify(mockRepository).getWorkflowSteps(workflowName);
    }

    @Test
    @DisplayName("Should throw exception when workflow instance not found in registry")
    void testMissingWorkflowInstanceResolution() throws SQLException {
      // Given
      String workflowName = "WorkflowWithMissingInstance";
      WorkflowMetadata metadata =
          new WorkflowMetadata(workflowName, "Workflow with missing instance", false, true, false);
      List<WorkflowStepMetadata> steps =
          List.of(new WorkflowStepMetadata("Step1", "First step", "MissingWorkflow", 1));

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenReturn(steps);
      when(mockRegistry.getWorkflow("MissingWorkflow")).thenReturn(Optional.empty());

      // When & Then
      WorkflowBuildException exception =
          assertThrows(WorkflowBuildException.class, () -> processor.buildWorkflow(workflowName));

      assertEquals(
          "Workflow instance not found in registry: MissingWorkflow", exception.getMessage());
      assertInstanceOf(IllegalArgumentException.class, exception.getCause());

      verify(mockRegistry).getWorkflow("MissingWorkflow");
    }

    @Test
    @DisplayName("Should handle database connection failure")
    void testDatabaseConnectionFailure() throws SQLException {
      // Given
      String workflowName = "TestWorkflow";
      SQLException sqlException = new SQLException("Connection failed");

      when(mockRepository.getWorkflow(workflowName)).thenThrow(sqlException);

      // When & Then
      WorkflowBuildException exception =
          assertThrows(WorkflowBuildException.class, () -> processor.buildWorkflow(workflowName));

      assertEquals(
          "Database error while building workflow: " + workflowName, exception.getMessage());
      assertEquals(sqlException, exception.getCause());

      verify(mockRepository).getWorkflow(workflowName);
    }

    @Test
    @DisplayName("Should handle SQL exception during workflow steps fetch")
    void testWorkflowStepsFetchFailure() throws SQLException {
      // Given
      String workflowName = "TestWorkflow";
      WorkflowMetadata metadata =
          new WorkflowMetadata(workflowName, "Test workflow", false, true, false);
      SQLException sqlException = new SQLException("Failed to fetch steps");

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenThrow(sqlException);

      // When & Then
      WorkflowBuildException exception =
          assertThrows(WorkflowBuildException.class, () -> processor.buildWorkflow(workflowName));

      assertEquals(
          "Database error while building workflow: " + workflowName, exception.getMessage());
      assertEquals(sqlException, exception.getCause());
    }
  }

  @Nested
  @DisplayName("Workflow Existence and Retrieval")
  class WorkflowRetrievalTests {

    @Test
    @DisplayName("Should check workflow existence successfully")
    void testWorkflowExistenceCheck() throws SQLException {
      // Given
      String workflowName = "ExistingWorkflow";

      when(mockRepository.workflowExists(workflowName)).thenReturn(true);

      // When
      boolean exists = processor.workflowExists(workflowName);

      // Then
      assertTrue(exists);
      verify(mockRepository).workflowExists(workflowName);
    }

    @Test
    @DisplayName("Should return false when workflow does not exist")
    void testNonExistentWorkflowCheck() throws SQLException {
      // Given
      String workflowName = "NonExistentWorkflow";

      when(mockRepository.workflowExists(workflowName)).thenReturn(false);

      // When
      boolean exists = processor.workflowExists(workflowName);

      // Then
      assertFalse(exists);
      verify(mockRepository).workflowExists(workflowName);
    }

    @Test
    @DisplayName("Should handle database error during workflow existence check")
    void testWorkflowExistenceCheckDatabaseError() throws SQLException {
      // Given
      String workflowName = "TestWorkflow";
      SQLException sqlException = new SQLException("Database error");

      when(mockRepository.workflowExists(workflowName)).thenThrow(sqlException);

      // When
      boolean exists = processor.workflowExists(workflowName);

      // Then
      assertFalse(exists);
      verify(mockRepository).workflowExists(workflowName);
    }

    @Test
    @DisplayName("Should fetch all workflows successfully")
    void testGetAllWorkflows() throws SQLException {
      // Given
      List<WorkflowMetadata> expectedWorkflows =
          List.of(
              new WorkflowMetadata("Workflow1", "First workflow", false, true, false),
              new WorkflowMetadata("Workflow2", "Second workflow", true, false, true));

      when(mockRepository.getAllWorkflows()).thenReturn(expectedWorkflows);

      // When
      List<WorkflowMetadata> result = processor.getAllWorkflows();

      // Then
      assertEquals(expectedWorkflows, result);
      verify(mockRepository).getAllWorkflows();
    }

    @Test
    @DisplayName("Should return empty list when database error occurs during fetch all")
    void testGetAllWorkflowsDatabaseError() throws SQLException {
      // Given
      SQLException sqlException = new SQLException("Database error");

      when(mockRepository.getAllWorkflows()).thenThrow(sqlException);

      // When
      List<WorkflowMetadata> result = processor.getAllWorkflows();

      // Then
      assertTrue(result.isEmpty());
      verify(mockRepository).getAllWorkflows();
    }

    @Test
    @DisplayName("Should return empty list when no workflows exist")
    void testGetAllWorkflowsEmpty() throws SQLException {
      // Given
      when(mockRepository.getAllWorkflows()).thenReturn(List.of());

      // When
      List<WorkflowMetadata> result = processor.getAllWorkflows();

      // Then
      assertTrue(result.isEmpty());
      verify(mockRepository).getAllWorkflows();
    }
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should create processor with DataSource constructor")
    void testDataSourceConstructor() {
      // When
      DatabaseWorkflowProcessor localProcessor =
          new DatabaseWorkflowProcessor(mockDataSource, mockRegistry);

      // Then
      assertNotNull(localProcessor);
      // Note: We can't easily access the private repository field to verify
      // but the constructor should not throw exceptions
    }

    @Test
    @DisplayName("Should create processor with repository constructor")
    void testRepositoryConstructor() {
      // When
      DatabaseWorkflowProcessor localProcessor2 =
          new DatabaseWorkflowProcessor(mockRepository, mockRegistry);

      // Then
      assertNotNull(localProcessor2);
    }

    @Test
    @DisplayName("Should handle null DataSource gracefully")
    void testNullDataSourceConstructor() {
      // Given & When & Then
      assertDoesNotThrow(() -> new DatabaseWorkflowProcessor((DataSource) null, mockRegistry));
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCaseTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideEdgeCaseData")
    void testWorkflowEdgeCases(
        String testDisplayName, String workflowName, String workflowDesc, String stepDesc)
        throws SQLException {

      // Given
      WorkflowMetadata metadata =
          new WorkflowMetadata(workflowName, workflowDesc, false, true, false);
      List<WorkflowStepMetadata> steps =
          List.of(new WorkflowStepMetadata("Step1", stepDesc, "TestWorkflow", 1));

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenReturn(steps);
      when(mockRegistry.getWorkflow("TestWorkflow")).thenReturn(Optional.of(mockWorkflow1));

      // When
      Workflow result = processor.buildWorkflow(workflowName);

      // Then
      assertNotNull(result, "Workflow should be built successfully for: " + testDisplayName);
      assertEquals(workflowName, result.getName());
    }

    private static Stream<Arguments> provideEdgeCaseData() {
      return Stream.of(
          Arguments.of(
              "Should handle workflow with null description",
              "WF_NullDesc",
              null,
              "Step description"),
          Arguments.of(
              "Should handle workflow step with null description",
              "WF_StepNullDesc",
              "WF description",
              null),
          Arguments.of(
              "Should handle empty workflow name", "", "Empty name workflow", "Step description"),
          Arguments.of(
              "Should handle workflow with special characters in name",
              "WF_@.-123",
              "Special chars",
              "Step description"));
    }
  }

  @Nested
  @DisplayName("Integration and Workflow Resolution")
  class IntegrationTests {

    @Test
    @DisplayName("Should resolve workflow instances in correct order")
    void testWorkflowInstanceResolutionOrder() throws SQLException {
      // Given
      String workflowName = "OrderedWorkflow";
      WorkflowMetadata metadata =
          new WorkflowMetadata(workflowName, "Ordered workflow", false, true, false);
      List<WorkflowStepMetadata> steps =
          List.of(
              new WorkflowStepMetadata("Step1", "First step", "FirstWorkflow", 1),
              new WorkflowStepMetadata("Step2", "Second step", "SecondWorkflow", 2),
              new WorkflowStepMetadata("Step3", "Third step", "ThirdWorkflow", 3));

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenReturn(steps);
      when(mockRegistry.getWorkflow("FirstWorkflow")).thenReturn(Optional.of(mockWorkflow1));
      when(mockRegistry.getWorkflow("SecondWorkflow")).thenReturn(Optional.of(mockWorkflow2));
      when(mockRegistry.getWorkflow("ThirdWorkflow")).thenReturn(Optional.of(mockWorkflow3));

      // When
      Workflow result = processor.buildWorkflow(workflowName);

      // Then
      assertNotNull(result);
      assertInstanceOf(SequentialWorkflow.class, result);

      // Verify resolution order
      var inOrder = inOrder(mockRegistry);
      inOrder.verify(mockRegistry).getWorkflow("FirstWorkflow");
      inOrder.verify(mockRegistry).getWorkflow("SecondWorkflow");
      inOrder.verify(mockRegistry).getWorkflow("ThirdWorkflow");
    }

    @Test
    @DisplayName("Should handle workflow instance resolution with same instance multiple times")
    void testRepeatedWorkflowInstanceResolution() throws SQLException {
      // Given
      String workflowName = "RepeatedInstanceWorkflow";
      WorkflowMetadata metadata =
          new WorkflowMetadata(workflowName, "Repeated instance workflow", false, true, false);
      List<WorkflowStepMetadata> steps =
          List.of(
              new WorkflowStepMetadata("Step1", "First step", "SharedWorkflow", 1),
              new WorkflowStepMetadata("Step2", "Second step", "SharedWorkflow", 2));

      when(mockRepository.getWorkflow(workflowName)).thenReturn(Optional.of(metadata));
      when(mockRepository.getWorkflowSteps(workflowName)).thenReturn(steps);
      when(mockRegistry.getWorkflow("SharedWorkflow")).thenReturn(Optional.of(mockWorkflow1));

      // When
      Workflow result = processor.buildWorkflow(workflowName);

      // Then
      assertNotNull(result);
      assertInstanceOf(SequentialWorkflow.class, result);

      // Verify the same workflow instance was resolved twice
      verify(mockRegistry, times(2)).getWorkflow("SharedWorkflow");
    }
  }
}
