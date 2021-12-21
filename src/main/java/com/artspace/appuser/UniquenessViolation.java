package com.artspace.appuser;

import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@Getter
@ToString
/** This class represents a violation of a rule that determined a data`s uniqueness. */
public final class UniquenessViolation {
  private final String field;
  private final String message;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UniquenessViolation that = (UniquenessViolation) o;
    return Objects.equals(field, that.field);
  }

  @Override
  public int hashCode() {
    return Objects.hash(field);
  }
}
