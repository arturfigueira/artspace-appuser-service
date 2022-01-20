package com.artspace.appuser.outgoing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.javafaker.Faker;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.hamcrest.core.Is;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppUserKafkaEmitterTest {

  static Faker FAKER;

  AppUserKafkaEmitter appUserKafkaEmitter;

  @Mock MutinyEmitter<AppUserDTO> mutinyEmitter;

  @Mock EmitFallbackService fallbackService;

  @Captor ArgumentCaptor<Message<AppUserDTO>> messageCaptor;

  @BeforeAll
  static void setup() {
    FAKER = new Faker(Locale.ENGLISH);
  }

  @BeforeEach
  void beforeEach() {
    appUserKafkaEmitter = new AppUserKafkaEmitter();
    appUserKafkaEmitter.correlationKey = "correlationId";
    appUserKafkaEmitter.logger = Logger.getLogger(AppUserKafkaEmitter.class);
    appUserKafkaEmitter.emitter = mutinyEmitter;
    appUserKafkaEmitter.fallbackService = fallbackService;
  }

  @Test
  @DisplayName("Emit should throw when no AppUser argument is provided")
  void emitShouldThrowWhenAppUserIsNull() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          this.appUserKafkaEmitter.emit(UUID.randomUUID().toString(), null);
        });
  }

  @ParameterizedTest
  @EmptySource
  @NullSource
  @ValueSource(strings = {"     "})
  @DisplayName("Emit should throw when no CorrelationId is provided")
  void emitShouldThrowWhenCorrelationIsInvalid(String correlationId) {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          this.appUserKafkaEmitter.emit(correlationId, new AppUserDTO());
        });
  }

  @Test
  @DisplayName("Emit should send Message following required specs")
  void emitShouldProperlyEmitMessageToBroker() {
    // given
    final var appUserDTO = generateSampleUser();

    // when
    appUserKafkaEmitter.emit(UUID.randomUUID().toString(), appUserDTO);

    // then
    verify(mutinyEmitter).send(messageCaptor.capture());
    final var message = messageCaptor.getValue();
    var hasCorrelationId =
        message
            .getMetadata(OutgoingKafkaRecordMetadata.class)
            .get()
            .getHeaders()
            .headers(appUserKafkaEmitter.getCorrelationKey())
            .iterator()
            .hasNext();

    assertTrue(hasCorrelationId);
    assertThat(message.getPayload(), Is.is(appUserDTO));
  }

  @Test
  @DisplayName("Emit should handle failures for later processing")
  @SuppressWarnings("unchecked")
  void emitShouldHandleFailuresForLater() throws ExecutionException, InterruptedException {
    // given
    final var appUserDTO = generateSampleUser();

    when(fallbackService.registerFailure(any(Message.class), any(Throwable.class)))
        .thenReturn(Uni.createFrom().voidItem());

    final var messageNackRunnable = new AtomicReference<CompletionStage<Void>>(null);
    doAnswer(
            invocation -> {
              Message<?> message = invocation.getArgument(0);
              messageNackRunnable.set(message.nack(new IllegalStateException()));
              return null;
            })
        .when(mutinyEmitter)
        .send(any(Message.class));

    // when
    appUserKafkaEmitter.emit(UUID.randomUUID().toString(), appUserDTO);

    // then
    giveBreathForTheThreadToFinish();
    verify(fallbackService, times(1))
        .registerFailure(any(Message.class), any(IllegalStateException.class));
  }

  /**
   * It was identified a bug with quarkus' and {@link ServiceConfigurationError}, which prevented
   * the usage of a elegant saluting via {@code
   * messageNackRunnable.get().toCompletableFuture().get()} to wait for the nack runnable to finish,
   * before validating the result of the test. Until this is fixed a thread sleep was introduced.
   *
   * <p>Refer to <A href="https://github.com/quarkusio/quarkus/issues/19494">Quarkus Issue 19494</A>
   */
  private void giveBreathForTheThreadToFinish() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static AppUserDTO generateSampleUser() {
    var appUser = new AppUserDTO();
    appUser.setUsername(FAKER.name().username());
    appUser.setFirstName(FAKER.name().firstName());
    appUser.setLastName(FAKER.name().lastName());
    appUser.setActive(true);
    return appUser;
  }
}
