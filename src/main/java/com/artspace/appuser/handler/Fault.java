package com.artspace.appuser.handler;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

/**
 * This class represents an Internal Server error that might occur during this application
 * lifecycle, due to a fault tolerance. This class will encapsulate any technical information that
 * must not be exposed to external clients.
 */
@Getter
@ToString
class Fault {

  /** Available fault reasons for the error. */
  @RequiredArgsConstructor
  private enum Reason {
    REJECTED(BulkheadException.class, "Operation Rejected"),
    TIMEOUT(TimeoutException.class, "Operation Timeout"),
    FAULT_TOLERANCE(FaultToleranceException.class, "Fault Tolerance");

    private final Class<? extends FaultToleranceException> clazz;
    private final String description;
  }

  Fault(final Reason input) {
    this.reason = input.name();
    this.message = input.description;
  }

  private final String reason;

  @Setter private String message = "";

  public static Fault byException(final FaultToleranceException e) {
    final var reason =
        Arrays.stream(Reason.values())
            .filter(r -> r.clazz.isInstance(e))
            .findFirst()
            .orElse(Reason.FAULT_TOLERANCE);

    return new Fault(reason);
  }
}
