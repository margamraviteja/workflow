package com.workflow.registry.spring;

import com.workflow.Workflow;
import com.workflow.registry.WorkflowRegistry;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * A Spring-integrated workflow registry that performs lookups directly against the Spring
 * ApplicationContext.
 */
@Slf4j
@Component
public class SpringWorkflowRegistry extends WorkflowRegistry {

  private final ApplicationContext applicationContext;

  public SpringWorkflowRegistry(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * Retrieves a workflow bean from the Spring context by its name. Overrides the base method to
   * provide Spring-managed singleton instances.
   */
  @Override
  public synchronized Optional<Workflow> getWorkflow(String name) {
    try {
      // Attempt to find the bean by name in the Spring Context
      Workflow bean = applicationContext.getBean(name, Workflow.class);
      return Optional.of(bean);
    } catch (BeansException _) {
      // Fallback to the parent cache if manual registration was used
      return super.getWorkflow(name);
    }
  }

  /** Checks the Spring context to see if a workflow bean exists with the given name. */
  @Override
  public synchronized boolean isRegistered(String name) {
    return applicationContext.containsBean(name) || super.isRegistered(name);
  }
}
