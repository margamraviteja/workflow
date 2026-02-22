package com.workflow.context;

import java.util.Objects;

/**
 * A typed key for storing and retrieving values from a context.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * TypedKey<String> USERNAME_KEY = TypedKey.of("username", String.class);
 * context.put(USERNAME_KEY, "alice");
 * String username = context.get(USERNAME_KEY);
 * }</pre>
 *
 * This ensures type safety when storing and retrieving values from the context.
 *
 * @param <T> the type of the value associated with this key
 */
public final class TypedKey<T> {

  private final String name;
  private final Class<T> type;

  private TypedKey(String name, Class<T> type) {
    this.name = Objects.requireNonNull(name);
    this.type = Objects.requireNonNull(type);
  }

  public static <T> TypedKey<T> of(String name, Class<T> type) {
    return new TypedKey<>(name, type);
  }

  public String name() {
    return name;
  }

  public Class<T> type() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypedKey<?> other)) return false;
    return name.equals(other.name) && type.equals(other.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }

  @Override
  public String toString() {
    return name + "<" + type.getSimpleName() + ">";
  }
}
