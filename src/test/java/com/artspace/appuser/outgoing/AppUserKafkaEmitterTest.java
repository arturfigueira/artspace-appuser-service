package com.artspace.appuser.outgoing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.github.javafaker.Faker;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import java.util.Locale;
import java.util.UUID;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.hamcrest.core.Is;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;


@QuarkusTest
@QuarkusTestResource(KafkaTestResourceLifecycleManager.class)
class AppUserKafkaEmitterTest {

  static Faker FAKER;

  @Inject AppUserKafkaEmitter appUserKafkaEmitter;

  @Inject @Any InMemoryConnector connector;

  @BeforeAll
  static void setup() {
    FAKER = new Faker(Locale.ENGLISH);
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
    final var appUserOutChannel = connector.sink("appusers-out");

    final var appUserDTO = generateSampleUser();

    // when
    appUserKafkaEmitter.emit(UUID.randomUUID().toString(), appUserDTO);

    // then
    assertThat(appUserOutChannel.received().size(), Is.is(1));

    final var message = appUserOutChannel.received().get(0);
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
  void emitShouldHandleFailuresForLater() {
    // given
    final MutinyEmitter mutinyEmitter = mock(MutinyEmitter.class);
    var kafkaEmitter = new AppUserKafkaEmitter();
    kafkaEmitter.emitter = mutinyEmitter;
    kafkaEmitter.logger = Logger.getLogger(this.getClass());

    final var fallbackService = mock(EmitFallbackService.class);
    kafkaEmitter.fallbackService = fallbackService;
    kafkaEmitter.correlationKey = "correlationId";

    final var appUserDTO = generateSampleUser();

    doAnswer(
            invocation -> {
              Message<?> message = invocation.getArgument(0);
              message.nack(new IllegalStateException());
              return null;
            })
        .when(mutinyEmitter)
        .send(any(Message.class));

    // when
    kafkaEmitter.emit(UUID.randomUUID().toString(), appUserDTO);

    // then
    verify(fallbackService, times(1))
        .registerFailure(any(Message.class), any(IllegalStateException.class));
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
