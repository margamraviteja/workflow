package com.workflow.annotation.spring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration for Spring-based workflow annotation processing. This configuration is
 * automatically enabled when Spring Boot is on the classpath.
 *
 * <p>To use Spring annotation processing in your application:
 *
 * <pre>{@code
 * @SpringBootApplication
 * public class MyApplication {
 *   public static void main(String[] args) {
 *     SpringApplication.run(MyApplication.class, args);
 *   }
 * }
 *
 * @Component
 * @WorkflowAnnotation(name = "MyWorkflow")
 * public class MyWorkflowDefinition {
 *   @WorkflowMethod(order = 1)
 *   public Workflow step1(@Autowired MyService service) {
 *     return ...;
 *   }
 * }
 * }</pre>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ApplicationContext.class)
public class WorkflowAnnotationAutoConfiguration {

  /**
   * Creates a Spring annotation workflow processor bean if one doesn't already exist.
   *
   * @param applicationContext the Spring application context
   * @return the processor bean
   */
  @Bean
  @ConditionalOnMissingBean
  public SpringAnnotationWorkflowProcessor springAnnotationWorkflowProcessor(
      ApplicationContext applicationContext) {
    log.info("Initializing Spring Annotation Workflow Processor");
    return new SpringAnnotationWorkflowProcessor(applicationContext);
  }
}
