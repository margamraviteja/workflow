package com.workflow.exception;

/**
 * Exception thrown when a circular dependency is detected in workflow composition.
 *
 * <p><b>What is a Circular Dependency:</b> Occurs when workflow A includes workflow B, and workflow
 * B (directly or indirectly) includes workflow A. This creates an infinite loop during workflow
 * execution or construction.
 *
 * <p><b>Why They're Prevented:</b>
 *
 * <ul>
 *   <li>Would cause infinite loops during execution
 *   <li>Would cause infinite recursion during composition analysis
 *   <li>Indicate a design flaw in workflow structure
 * </ul>
 *
 * <p><b>When Thrown:</b> Detected during workflow composition or validation, not during execution.
 * Gives early warning of structural problems.
 *
 * <p><b>Examples of Circular Dependencies:</b>
 *
 * <ul>
 *   <li>WorkflowA contains WorkflowB, WorkflowB contains WorkflowA
 *   <li>WorkflowA contains WorkflowB, WorkflowB contains WorkflowC, WorkflowC contains WorkflowA
 * </ul>
 *
 * <p><b>How to Fix:</b>
 *
 * <ul>
 *   <li>Refactor workflows to remove the cycle
 *   <li>Extract common logic to a separate, reusable workflow
 *   <li>Use composition instead of inheritance
 *   <li>Create separate workflows for different concerns
 *   <li>Use fallback workflows for error handling instead of circular references
 * </ul>
 *
 * <p><b>Example - Bad Structure (Circular):</b>
 *
 * <pre>{@code
 * Workflow workflowA = new SequentialWorkflow();
 * Workflow workflowB = new SequentialWorkflow();
 *
 * // This is problematic:
 * // workflowA includes workflowB
 * // workflowB includes workflowA  <-- Circular!
 * }</pre>
 *
 * <p><b>Example - Good Structure (No Circular):</b>
 *
 * <pre>{@code
 * Workflow common = new CommonProcessingWorkflow();
 * Workflow branchA = SequentialWorkflow.builder()
 *     .workflow(common)
 *     .workflow(new SpecificProcessingA())
 *     .build();
 * Workflow branchB = SequentialWorkflow.builder()
 *     .workflow(common)
 *     .workflow(new SpecificProcessingB())
 *     .build();
 *
 * // No circles here - common is shared but doesn't reference branchA or branchB
 * }</pre>
 *
 * @see com.workflow.Workflow
 * @see WorkflowCompositionException
 */
public class CircularDependencyException extends WorkflowCompositionException {
  /**
   * Creates a new circular dependency exception with a message.
   *
   * @param message the exception message
   */
  public CircularDependencyException(String message) {
    super(message);
  }

  /**
   * Creates a new circular dependency exception with a message and cause.
   *
   * @param message the exception message
   * @param cause the underlying cause
   */
  public CircularDependencyException(String message, Throwable cause) {
    super(message, cause);
  }
}
