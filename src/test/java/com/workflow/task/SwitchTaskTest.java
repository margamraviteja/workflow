package com.workflow.task;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.context.WorkflowContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SwitchTaskTest {

  @Test
  void routesToCorrectTask() {
    WorkflowContext ctx = new WorkflowContext();
    ctx.put("status", "OK");

    Task okTask = c -> c.put("result", "success");
    Task failTask = c -> c.put("result", "failure");

    SwitchTask task =
        SwitchTask.builder()
            .switchKey("status")
            .cases(Map.of("OK", okTask))
            .defaultTask(failTask)
            .build();

    task.execute(ctx);
    assertEquals("success", ctx.get("result"));
  }
}
