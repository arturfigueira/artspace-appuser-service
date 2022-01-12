package com.artspace.appuser.outgoing;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Make {@link JsonProcessingException} available to be instantiated by our tests
 */
class MockedProcessingException extends JsonProcessingException {

  public MockedProcessingException(String msg, JsonLocation loc,
      Throwable rootCause) {
    super(msg, loc, rootCause);
  }

  public MockedProcessingException(String msg) {
    super(msg);
  }

  public MockedProcessingException(String msg, JsonLocation loc) {
    super(msg, loc);
  }

  public MockedProcessingException(String msg, Throwable rootCause) {
    super(msg, rootCause);
  }

  public MockedProcessingException(Throwable rootCause) {
    super(rootCause);
  }
}
