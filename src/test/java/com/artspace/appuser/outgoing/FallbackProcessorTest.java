package com.artspace.appuser.outgoing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artspace.appuser.AppUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import io.smallrye.mutiny.Uni;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FallbackProcessorTest {

  static final Faker FAKER = new Faker(Locale.ENGLISH);
  static final Logger LOGGER = Logger.getLogger(FallbackProcessorTest.class);
  static final ObjectMapper MAPPER = new ObjectMapper();

  EmitFallbackService fallbackService;

  AppUserService userService;

  DataEmitter<AppUserDTO> emitter;

  ObjectMapper mockedMapper;

  FallbackProcessor fallbackProcessor;

  @BeforeEach
  void setup() {
    fallbackService = mock(EmitFallbackService.class);
    userService = mock(AppUserService.class);
    emitter = mock(DataEmitter.class);
    mockedMapper = mock(ObjectMapper.class);

    fallbackProcessor = new FallbackProcessor();
    fallbackProcessor.logger = LOGGER;
    fallbackProcessor.fallbackService = fallbackService;
    fallbackProcessor.objectMapper = mockedMapper;
    fallbackProcessor.emitter = emitter;
    fallbackProcessor.userService = userService;
    fallbackProcessor.maxMessagesPerRun = 1;
  }

  @Test
  @DisplayName("processNextFailure should deal when no failed messages are found")
  void processFailedMessagesShouldNotBreakIfThereAreNoMessages() {
    // given
    when(fallbackService.processNextFailure()).thenReturn(Uni.createFrom().item(Optional.empty()));

    // then
    Assertions.assertDoesNotThrow(() -> this.fallbackProcessor.processFailedMessages());
    verify(emitter, never()).emit(anyString(), any(AppUserDTO.class));
  }

  @Test
  @DisplayName("processNextFailure with zero messages per run should be disabled")
  void processNextFailureShouldBeDisabledViaConfig() {
    // given
    fallbackProcessor.maxMessagesPerRun = 0;

    when(fallbackService.processNextFailure()).thenReturn(Uni.createFrom().item(Optional.empty()));

    // then
    Assertions.assertDoesNotThrow(() -> this.fallbackProcessor.processFailedMessages());
    verify(emitter, never()).emit(anyString(), any(AppUserDTO.class));
  }

  @Test
  @DisplayName("processNextFailure should swallow parsing exceptions")
  void processNextFailureShouldCatchAllExceptions() throws JsonProcessingException {
    // given
    when(fallbackService.processNextFailure())
        .thenReturn(Uni.createFrom().item(Optional.of(provideSampleMessage())));

    when(mockedMapper.readValue(anyString(), eq(AppUserDTO.class)))
        .thenThrow(new MockedProcessingException("Error"));

    // then
    Assertions.assertDoesNotThrow(() -> this.fallbackProcessor.processFailedMessages());
  }

  @Test
  @DisplayName("processNextFailure should ignore messages from vanished AppUsers")
  void processNextFailureShouldIgnoreMessagesFromVanishedUsers() throws JsonProcessingException {
    // given
    fallbackProcessor.objectMapper = MAPPER;

    final var failedMessage = provideSampleMessage();
    when(fallbackService.processNextFailure())
        .thenReturn(Uni.createFrom().item(Optional.of(failedMessage)));

    when(userService.getUserByUserName(anyString(), anyString()))
        .thenReturn(Uni.createFrom().item(Optional.empty()));

    // when
    this.fallbackProcessor.processFailedMessages();

    // then
    verify(emitter, never()).emit(anyString(), any(AppUserDTO.class));
  }

  @Test
  @DisplayName("processNextFailure should ignore obsolete messages")
  void processNextFailureShouldIgnoreMessagesFromObsoleteMessages() throws JsonProcessingException {
    // given
    fallbackProcessor.objectMapper = MAPPER;

    final var failedMessage = provideSampleMessage();
    when(fallbackService.processNextFailure())
        .thenReturn(Uni.createFrom().item(Optional.of(failedMessage)));

    final var user = FakeUser.from(provideSampleUser());
    when(userService.getUserByUserName(anyString(), anyString()))
        .thenReturn(Uni.createFrom().item(Optional.of(user)));

    // when
    this.fallbackProcessor.processFailedMessages();

    // then
    verify(emitter, never()).emit(anyString(), any(AppUserDTO.class));
  }

  @Test
  @DisplayName("processNextFailure should reemit relevant messages")
  void processNextFailureShouldReEmitRelevantMessages() throws JsonProcessingException {
    // given
    fallbackProcessor.objectMapper = MAPPER;

    final var sampleUser = provideSampleUser();
    final var failedMessage = provideSampleMessage(sampleUser);
    when(fallbackService.processNextFailure())
        .thenReturn(Uni.createFrom().item(Optional.of(failedMessage)));

    when(userService.getUserByUserName(anyString(), anyString()))
        .thenReturn(Uni.createFrom().item(Optional.of(FakeUser.from(sampleUser))));

    // when
    this.fallbackProcessor.processFailedMessages();

    // then
    verify(emitter, times(1)).emit(eq(failedMessage.getCorrelationId()), eq(sampleUser));
  }

  private FailedMessage provideSampleMessage() throws JsonProcessingException {
    return provideSampleMessage(provideSampleUser());
  }

  private FailedMessage provideSampleMessage(final AppUserDTO userDTO)
      throws JsonProcessingException {
    return FailedMessage.builder()
        .serializedPayload(MAPPER.writeValueAsString(userDTO))
        .correlationId(UUID.randomUUID().toString())
        .id(1000L)
        .reason("Mocked Error")
        .build();
  }

  private AppUserDTO provideSampleUser() {
    var user = new AppUserDTO();
    final var name = FAKER.name();
    user.setFirstName(name.firstName());
    user.setUsername(name.username());
    user.setLastName(name.lastName());
    user.setActive(true);
    return user;
  }
}
