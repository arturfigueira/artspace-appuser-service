package com.artspace.appuser.outgoing;

/**
 * An emitter of data to external services/applications.
 * @param <T> The type of the data being emitted.
 */
public interface DataEmitter<T> {

  /**
   * Emit given input data, with a correlation identifier to an external service
   * @param correlationId identifier of the transaction that originated this necessity of emission
   * @param input data to be emitted
   */
  void emit(final String correlationId, final T input);

}
