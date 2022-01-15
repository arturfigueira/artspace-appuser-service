package com.artspace.appuser.outgoing;

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
   * <p>Not acknowledged messages will be stored with a fallback service, to
   * retry it later on.
   *
   * @param correlationId identifier of the transaction that originated this necessity of emission
   * @param input data to be emitted
   * @throws IllegalArgumentException if correlationId is null or blank or input is null
   */
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

    try {
      emitter.send(message);
    } catch (IllegalStateException e) {
      handleFailure(input, e, corId);
    }
  }

  private Message<AppUserDTO> messageOf(final AppUserDTO appUserDTO, String correlationId) {
    final var message = Message.of(appUserDTO);
    return OutgoingKafkaRecord.from(message)
        .withHeader(correlationKey, correlationId.getBytes())
        .withAck(() -> handleAck(correlationId, appUserDTO))
        .withNack(throwable -> handleNack(message, throwable));
  }

  private void handleFailure(final AppUserDTO input, final Throwable e, String correlationId) {
    logger.errorf("[%] Failed to send %s to the broker. %s", correlationId, input, e);
  }

  private CompletableFuture<Void> handleNack(
      final Message<AppUserDTO> message, final Throwable throwable) {
    return CompletableFuture.runAsync(getFailureRunnable(message, throwable));
  }

  private CompletionStage<Void> handleAck(String correlationId, final AppUserDTO appUserDTO) {
    return CompletableFuture.runAsync(getCompletionRunnable(correlationId, appUserDTO));
  }

  private Runnable getCompletionRunnable(String correlationId, final AppUserDTO appUserDTO) {
    return () ->
        logger.infof("[%s] Sent %s was acknowledged by the broker", correlationId, appUserDTO);
  }

  private Runnable getFailureRunnable(
      final Message<AppUserDTO> message, final Throwable throwable) {
    return () -> {
      logger.errorf("Sent message was NOT acknowledged by the broker. %s", throwable);
      fallbackService
          .registerFailure(message, throwable)
          .subscribe()
          .with(x -> {}, ProviderLogging.log::failureEmittingMessage);
    };
  }
}
