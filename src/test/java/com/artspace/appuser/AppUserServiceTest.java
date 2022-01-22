package com.artspace.appuser;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artspace.appuser.cache.CacheService;
import com.artspace.appuser.cache.CacheUserDTO;
import com.artspace.appuser.outgoing.AppUserDTO;
import com.artspace.appuser.outgoing.DataEmitter;
import com.github.javafaker.Faker;
import io.smallrye.mutiny.Uni;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.NoResultException;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

  @Mock private DataEmitter<AppUserDTO> emitter;
  @Mock private AppUserRepository appUserRepo;
  @Mock private CacheService cacheService;

  private final AppUserMapper appUserMapper = Mappers.getMapper(AppUserMapper.class);

  private AppUserService appUserService;

  private Faker faker;

  private static final Logger LOGGER = Logger.getLogger(AppUserService.class);

  private static final Duration ONE_SEC = Duration.ofSeconds(1);

  @BeforeEach
  void setUp() {
    this.faker = new Faker(Locale.ENGLISH);
    this.appUserService =
        new AppUserService(appUserRepo, emitter, cacheService, appUserMapper, LOGGER);
  }

  @ParameterizedTest
  @ValueSource(strings = " ")
  @NullAndEmptySource
  @DisplayName("An invalid username, such as {0}, should always resolved into an empty optional")
  void getByUserNameReturnsEmptyWithInvalidArgs(String value) {
    //given
    when(cacheService.find(any())).thenReturn(Uni.createFrom().item(Optional.empty()));

    // when
    var user = this.appUserService.getUserByUserName(value, getSampleCorrelationId());

    // then
    final var foundUser = user.await().atMost(ONE_SEC);
    assertThat(foundUser, is(Optional.empty()));
  }

  @Test
  @DisplayName("Empty should be resolved when no user is found by userName")
  void getByUserNameReturnsEmptyWhenNotFound() {
    // give
    when(cacheService.find(any())).thenReturn(Uni.createFrom().item(Optional.empty()));

    when(appUserRepo.findByUserName(Mockito.anyString())).thenReturn(Uni.createFrom().failure(
        NoResultException::new));

    // when
    var user = this.appUserService.getUserByUserName("jhondoe", getSampleCorrelationId());

    // then
    final var foundUser = user.await().atMost(ONE_SEC);
    assertThat(foundUser, is(Optional.empty()));
  }

  @Test
  @DisplayName("A user should be resolved when its found by username")
  void getByUserNameReturnsReturnsAnUser() {
    // given
    var user = this.provideSampleUser();

    when(cacheService.find(any())).thenReturn(Uni.createFrom().item(Optional.empty()));
    when(cacheService.persist(any(CacheUserDTO.class))).thenReturn(Uni.createFrom().item(true));
    when(appUserRepo.findByUserName(Mockito.anyString())).thenReturn(Uni.createFrom().item(user));

    // when
    var foundUser =
        this.appUserService.getUserByUserName(user.getUsername(), getSampleCorrelationId())
            .await().atMost(ONE_SEC).get();

    // then
    assertThat(foundUser.getUsername(), is(user.getUsername()));
    assertThat(foundUser.getFirstName(), is(user.getFirstName()));
    assertThat(foundUser.getId(), is(user.getId()));
    assertThat(foundUser.getEmail(), is(user.getEmail()));
    assertThat(foundUser.getBiography(), nullValue());
    assertTrue(foundUser.isActive());
  }

  @Test
  @DisplayName("A cached user should be returned when its found by username")
  void getByUserNameReturnsReturnsCachedUser() {
    // given
    var user = this.provideSampleUser();
    final var cache = appUserMapper.toCache(user);

    when(cacheService.find(any())).thenReturn(Uni.createFrom().item(Optional.of(cache)));

    // when
    var foundUser =
        this.appUserService.getUserByUserName(user.getUsername(), getSampleCorrelationId())
            .await().atMost(ONE_SEC).get();

    // then
    assertThat(foundUser.getUsername(), is(user.getUsername()));
    assertThat(foundUser.getFirstName(), is(user.getFirstName()));
    assertThat(foundUser.getId(), is(user.getId()));
    assertThat(foundUser.getEmail(), is(user.getEmail()));
    assertThat(foundUser.getBiography(), nullValue());
    assertTrue(foundUser.isActive());

    verify(appUserRepo, never()).findByUserName(anyString());
  }

  @Test
  @DisplayName("Empty optional should be resolved when no user is found by id")
  void getByIdReturnsEmptyWhenNotFound() {
    // give
    when(appUserRepo.findById(Mockito.anyLong())).thenReturn(Uni.createFrom().failure(
        NoResultException::new));
    // when
    var user = this.appUserService.getUserById(1L);

    // then
    final var foundUser = user.await().atMost(ONE_SEC);
    assertThat(foundUser, is(Optional.empty()));
  }

  @Test
  @DisplayName("Persisted User should be normalized")
  void persistUserShouldNormalizeUserData() {
    // given
    final var appUser = this.provideSampleUser();
    appUser.setUsername("  " + appUser.getUsername().toUpperCase() + "  ");
    appUser.setEmail("My.Email@AcMe.com");
    appUser.setActive(false);
    appUser.setCreationDate(Instant.now());

    when(cacheService.persist(any(CacheUserDTO.class))).thenReturn(Uni.createFrom().item(true));

    when(this.appUserRepo.findByUserNameOrEmail(anyString(), anyString()))
        .thenReturn(Uni.createFrom().item(Collections.emptyList()));

    when(this.appUserRepo.persist(any(AppUser.class)))
        .thenReturn(Uni.createFrom().item(appUser));

    var correlationId = getSampleCorrelationId();

    // when
    this.appUserService.persistAppUser(appUser, correlationId).await().atMost(ONE_SEC);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(AppUser.class);
    verify(this.appUserRepo).persist(argumentCaptor.capture());
    final var capturedUser = argumentCaptor.getValue();
    assertThat(capturedUser.getUsername(), is(appUser.getUsername().toLowerCase().trim()));
    assertThat(capturedUser.getEmail(), is(appUser.getEmail().toLowerCase().trim()));
    assertThat(capturedUser.getCreationDate(), notNullValue());
    assertTrue(capturedUser.isActive());
  }

  @Test
  @DisplayName("Persisted User should be cached")
  void persistUserShouldBeCached() {
    // given
    final var appUser = this.provideSampleUser();

    when(cacheService.persist(any(CacheUserDTO.class))).thenReturn(Uni.createFrom().item(true));

    when(this.appUserRepo.findByUserNameOrEmail(anyString(), anyString()))
        .thenReturn(Uni.createFrom().item(Collections.emptyList()));

    when(this.appUserRepo.persist(any(AppUser.class)))
        .thenReturn(Uni.createFrom().item(appUser));

    var correlationId = getSampleCorrelationId();

    // when
    this.appUserService.persistAppUser(appUser, correlationId).await().atMost(ONE_SEC);

    // then
    verify(this.cacheService,times(1)).persist(any(CacheUserDTO.class));
  }

  @RepeatedTest(value = 2, name = "A non-unique appUser should not be persisted")
  void persistUserShouldNotPersistNonUniqueUsers(RepetitionInfo repetitionInfo) {
    Assertions.assertThrows(
        UniquenessViolationException.class,
        () -> {
          // given
          final var appUser = this.provideSampleUser();
          when(this.appUserRepo.findByUserNameOrEmail(anyString(), anyString()))
              .thenReturn(Uni.createFrom().item(List.of(appUser)));

          var correlationId = getSampleCorrelationId();

          AppUser copy;
          if (repetitionInfo.getCurrentRepetition() == 1) {
            copy = appUser.withEmail("fake123@mail.com");
          } else {
            copy = appUser.withUsername(faker.funnyName().name());
          }

          // when
          this.appUserService.persistAppUser(copy, correlationId).await().atMost(ONE_SEC);
        });
  }

  @Test
  @DisplayName("Disable user will resolve into an empty optional if user is not found")
  void disableUserWillReturnEmptyIfUserNotFound() {
    // given
    when(this.appUserRepo.findByUserName(anyString())).thenReturn(Uni.createFrom().failure(
        NoResultException::new));

    var correlationId = getSampleCorrelationId();

    // then
    var output = this.appUserService.disableUser("johndoe", correlationId);

    // then
    final var disabledUser = output.await().atMost(ONE_SEC);
    assertThat(disabledUser, is(Optional.empty()));
  }

  @Test
  @DisplayName("Disable user should delete cached data")
  void disableUserShouldDeleteCachedData() {
    // given
    var correlationId = getSampleCorrelationId();
    var sampleUser = provideSampleUser();
    sampleUser.setUsername("johndoe");

    when(this.cacheService.remove(anyString())).thenReturn(Uni.createFrom().item(true));
    when(this.appUserRepo.findByUserName(anyString())).thenReturn(Uni.createFrom().item(sampleUser));

    // then
    this.appUserService.disableUser("johndoe", correlationId).await().atMost(ONE_SEC);

    // then
    verify(this.cacheService,times(1)).remove(eq("johndoe"));
  }

  @ParameterizedTest
  @DisplayName("Disable user will ignore if the username is invalid {}")
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  void disableUserWillIgnoreIfInputIsInvalid(String input) {
    //given
    var correlationId = getSampleCorrelationId();

    // then
    var output = this.appUserService.disableUser(input, correlationId);

    // then
    final var disabledUser = output.await().atMost(ONE_SEC);
    assertThat(disabledUser, is(Optional.empty()));
  }

  @Test
  @DisplayName("Update user should resolve into an empty optional if user is not found")
  void updateUserShouldReturnEmptyIfUserNotFound() {
    // given
    when(this.appUserRepo.findByUserName(anyString())).thenReturn(Uni.createFrom().failure(
        NoResultException::new));

    var correlationId = getSampleCorrelationId();

    // when
    var output = this.appUserService.updateAppUser(provideSampleUser(), correlationId);

    // then
    final var updatedUser = output.await().atMost(ONE_SEC);
    assertThat(updatedUser, is(Optional.empty()));
  }

  @Test
  @DisplayName("Update user should not change data that are considered constant")
  void updateUserShouldNotUpdateConstantData() {
    // given
    var repoUser = provideSampleUser();
    repoUser.setId(1000L);

    var fiveDaysAgo = Instant.now().minus(5, ChronoUnit.DAYS);
    repoUser.setCreationDate(fiveDaysAgo);

    when(cacheService.persist(any(CacheUserDTO.class))).thenReturn(Uni.createFrom().item(true));
    when(this.appUserRepo.findByUserName(anyString())).thenReturn(Uni.createFrom().item(repoUser));
    when(this.appUserRepo.findByUserNameOrEmail(anyString(), anyString()))
        .thenReturn(Uni.createFrom().item(List.of(repoUser)));

    final var sampleUser = repoUser.withFirstName("John");
    sampleUser.setLastName("McN'Cheese");
    sampleUser.setUsername("nonono");
    sampleUser.setEmail("john.mcc@acme.com");
    sampleUser.setBiography("Just an old man");
    sampleUser.toggleActive();
    sampleUser.toToday();
    sampleUser.setId(2000L);

    var correlationId = getSampleCorrelationId();

    // when
    var output = this.appUserService.updateAppUser(sampleUser, correlationId);

    // then
    final var outputUser = output.await().atMost(ONE_SEC).get();
    assertThat(outputUser.getCreationDate(), is(fiveDaysAgo));
    assertThat(outputUser.getId(), is(repoUser.getId()));
    assertThat(outputUser.getUsername(), is(repoUser.getUsername()));
    assertTrue(outputUser.isActive());

    assertThat(outputUser.getFirstName(), is(sampleUser.getFirstName()));
    assertThat(outputUser.getLastName(), is(sampleUser.getLastName()));
    assertThat(outputUser.getBiography(), is(sampleUser.getBiography()));
  }

  @Test
  @DisplayName("Update user should cache updated data")
  void updateUserShouldCacheItsResults() {
    // given
    var correlationId = getSampleCorrelationId();
    var repoUser = provideSampleUser();
    repoUser.setId(1000L);

    when(this.appUserRepo.findByUserNameOrEmail(anyString(), anyString())).thenReturn(Uni.createFrom().item(List.of(repoUser)));
    when(this.appUserRepo.findByUserName(anyString())).thenReturn(Uni.createFrom().item(repoUser));
    when(this.cacheService.persist(any(CacheUserDTO.class))).thenReturn(Uni.createFrom().item(true));

    var updateUser = repoUser.withLastName("NoName");

    // when
    var output = this.appUserService.updateAppUser(updateUser, correlationId)
        .await().atMost(ONE_SEC);

    // then
    verify(this.cacheService,times(1)).persist(any(CacheUserDTO.class));
  }

  private AppUser provideSampleUser() {
    var user = new AppUser();
    user.setFirstName(this.faker.name().firstName());
    String username = this.faker.name().username();
    user.setUsername(username);
    user.setEmail(username + "@acme.com");
    user.setId(1000L);
    return user;
  }

  private String getSampleCorrelationId() {
    return UUID.randomUUID().toString();
  }
}
