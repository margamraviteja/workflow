package com.workflow.context;

import java.util.Objects;
import tools.jackson.core.type.TypeReference;

public final class ScopedWorkflowContext extends WorkflowContext {
  private final WorkflowContext delegate;
  private final String prefix;

  ScopedWorkflowContext(WorkflowContext delegate, String prefix) {
    // Pass the delegate's listeners to ensure the scoped context uses the same event bus
    super(delegate.getListeners());
    this.delegate = delegate;
    this.prefix = prefix.endsWith(".") ? prefix : prefix + ".";
  }

  private String scoped(String key) {
    return prefix + key;
  }

  // --- String Key Overrides ---

  @Override
  public <T> void put(String key, T value) {
    delegate.put(scoped(key), value);
  }

  @Override
  public Object get(String key) {
    return delegate.get(scoped(key));
  }

  @Override
  public Object get(String key, Object defaultValue) {
    return delegate.get(scoped(key), defaultValue);
  }

  @Override
  public <T> T getTyped(String key, Class<T> type) {
    return delegate.getTyped(scoped(key), type);
  }

  @Override
  public <T> T getTyped(String key, Class<T> type, T defaultValue) {
    return delegate.getTyped(scoped(key), type, defaultValue);
  }

  @Override
  public boolean containsKey(String key) {
    return delegate.containsKey(scoped(key));
  }

  // --- TypedKey Overrides (This was causing your test failure) ---

  @Override
  public <T> void put(TypedKey<T> key, T value) {
    Objects.requireNonNull(key, "TypedKey must not be null");
    // We create a temporary namespaced string key or a new TypedKey
    // delegation is simplest via the string put
    delegate.put(scoped(key.name()), value);
  }

  @Override
  public <T> T get(TypedKey<T> key) {
    Object value = delegate.get(scoped(key.name()));
    return value == null ? null : key.type().cast(value);
  }

  @Override
  public <T> T getStrict(TypedKey<T> key) {
    // Delegate logic but use scoped name
    Object value = delegate.get(scoped(key.name()));
    if (value == null) return null;
    if (!key.type().isInstance(value)) {
      throw new IllegalStateException("Key " + scoped(key.name()) + " type mismatch");
    }
    return key.type().cast(value);
  }

  // --- TypeReference Overrides ---

  @Override
  public <T> T getTyped(String key, TypeReference<T> typeRef) {
    return delegate.getTyped(scoped(key), typeRef);
  }

  @Override
  public <T> T getTyped(String key, TypeReference<T> typeRef, T defaultValue) {
    return delegate.getTyped(scoped(key), typeRef, defaultValue);
  }
}
