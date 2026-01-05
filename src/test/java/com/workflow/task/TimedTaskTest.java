package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import com.workflow.sleeper.ThreadSleepingSleeper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TimedTaskTest {

  @Test
  void measuresExecutionTime() {
    Task delegate =
        _ -> {
          try {
            ThreadSleepingSleeper sleeper = new ThreadSleepingSleeper();
            sleeper.sleep(Duration.ofMillis(100));
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        };
    TimedTask task = new TimedTask(delegate, "duration");

    WorkflowContext ctx = new WorkflowContext();
    task.execute(ctx);

    Long duration = ctx.getTyped("duration", Long.class);
    assertNotNull(duration);
    assertTrue(duration > 0);
  }
}
