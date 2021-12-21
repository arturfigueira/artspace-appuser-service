package com.artspace.appuser.handler;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import lombok.AllArgsConstructor;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;
import org.jboss.logging.Logger;

/**
 * Handle {@link FaultToleranceException} that may occur while serving request from external clients
 * This class has the responsibility to handle and not let it be exposed untreated to the requester.
 */
@Provider
@AllArgsConstructor
class FaultToleranceExceptionHandler implements ExceptionMapper<FaultToleranceException> {

  Logger logger;

  @Override
  public Response toResponse(FaultToleranceException e) {
    var fault = Fault.byException(e);
    logger.error(fault.getMessage(), e);
    return Response.serverError().entity(fault).build();
  }
}
