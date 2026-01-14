package com.workflow.interpolation.resolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;

/**
 * A {@link PropertyResolver} that chains multiple resolvers together.
 *
 * <p>Resolvers are tried in order (sorted by {@link PropertyResolver#order()}) until one returns a
 * non-empty value. Lower order values have higher priority.
 *
 * @see PropertyResolver
 */
@Getter
public class CompositePropertyResolver implements PropertyResolver {

  private final List<PropertyResolver> resolvers;

  private CompositePropertyResolver(List<PropertyResolver> resolvers) {
    this.resolvers = List.copyOf(resolvers);
  }

  @Override
  public Optional<String> resolve(String key) {
    for (PropertyResolver resolver : resolvers) {
      if (resolver.supports(key)) {
        Optional<String> result = resolver.resolve(key);
        if (result.isPresent()) {
          return result;
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Creates a new builder for {@link CompositePropertyResolver}.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a composite resolver with the given resolvers.
   *
   * @param resolvers the resolvers to combine
   * @return a new composite resolver
   */
  public static CompositePropertyResolver of(PropertyResolver... resolvers) {
    Builder builder = builder();
    for (PropertyResolver resolver : resolvers) {
      builder.add(resolver);
    }
    return builder.build();
  }

  /** Builder for creating {@link CompositePropertyResolver} instances. */
  public static class Builder {
    private final List<PropertyResolver> resolvers = new ArrayList<>();

    /**
     * Add a resolver to the chain.
     *
     * @param resolver the resolver to add
     * @return this builder for chaining
     */
    public Builder add(PropertyResolver resolver) {
      Objects.requireNonNull(resolver, "resolver must not be null");
      resolvers.add(resolver);
      return this;
    }

    /**
     * Add multiple resolvers to the chain.
     *
     * @param resolvers the resolvers to add
     * @return this builder for chaining
     */
    public Builder addAll(List<PropertyResolver> resolvers) {
      Objects.requireNonNull(resolvers, "resolvers must not be null");
      this.resolvers.addAll(resolvers);
      return this;
    }

    /**
     * Build the composite resolver.
     *
     * @return the composite resolver with resolvers sorted by order
     */
    public CompositePropertyResolver build() {
      List<PropertyResolver> sorted = new ArrayList<>(resolvers);
      sorted.sort(Comparator.comparingInt(PropertyResolver::order));
      return new CompositePropertyResolver(sorted);
    }
  }
}
