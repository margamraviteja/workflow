package com.workflow.context;

public final class TestContexts {
  private TestContexts() {}

  public static WorkflowContext ctx(Object... kvs) {
    WorkflowContext ctx = new WorkflowContext();
    for (int i = 0; i < kvs.length; i += 2) {
      ctx.put((String) kvs[i], kvs[i + 1]);
    }
    return ctx;
  }

  public static WorkflowContext emptyContext() {
    return new WorkflowContext();
  }
}
