package com.workflow.helper;

import com.workflow.Workflow;
import com.workflow.WorkflowContainer;
import com.workflow.WorkflowResult;
import com.workflow.context.WorkflowContext;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * A specialized utility for rendering a {@link Workflow} hierarchy as a human-readable ASCII tree.
 *
 * <p>It uses the {@link WorkflowContainer} interface to traverse sub-workflows and the {@link
 * Workflow#getWorkflowType()} method to provide dynamic metadata in the tree output.
 */
@UtilityClass
public final class TreeRenderer {

  private static final String BRANCH = "├── ";
  private static final String LAST_BRANCH = "└── ";
  private static final String VERTICAL = "│   ";
  private static final String EMPTY = "    ";

  /** Renders a full workflow tree starting from the root. */
  public static String render(Workflow workflow) {
    if (workflow == null) return "";

    StringBuilder sb = new StringBuilder();
    // Start with the root node - usually the root doesn't have a branch
    // unless it's part of a larger context, but your test expects it.
    renderHeader(sb, workflow, "", true);

    // The prefix for children of the root should be "    " (EMPTY)
    // because the root is the "last" (and only) item at level 0.
    renderSubTree(sb, workflow, EMPTY);
    return sb.toString();
  }

  /** Standardizes the header line for any workflow node. */
  public static void renderHeader(
      StringBuilder sb, Workflow workflow, String prefix, boolean isLast) {
    String type = workflow.getWorkflowType();
    String suffix = (type == null || type.isBlank()) ? "" : " " + type.trim();

    sb.append(prefix)
        .append(isLast ? LAST_BRANCH : BRANCH)
        .append(workflow.getName())
        .append(suffix)
        .append("\n");
  }

  /**
   * Renders the children of a workflow node. Used by standard rendering and custom toTreeString
   * overrides.
   *
   * @param sb The builder to append to
   * @param parent The workflow whose children should be rendered
   * @param prefix The current indentation prefix
   */
  public static void renderSubTree(StringBuilder sb, Workflow parent, String prefix) {
    if (!(parent instanceof WorkflowContainer container)) {
      return;
    }

    List<Workflow> children = container.getSubWorkflows();
    if (children == null || children.isEmpty()) {
      return;
    }

    for (int i = 0; i < children.size(); i++) {
      Workflow child = children.get(i);
      boolean isLast = (i == children.size() - 1);

      // 1. Render the child's header
      renderHeader(sb, child, prefix, isLast);

      // 2. Prepare the prefix for this child's own subtree
      // If this child is NOT the last, we need a vertical bar for its siblings
      String nextPrefix = prefix + (isLast ? EMPTY : VERTICAL);

      // 3. Recurse
      renderSubTree(sb, child, nextPrefix);
    }
  }

  // A simple wrapper that mimics the expected string format
  public record TreeLabelWrapper(String label, Workflow delegate)
      implements Workflow, WorkflowContainer {
    @Override
    public String getName() {
      return label + " " + delegate.getName();
    }

    @Override
    public String getWorkflowType() {
      return delegate.getWorkflowType();
    }

    @Override
    public List<Workflow> getSubWorkflows() {
      return delegate instanceof WorkflowContainer c ? c.getSubWorkflows() : List.of();
    }

    @Override
    public WorkflowResult execute(WorkflowContext c) {
      return delegate.execute(c);
    }
  }
}
