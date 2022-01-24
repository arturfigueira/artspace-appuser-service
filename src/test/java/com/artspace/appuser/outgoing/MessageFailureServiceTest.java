package com.artspace.appuser.outgoing;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import javax.inject.Inject;
import org.hamcrest.core.Is;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessageFailureServiceTest {

  static final Duration ONE_SECOND = Duration.ofSeconds(1);
  static final Faker FAKER = new Faker(Locale.ENGLISH);

  @Inject Logger logger;

  @Inject
  EmitFallbackService emitFallbackService;

  @Inject ObjectMapper objectMapper;

  @Inject FailureRepository failureRepository;

  @AfterEach
  void cleanup() {
    failureRepository
        .deleteAll()
        .onFailure()
        .invoke(throwable -> logger.error(throwable))
        .await()
        .atMost(Duration.ofMinutes(1L));
  }

  @Order(1)
  @Test
  @DisplayName("Service should persist any failed message")
  void registerFailureShouldPersistIntoRepository() throws JsonProcessingException {
    // given
    final var appUserDTO = provideSampleUser();
    final var correlationId = provideCorrelationId();

    // when
    this.emitFallbackService
        .registerFailure(correlationId, appUserDTO)
        .await()
        .atMost(ONE_SECOND);

    // then
    final var failedMessages = this.emitFallbackService.listAll().await().atMost(ONE_SECOND);

    assertThat(failedMessages.size(), Is.is(1));

    final var failedMessage = failedMessages.get(0);
    assertThat(failedMessage.getReason(), nullValue());
    assertThat(failedMessage.getFailedTime(), notNullValue());
    assertThat(failedMessage.getId(), notNullValue());

    final var received =
        objectMapper.readValue(failedMessage.getSerializedPayload(), AppUserDTO.class);

    assertThat(received, Is.is(appUserDTO));
  }

  @Order(2)
  @Test
  @DisplayName("Process Next Failure should set failure as processed")
  void processNextFailureShouldSetAsProcessed() {
    // given
    for (var index = 0; index <= 5; index++) {
      final var appUserDTO = provideSampleUser();
      this.emitFallbackService.registerFailure(provideCorrelationId(), appUserDTO)
          .await().atMost(ONE_SECOND);
    }

    // when
    final var failedMessage =
        this.emitFallbackService.processNextFailure().await().atMost(ONE_SECOND);

    // then
    assertTrue(failedMessage.isPresent());

    final var message = failedMessage.get();
    assertTrue(message.isProcessed());

    final var updatedMessage =
        this.emitFallbackService.findById(message.getId()).await().atMost(ONE_SECOND);

    assertTrue(updatedMessage.get().isProcessed());
  }

  private String provideCorrelationId() {
    return UUID.randomUUID().toString();
  }

  private AppUserDTO provideSampleUser() {
    var user = new AppUserDTO();
    final var name = FAKER.name();
    user.setFirstName(name.firstName());
    user.setUsername(name.username());
    return user;
  }
}
