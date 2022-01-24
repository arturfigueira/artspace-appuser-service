package com.artspace.appuser.outgoing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.common.runtime.ReactiveTransactional;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import io.smallrye.faulttolerance.api.FibonacciBackoff;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import lombok.AccessLevel;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
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

  public Uni<Void> registerFailure(final String correlationId, final AppUserDTO input) {
    var result =
        Uni.createFrom()
            .voidItem()
            .invoke(() -> logger.warn("Register of failed messages are disabled. Ignoring"));

    if (enabledRegistering) {
      result =
          this.innerRegister(correlationId, input)
              .onFailure()
              .invoke(e -> logger.error("Unable to register failed message", e));
    }
    return result;
  }

  @Timeout()
  @CircuitBreaker(
      requestVolumeThreshold = 10,
      failureRatio = 0.75,
      delay = 1000L,
      skipOn = {IllegalArgumentException.class, JsonProcessingException.class})
  @CircuitBreakerName("message-failure-register")
  @Retry(maxRetries = 5, delay = 200, abortOn = {IllegalArgumentException.class, JsonProcessingException.class})
  @FibonacciBackoff
  protected Uni<Void> innerRegister(final String correlationId, final AppUserDTO input) {
    try {
      final var corid = Optional.ofNullable(correlationId)
          .filter(s->!s.isBlank())
          .orElseThrow(() -> new IllegalArgumentException("Invalid correlationId"));

      final var appUser = Optional.ofNullable(input)
          .orElseThrow(() -> new IllegalArgumentException("Invalid input user"));

      final var valueAsString = this.objectMapper.writeValueAsString(appUser);
      final var failedMessage =
          FailedMessage.builder()
              .correlationId(corid)
              .serializedPayload(valueAsString)
              .build();

      return this.failureRepository
          .persist(failedMessage)
          .invoke(msg -> logger.debugf("Message registered for further analysis. %s", msg))
          .replaceWithVoid();

    } catch (Exception e) {
      logger.error("Unable to register failed message", e);
      return Uni.createFrom().failure(e);
    }
  }
}
