package com.workflow.registry.spring;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.workflow.SequentialWorkflow;
import com.workflow.Workflow;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class SpringWorkflowRegistryTest {

  @Mock private ApplicationContext applicationContext;

  private SpringWorkflowRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new SpringWorkflowRegistry(applicationContext);
  }

  @Test
  void constructor_withApplicationContext_createsInstance() {
    assertNotNull(registry);
  }

  @Test
  void getWorkflow_whenBeanExistsInSpringContext_returnsWorkflow() {
    Workflow mockWorkflow = mock(Workflow.class);
    when(applicationContext.getBean("testWorkflow", Workflow.class)).thenReturn(mockWorkflow);

    Optional<Workflow> result = registry.getWorkflow("testWorkflow");

    assertTrue(result.isPresent());
    assertEquals(mockWorkflow, result.get());
    verify(applicationContext).getBean("testWorkflow", Workflow.class);
  }

  @Test
  void getWorkflow_whenBeanDoesNotExistInSpringContext_fallsBackToParentCache() {
    when(applicationContext.getBean("testWorkflow", Workflow.class))
        .thenThrow(new NoSuchBeanDefinitionException("testWorkflow"));

    // Manually register in parent registry
    Workflow workflow = SequentialWorkflow.builder().build();
    registry.register("testWorkflow", workflow);

    Optional<Workflow> result = registry.getWorkflow("testWorkflow");

    assertTrue(result.isPresent());
    assertEquals(workflow, result.get());
  }

  @Test
  void getWorkflow_whenBeanNotFoundAnywhere_returnsEmpty() {
    when(applicationContext.getBean("nonExistent", Workflow.class))
        .thenThrow(new NoSuchBeanDefinitionException("nonExistent"));

    Optional<Workflow> result = registry.getWorkflow("nonExistent");

    assertFalse(result.isPresent());
  }

  @Test
  void isRegistered_whenBeanExistsInSpringContext_returnsTrue() {
    when(applicationContext.containsBean("testWorkflow")).thenReturn(true);

    boolean result = registry.isRegistered("testWorkflow");

    assertTrue(result);
    verify(applicationContext).containsBean("testWorkflow");
  }

  @Test
  void isRegistered_whenBeanExistsInParentCache_returnsTrue() {
    when(applicationContext.containsBean("testWorkflow")).thenReturn(false);

    // Register in parent registry
    Workflow workflow = SequentialWorkflow.builder().build();
    registry.register("testWorkflow", workflow);

    boolean result = registry.isRegistered("testWorkflow");

    assertTrue(result);
  }

  @Test
  void isRegistered_whenBeanNotFoundAnywhere_returnsFalse() {
    when(applicationContext.containsBean("nonExistent")).thenReturn(false);

    boolean result = registry.isRegistered("nonExistent");

    assertFalse(result);
  }

  @Test
  void getWorkflow_prioritizesSpringContextOverParentCache() {
    Workflow springWorkflow = mock(Workflow.class);
    Workflow parentWorkflow = SequentialWorkflow.builder().build();

    when(applicationContext.getBean("testWorkflow", Workflow.class)).thenReturn(springWorkflow);

    // Register in parent registry
    registry.register("testWorkflow", parentWorkflow);

    Optional<Workflow> result = registry.getWorkflow("testWorkflow");

    assertTrue(result.isPresent());
    assertEquals(springWorkflow, result.get());
    assertNotEquals(parentWorkflow, result.get());
  }

  @Test
  void registerWorkflow_andGetWorkflow_whenNotInSpringContext_worksCorrectly() {
    when(applicationContext.getBean("customWorkflow", Workflow.class))
        .thenThrow(new NoSuchBeanDefinitionException("customWorkflow"));

    Workflow workflow = SequentialWorkflow.builder().build();
    registry.register("customWorkflow", workflow);

    Optional<Workflow> result = registry.getWorkflow("customWorkflow");

    assertTrue(result.isPresent());
    assertEquals(workflow, result.get());
  }

  @Test
  void getWorkflow_multipleCallsWithSameWorkflow_returnsSameInstance() {
    Workflow mockWorkflow = mock(Workflow.class);
    when(applicationContext.getBean("testWorkflow", Workflow.class)).thenReturn(mockWorkflow);

    Optional<Workflow> result1 = registry.getWorkflow("testWorkflow");
    Optional<Workflow> result2 = registry.getWorkflow("testWorkflow");

    assertTrue(result1.isPresent());
    assertTrue(result2.isPresent());
    assertSame(result1.get(), result2.get());
  }

  @Test
  void getWorkflow_withNullName_fallsBackToParentBehavior() {
    when(applicationContext.getBean(isNull(), eq(Workflow.class)))
        .thenThrow(new NoSuchBeanDefinitionException("null"));

    Optional<Workflow> result = registry.getWorkflow(null);

    assertFalse(result.isPresent());
  }
}
