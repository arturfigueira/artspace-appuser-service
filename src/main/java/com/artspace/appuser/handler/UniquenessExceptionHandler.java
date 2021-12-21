package com.artspace.appuser.handler;

import com.artspace.appuser.ExtStatus;
import com.artspace.appuser.UniquenessViolation;
import com.artspace.appuser.UniquenessViolationException;
import java.util.List;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jboss.logging.Logger;

@Provider
@AllArgsConstructor
public class UniquenessExceptionHandler implements ExceptionMapper<UniquenessViolationException> {

  Logger logger;

  @AllArgsConstructor
  @Data
  static class Wrapper {
    String title;
    List<UniquenessViolation> violations;
  }

  @Override
  public Response toResponse(UniquenessViolationException e) {
    logger.error(e);
    return Response.status(ExtStatus.UNPROCESSABLE_ENTITY)
        .entity(new Wrapper(e.getMessage(), e.violations()))
        .build();
  }
}
