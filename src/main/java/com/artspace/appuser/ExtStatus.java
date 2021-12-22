package com.artspace.appuser;

import javax.ws.rs.core.Response;
import lombok.Getter;
import lombok.ToString;

/**
 * Extension of javax {@link javax.ws.rs.core.Response.Status} adding other available status codes
 *
 * <p>Refer to <a * href="https://httpstatuses.com">httpstatuses</a> for more details regarding all
 * http status available
 */
@Getter
@ToString
public enum ExtStatus implements Response.StatusType {
  UNPROCESSABLE_ENTITY(422, "OK");

  private final int statusCode;
  private final String reasonPhrase;
  private final Response.Status.Family family;

  ExtStatus(int statusCode, String reasonPhrase) {
    this.statusCode = statusCode;
    this.reasonPhrase = reasonPhrase;
    this.family = Response.Status.Family.familyOf(statusCode);
  }
}
