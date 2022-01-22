package com.artspace.appuser.outgoing;

import com.artspace.appuser.AppUser;
import com.artspace.appuser.AppUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Service that provides scheduled methods to process previously emitted messages that were not
 * acknowledged by the message broker
 */
@ApplicationScoped
class FallbackProcessor {

  private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

  @ConfigProperty(name = "outgoing.ev.fallback.job.items.per-run", defaultValue = "30")
  long maxMessagesPerRun;

  @Inject EmitFallbackService fallbackService;

  @Inject AppUserService userService;

  @Inject DataEmitter<AppUserDTO> emitter;

  @Inject ObjectMapper objectMapper;

  @Inject Logger logger;

  /**
   * Retrieves failed {@code AppUserDTO} messages that were stored to further processing, due to
   * acknowledge failure. The processing will be done in batches that are configured by {@code
   * outgoing.ev.fallback.job.frequency}. If set to zero no messages will be processed.
   *
   * <p>Only messages that contain up-to-date user data will be processed. User data that are
   * obsolete will make the message to be discarded.
   */
  @Scheduled(cron = "{outgoing.ev.fallback.job.frequency}")
  void processFailedMessages() {
    var failedMessage = this.getNexMessageToProcess();
    var messagesProcessed = 0;
    while (failedMessage.isPresent() && messagesProcessed < maxMessagesPerRun) {
      this.processSingleMessage(failedMessage.get());
      messagesProcessed++;
      failedMessage = this.getNexMessageToProcess();
    }
  }

  private Optional<FailedMessage> getNexMessageToProcess() {
    return fallbackService.processNextFailure().await().atMost(TEN_SECONDS);
  }

  private void processSingleMessage(final FailedMessage failedMessage) {
    try {
      final var correlationId = failedMessage.getCorrelationId();
      final var appUserDTO =
          objectMapper.readValue(failedMessage.getSerializedPayload(), AppUserDTO.class);

      userService
          .getUserByUserName(appUserDTO.getUsername(), correlationId)
          .map(userOptional -> shouldReEmit(appUserDTO, userOptional))
          .chain(chainEmission(correlationId, appUserDTO))
          .subscribe()
          .with(unused -> {});

    } catch (JsonProcessingException e) {
      logger.errorf("Unable to serialize failed message", e);
    }
  }

  private Function<Boolean, Uni<? extends Void>> chainEmission(
      String correlationId, AppUserDTO appUserDTO) {
    return shouldReEmit -> {
      if (shouldReEmit) {
        doEmit(correlationId, appUserDTO);
      } else {
        ignoreEmission(correlationId, appUserDTO);
      }
      return Uni.createFrom().voidItem();
    };
  }

  private void doEmit(String correlationId, final AppUserDTO appUserDTO) {
    logger.debugf(
        "[%s]Re-emitting failed message. Data: %s", correlationId, appUserDTO);
    emitter.emit(correlationId, appUserDTO);
  }

  private void ignoreEmission(String correlationId, final AppUserDTO appUserDTO) {
    logger.warnf(
        "[%s]Re-emitting was ignored due to data being obsolete. Data: %s",
        correlationId, appUserDTO);
  }

  private Boolean shouldReEmit(final AppUserDTO appUserDTO, final Optional<AppUser> userOptional) {
    return userOptional
        .map(currentUser -> messageDataIsUpToDate(currentUser, appUserDTO))
        .orElse(false);
  }

  private boolean messageDataIsUpToDate(final AppUser currentState, final AppUserDTO updateState) {
    return currentState.isActive() == updateState.isActive()
        && currentState.getFirstName().equals(updateState.getFirstName())
        && currentState.getLastName().equals(updateState.getLastName());
  }
}
