package com.workflow.exception;

/** Exception thrown when there is an error building a workflow. */
public class WorkflowBuildException extends RuntimeException {
  public WorkflowBuildException(String message) {
    super(message);
  }

  public WorkflowBuildException(String message, Throwable cause) {
    super(message, cause);
  }
}
