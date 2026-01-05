package com.workflow.annotation.spring;

import com.workflow.ParallelWorkflow;
import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import com.workflow.annotation.WorkflowAnnotation;
import com.workflow.annotation.processor.AnnotationWorkflowProcessor;
import com.workflow.annotation.processor.WorkflowElement;
import com.workflow.annotation.processor.WorkflowElement.ElementType;
import com.workflow.annotation.processor.WorkflowRefMetadata;
import com.workflow.exception.WorkflowBuildException;
import com.workflow.exception.WorkflowCompositionException;
import com.workflow.task.Task;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * Spring-based annotation processor that integrates with Spring's dependency injection and
 * conditional configuration features.
 *
 * <p>This processor extends the pure Java annotation processor with Spring-specific features such
 * as:
 *
 * <ul>
 *   <li>Dependency injection via {@code @Autowired} on method parameters
 *   <li>Conditional workflow execution using Spring's {@code @ConditionalXXX} annotations
 *   <li>Integration with Spring application context
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @WorkflowAnnotation(name = "DataPipeline", parallel = false)
 * public class DataPipelineWorkflow {
 *
 *   @WorkflowMethod(name = "Ingest", order = 1)
 *   @ConditionalOnProperty(name = "data.ingest.enabled", havingValue = "true")
 *   public Workflow createIngestWorkflow(@Autowired DataSource dataSource) {
 *     return SequentialWorkflow.builder()
 *       .name("IngestWorkflow")
 *       .task(new IngestTask(dataSource))
 *       .build();
 *   }
 *
 *   @TaskMethod(name = "Transform", order = 2)
 *   public Task createTransformTask(@Autowired TransformConfig config) {
 *     return new TransformTask(config);
 *   }
 * }
 * }</pre>
 */
@Slf4j
public class SpringAnnotationWorkflowProcessor extends AnnotationWorkflowProcessor {

  private final ApplicationContext applicationContext;

  /**
   * Creates a new Spring annotation processor with the given application context.
   *
   * @param applicationContext the Spring application context for dependency injection
   */
  public SpringAnnotationWorkflowProcessor(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

  /**
   * Builds a workflow from an annotated class, using Spring for instantiation and dependency
   * injection.
   *
   * @param workflowClass the class annotated with {@link WorkflowAnnotation}
   * @return the constructed Workflow
   * @throws IllegalArgumentException if the class is not properly annotated
   * @throws WorkflowBuildException if workflow construction fails
   */
  @Override
  public Workflow buildWorkflow(Class<?> workflowClass) {
    WorkflowAnnotation annotation = workflowClass.getAnnotation(WorkflowAnnotation.class);
    if (annotation == null) {
      throw new IllegalArgumentException(
          "Class " + workflowClass.getName() + " is not annotated with @WorkflowAnnotation");
    }

    try {
      Object instance = applicationContext.getBean(workflowClass);
      return buildWorkflow(instance, annotation);
    } catch (WorkflowCompositionException e) {
      // Re-throw composition exceptions as-is (includes CircularDependencyException)
      throw e;
    } catch (Exception _) {
      // Fallback to default instantiation if not a Spring bean
      return super.buildWorkflow(workflowClass);
    }
  }

  @Override
  protected Workflow buildWorkflow(Object instance, WorkflowAnnotation annotation) {
    getLogger()
        .info("Building Spring-enabled workflow from class: {}", instance.getClass().getName());

    List<WorkflowElement> elements = extractWorkflowElements(instance.getClass());
    List<WorkflowRefMetadata> workflowRefMetadata = extractWorkflowRefs(instance.getClass());
    List<Workflow> workflows = new ArrayList<>();

    // Validate for circular dependencies
    validateNoDependencies(instance.getClass(), new HashSet<>());

    // Process workflow refs first
    for (WorkflowRefMetadata metadata : workflowRefMetadata) {
      Workflow workflowRef = buildWorkflow(metadata);
      try {
        // Inject the built workflow back into the field
        metadata.field().setAccessible(true);
        metadata.field().set(instance, workflowRef);
      } catch (IllegalAccessException e) {
        throw new WorkflowBuildException(e.getMessage(), e);
      }
    }

    // Process regular workflow/task elements
    for (WorkflowElement element : elements) {
      // Check Spring conditional annotations
      if (!isConditionallyEnabled(element.method())) {
        getLogger()
            .debug("Skipping method {} due to unsatisfied conditional", element.method().getName());
        continue;
      }

      try {
        Workflow workflow;
        if (element.type() == ElementType.WORKFLOW) {
          workflow = invokeWorkflowMethodWithInjection(element, instance);
        } else {
          Task task = invokeTaskMethodWithInjection(element, instance);
          workflow = wrapTaskAsWorkflow(task, element);
        }
        workflows.add(workflow);
      } catch (Exception e) {
        throw new WorkflowBuildException(
            "Failed to invoke method: " + element.method().getName(), e);
      }
    }

    String workflowName =
        annotation.name().isEmpty() ? instance.getClass().getSimpleName() : annotation.name();

    if (annotation.parallel()) {
      ParallelWorkflow.ParallelWorkflowBuilder builder =
          ParallelWorkflow.builder().name(workflowName).shareContext(annotation.shareContext());
      workflows.forEach(builder::workflow);
      return builder.build();
    } else {
      SequentialWorkflow.SequentialWorkflowBuilder builder =
          SequentialWorkflow.builder().name(workflowName);
      workflows.forEach(builder::workflow);
      return builder.build();
    }
  }

  private boolean isConditionallyEnabled(Method method) {
    // Check various Spring conditional annotations
    if (isConditionalOnBeanEnabled(method)) return false;
    if (isConditionalOnMissingBeanEnabled(method)) return false;
    if (isConditionalOnPropertyEnabled(method)) return false;
    return !isConditionalOnClassEnabled(method);
  }

  private boolean isConditionalOnClassEnabled(Method method) {
    ConditionalOnClass conditionalOnClass =
        AnnotationUtils.findAnnotation(method, ConditionalOnClass.class);
    if (conditionalOnClass != null) {
      for (String className : conditionalOnClass.name()) {
        try {
          Class.forName(className);
        } catch (ClassNotFoundException _) {
          getLogger().debug("Conditional check failed: class {} not found", className);
          return true;
        }
      }
    }
    return false;
  }

  private boolean isConditionalOnPropertyEnabled(Method method) {
    ConditionalOnProperty conditionalOnProperty =
        AnnotationUtils.findAnnotation(method, ConditionalOnProperty.class);
    if (conditionalOnProperty != null) {
      String propertyName =
          conditionalOnProperty.name().length > 0 ? conditionalOnProperty.name()[0] : "";
      String havingValue = conditionalOnProperty.havingValue();

      if (!propertyName.isEmpty()) {
        String actualValue = applicationContext.getEnvironment().getProperty(propertyName);
        if (!havingValue.equals(actualValue)) {
          getLogger()
              .debug(
                  "Conditional check failed: property {} has value {} but expected {}",
                  propertyName,
                  actualValue,
                  havingValue);
          return true;
        }
      }
    }
    return false;
  }

  private boolean isConditionalOnMissingBeanEnabled(Method method) {
    ConditionalOnMissingBean conditionalOnMissingBean =
        AnnotationUtils.findAnnotation(method, ConditionalOnMissingBean.class);
    if (conditionalOnMissingBean != null) {
      for (Class<?> beanClass : conditionalOnMissingBean.value()) {
        try {
          applicationContext.getBean(beanClass);
          getLogger().debug("Conditional check failed: bean {} exists", beanClass.getName());
          return true;
        } catch (Exception _) {
          // Bean doesn't exist, condition is satisfied
        }
      }
    }
    return false;
  }

  private boolean isConditionalOnBeanEnabled(Method method) {
    ConditionalOnBean conditionalOnBean =
        AnnotationUtils.findAnnotation(method, ConditionalOnBean.class);
    if (conditionalOnBean != null) {
      for (Class<?> beanClass : conditionalOnBean.value()) {
        try {
          applicationContext.getBean(beanClass);
        } catch (Exception _) {
          getLogger().debug("Conditional check failed: bean {} not found", beanClass.getName());
          return true;
        }
      }
    }
    return false;
  }

  private Workflow invokeWorkflowMethodWithInjection(WorkflowElement element, Object instance)
      throws InvocationTargetException, IllegalAccessException {
    Method method = element.method();
    Object[] args = resolveMethodParameters(method);
    Object result = method.invoke(instance, args);

    if (result instanceof Workflow workflow) {
      return workflow;
    }
    throw new IllegalStateException(
        "Method " + method.getName() + " did not return a Workflow instance");
  }

  private Task invokeTaskMethodWithInjection(WorkflowElement element, Object instance)
      throws InvocationTargetException, IllegalAccessException {
    Method method = element.method();
    Object[] args = resolveMethodParameters(method);
    Object result = method.invoke(instance, args);

    if (result instanceof Task task) {
      return task;
    }
    throw new IllegalStateException(
        "Method " + method.getName() + " did not return a Task instance");
  }

  private Object[] resolveMethodParameters(Method method) {
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    Map<String, Workflow> workflowRefMap = buildWorkflowRefParameterMap(method);

    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];

      // Check for @WorkflowRef first
      if (workflowRefMap.containsKey(parameter.getName())) {
        args[i] = workflowRefMap.get(parameter.getName());
        getLogger().debug("Injected Workflow for @WorkflowRef parameter: {}", parameter.getName());
      } else {
        // Fall back to Autowired injection
        Autowired autowired = parameter.getAnnotation(Autowired.class);

        if (autowired != null || parameter.isAnnotationPresent(Autowired.class)) {
          // Inject from Spring context
          args[i] = injectForAutowiredAnnotation(method, parameter, autowired);
        } else {
          // Try to inject anyway if available
          try {
            args[i] = applicationContext.getBean(parameter.getType());
          } catch (Exception _) {
            args[i] = null;
            getLogger()
                .warn(
                    "Cannot inject parameter {} of type {} in method {}",
                    parameter.getName(),
                    parameter.getType().getName(),
                    method.getName());
          }
        }
      }
    }

    return args;
  }

  private Object injectForAutowiredAnnotation(
      Method method, Parameter parameter, Autowired autowired) {
    try {
      Object obj = applicationContext.getBean(parameter.getType());
      getLogger()
          .debug(
              "Injected {} for parameter {} in method {}",
              parameter.getType().getSimpleName(),
              parameter.getName(),
              method.getName());
      return obj;
    } catch (Exception e) {
      if (autowired != null && autowired.required()) {
        throw new WorkflowBuildException(
            "Required bean of type "
                + parameter.getType().getName()
                + " not found for parameter "
                + parameter.getName(),
            e);
      }
      getLogger()
          .warn(
              "Optional bean {} not found for parameter {}",
              parameter.getType().getName(),
              parameter.getName());
    }
    return null;
  }
}
