package com.artspace.appuser.outgoing;

import io.smallrye.faulttolerance.api.CircuitBreakerName;
import io.smallrye.faulttolerance.api.FibonacciBackoff;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.i18n.ProviderLogging;
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecord;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.eclipse.microprofile.reactive.messaging.OnOverflow.Strategy;
import org.jboss.logging.Logger;

/**
 * Kafka emitter implementation of a {@link DataEmitter} that emits messages to notify changes over
 * the AppUsers service
 *
 * <p>The data emitted will be done via {@link AppUserDTO}, which includes only the necessary
 * information that must be exposed to the external world.
 *
 * <p>Due to being a core information for the entire application, fallback strategies are applied to
 * guarantee that no failed emission won't be left unprocessed and won't reach its destination.
 */
@ApplicationScoped
class AppUserKafkaEmitter implements DataEmitter<AppUserDTO> {

  @Getter(AccessLevel.PROTECTED)
  @ConfigProperty(name = "outgoing.correlation.key", defaultValue = "correlationId")
  String correlationKey;

  @Inject Logger logger;

  @Inject EmitFallbackService fallbackService;

  @Inject
  @Channel("appusers-out")
  @OnOverflow(value = Strategy.BUFFER, bufferSize = 1000)
  MutinyEmitter<AppUserDTO> emitter;

  /**
   * {@inheritDoc}
   *
   * <p>Not acknowledged messages will be stored with a fallback service, to retry it later on.
   *
   * @param correlationId identifier of the transaction that originated this necessity of emission
   * @param input data to be emitted
   * @throws IllegalArgumentException if correlationId is null or blank or input is null
   */
  @Timeout()
  @CircuitBreaker(
      requestVolumeThreshold = 10,
      delay = 500L,
      skipOn = {IllegalArgumentException.class})
  @Retry(
      maxRetries = 5,
      delay = 200,
      abortOn = {IllegalArgumentException.class})
  @FibonacciBackoff
  @Bulkhead()
  @Fallback(fallbackMethod = "registerLocally")
  @Override
  public void emit(final String correlationId, final AppUserDTO input) {
    final var corId =
        Optional.ofNullable(correlationId)
            .filter(s -> !s.isBlank())
            .orElseThrow(
                () -> new IllegalArgumentException("CorrelationId should not be blank nor null"));

    final var message =
        Optional.ofNullable(input)
            .map(user -> messageOf(user, corId))
            .orElseThrow(() -> new IllegalArgumentException("Emit AppUser can not be null"));

    emitter.send(message);
  }

  private Message<AppUserDTO> messageOf(final AppUserDTO appUserDTO, String correlationId) {
    final var message = Message.of(appUserDTO);
    return OutgoingKafkaRecord.from(message)
        .withHeader(correlationKey, correlationId.getBytes())
        .withAck(() -> handleAck(correlationId, appUserDTO))
        .withNack(throwable -> handleNack(correlationId, appUserDTO, throwable));
  }

  private CompletableFuture<Void> handleNack(
      String correlationId, final AppUserDTO appUserDTO, final Throwable throwable) {
    return CompletableFuture.runAsync(getFailureRunnable(correlationId, appUserDTO, throwable));
  }

  private CompletionStage<Void> handleAck(String correlationId, final AppUserDTO appUserDTO) {
    return CompletableFuture.runAsync(getCompletionRunnable(correlationId, appUserDTO));
  }

  private Runnable getCompletionRunnable(String correlationId, final AppUserDTO appUserDTO) {
    return () ->
        logger.infof("[%s] Sent %s was acknowledged by the broker", correlationId, appUserDTO);
  }

  private void registerLocally(final String correlationId, final AppUserDTO input) {
    fallbackService
        .registerFailure(correlationId, input)
        .subscribe()
        .with(x -> {}, ProviderLogging.log::failureEmittingMessage);
  }

  private Runnable getFailureRunnable(
      String correlationId, final AppUserDTO appUserDTO, final Throwable throwable) {
    return () -> {
      logger.errorf(
          "[%s] Sent message was NOT acknowledged by the broker. %s", correlationId, throwable);
      fallbackService
          .registerFailure(correlationId, appUserDTO)
          .subscribe()
          .with(x -> {}, ProviderLogging.log::failureEmittingMessage);
    };
  }
}
