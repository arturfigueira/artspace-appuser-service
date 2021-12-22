package com.artspace.appuser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Uniqueness Violation Exception is a superclass related to all failures to comply to a uniqueness
 * rule, during the application runtime. This is an <em>unchecked exceptions</em> and it's a
 * subclass of {@link RuntimeException}
 *
 * <p>The exception will contain the list of {@link UniquenessViolation}. It also contains a default
 * message {@value DEFAULT_MESSAGE}
 *
 * @see RuntimeException
 */
public class UniquenessViolationException extends RuntimeException {
  private final Set<UniquenessViolation> violationSet = new HashSet<>();

  private static final String DEFAULT_MESSAGE = "Uniqueness Constraint Violation";

  /**
   * Create a new instance of UniquenessViolationException with an empty list of violations
   */
  public UniquenessViolationException() {
    super(DEFAULT_MESSAGE);
  }

  /**
   * Create a new instance of UniquenessViolationException with a list of violations
   * @param violations
   */
  public UniquenessViolationException(Set<UniquenessViolation> violations) {
    this();
    this.violationSet.addAll(violations);
  }

  public List<UniquenessViolation> violations() {
    return new ArrayList<>(violationSet);
  }
}
