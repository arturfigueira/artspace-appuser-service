package com.artspace.appuser.outgoing;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.i18n.ProviderLogging;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.reactive.messaging.Message;

@RequiredArgsConstructor
class FailureEmitter<T> implements MutinyEmitter<T> {

  private final RuntimeException error;

  @Override
  public Uni<Void> send(T t) {
    return Uni.createFrom().failure(error);
  }

  @Override
  public void sendAndAwait(T t) {
  }

  @Override
  public Cancellable sendAndForget(T t) {
    return null;
  }

  @Override
  public <M extends Message<? extends T>> void send(M m) {
    Uni.createFrom().emitter(e -> emit(m)).subscribe().with(x -> {
    }, ProviderLogging.log::failureEmittingMessage);
  }

  private <M extends Message<? extends T>> void emit(M m) {
    m.nack(error);
    throw error;
  }

  @Override
  public void complete() {
  }

  @Override
  public void error(Exception e) {
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean hasRequests() {
    return false;
  }
}
