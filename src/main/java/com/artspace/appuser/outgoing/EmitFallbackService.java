package com.artspace.appuser.outgoing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.common.runtime.ReactiveTransactional;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import io.smallrye.faulttolerance.api.FibonacciBackoff;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

@ReactiveTransactional
@ApplicationScoped
class EmitFallbackService {

  @Getter(AccessLevel.PROTECTED)
  @ConfigProperty(name = "outgoing.ev.correlation.key", defaultValue = "correlationId")
  String correlationKey;

  @ConfigProperty(name = "outgoing.ev.fallback.enabled", defaultValue = "true")
  boolean enabledRegistering;

  @Inject FailureRepository failureRepository;

  @Inject Logger logger;

  @Inject ObjectMapper objectMapper;

  @Transactional(TxType.SUPPORTS)
  Uni<List<FailedMessage>> listAll() {
    return this.failureRepository.findAll().list();
  }

  @Transactional(TxType.SUPPORTS)
  Uni<Optional<FailedMessage>> findById(final long id) {
    return this.failureRepository
        .findById(id)
        .map(Optional::of)
        .onFailure(NoResultException.class)
        .recoverWithItem(Optional.empty());
  }

  /**
   * Retrieve an unprocessed {@link FailedMessage}, from the failed messages' repository. Messages
   * returned by this method will be automatically set as processed. If no messages are found an
   * {@code Optional.empty()} will be returned as result.
   *
   * @return a {@link Uni} that might resolve into an unprocessed {@link FailedMessage} or empty if
   *     no failed messages is available
   * @throws TimeoutException if this method reaches its maximum processing time
   */
  @Timeout(500)
  @CircuitBreaker(requestVolumeThreshold = 6, failureRatio = 0.75, delay = 2000L)
  @CircuitBreakerName("message-failure-next")
  @Retry(maxRetries = 5, delay = 200)
  @FibonacciBackoff
  Uni<Optional<FailedMessage>> processNextFailure() {
    try {
      return this.failureRepository
          .find("isprocessed", false)
          .firstResult()
          .map(Optional::ofNullable)
          .invoke(opt -> opt.ifPresent(FailedMessage::markAsProcessed));
    } catch (Exception e) {
      logger.error("Unable to process next failed message", e);
      return Uni.createFrom().failure(e);
    }
  }

  /**
   * Register a new failure message for given message and error
   * @param message Original message that was not acknowledged
   * @param error Reason for not being acknowledged
   * @return {@code Uni} void
   * @throws TimeoutException if this method reaches its maximum processing time
   */
  public Uni<Void> registerFailure(final Message<AppUserDTO> message, final Throwable error) {
    var result =
        Uni.createFrom()
            .voidItem()
            .invoke(() -> logger.warn("Register of failed messages are disabled. Ignoring"));

    if (enabledRegistering) {
      result =
          this.innerRegister(message, error)
              .onFailure()
              .invoke(e -> logger.error("Unable to register failed message", e));
    }
    return result;
  }


  @Timeout(value = 500)
  @CircuitBreaker(requestVolumeThreshold = 6, failureRatio = 0.75, delay = 2000L)
  @CircuitBreakerName("message-failure-register")
  @Retry(maxRetries = 5, delay = 200)
  @FibonacciBackoff
  protected Uni<Void> innerRegister(final Message<AppUserDTO> message, final Throwable error) {
    try {
      if (message == null) {
        logger.warn("No message provided to be registered as failure. Ignoring");
        return Uni.createFrom().voidItem();
      }

      var correlationId = this.extractCorrelation(message);
      final var valueAsString = this.objectMapper.writeValueAsString(message.getPayload());
      final var failedMessage =
          FailedMessage.builder()
              .correlationId(correlationId)
              .serializedPayload(valueAsString)
              .build();

      Optional.ofNullable(error).map(Throwable::getMessage).ifPresent(failedMessage::setReason);

      return this.failureRepository
          .persist(failedMessage)
          .invoke(msg -> logger.debugf("Message registered for further analysis. %s", msg))
          .replaceWithVoid();
    } catch (Exception e) {
      logger.error("Unable to register failed message", e);
      return Uni.createFrom().failure(e);
    }
  }

  private String extractCorrelation(Message<AppUserDTO> message) {
    final var kafkaRecordMetadata = message.getMetadata(OutgoingKafkaRecordMetadata.class);

    final var headerIterator =
        kafkaRecordMetadata.map(
            metadata -> metadata.getHeaders().headers(this.correlationKey).iterator());

    return headerIterator.isPresent() && headerIterator.get().hasNext()
        ? extractHeaderValue(headerIterator.get())
        : null;
  }

  private String extractHeaderValue(Iterator<Header> headerIterator) {
    return new String(headerIterator.next().value());
  }
}
