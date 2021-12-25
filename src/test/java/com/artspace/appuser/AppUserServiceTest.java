package com.artspace.appuser;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.javafaker.Faker;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

  @Mock private AppUserRepository appUserRepo;

  private AppUserService appUserService;

  private Faker faker;

  private static final Logger LOGGER = Logger.getLogger(AppUserService.class);

  @BeforeEach
  void setUp() {
    this.faker = new Faker(Locale.ENGLISH);
    this.appUserService = new AppUserService(appUserRepo, LOGGER);
  }

  @ParameterizedTest
  @ValueSource(strings = " ")
  @NullAndEmptySource
  @DisplayName("An invalid username, such as {0}, should always return an empty optional")
  void getByUserNameReturnsEmptyWithInvalidArgs(String value) {
    // when
    var user = this.appUserService.getUserByUserName(value);

    // then
    assertThat(user, is(Optional.empty()));
  }

  @Test
  @DisplayName("Empty should be returned when no user is found by userName")
  void getByUserNameReturnsEmptyWhenNotFound() {
    // give
    when(appUserRepo.findByUserName(Mockito.anyString())).thenReturn(Optional.empty());

    // when
    var user = this.appUserService.getUserByUserName("jhondoe");

    // then
    assertThat(user, is(Optional.empty()));
  }

  @Test
  @DisplayName("A user should be returned when its found by username")
  void getByUserNameReturnsReturnsAnUser() {
    // give
    var user = this.provideSampleUser();

    when(appUserRepo.findByUserName(Mockito.anyString())).thenReturn(Optional.of(user));

    // when
    var foundUser = this.appUserService.getUserByUserName(user.getUsername()).get();

    // then
    assertThat(foundUser.getUsername(), is(user.getUsername()));
    assertThat(foundUser.getFirstName(), is(user.getFirstName()));
    assertThat(foundUser.getId(), is(user.getId()));
    assertThat(foundUser.getEmail(), is(user.getEmail()));
    assertThat(foundUser.getBiography(), nullValue());
    assertTrue(foundUser.isActive());
  }

  @Test
  @DisplayName("Empty should be returned when no user is found by id")
  void getByIdReturnsEmptyWhenNotFound() {
    // give
    when(appUserRepo.findByIdOptional(Mockito.anyLong())).thenReturn(Optional.empty());

    // when
    var user = this.appUserService.getUserById(1L);

    // then
    assertThat(user, is(Optional.empty()));
  }

  @Test
  @DisplayName("Persisted User should be normalized")
  void persistUserShouldNormalizeUserData() {
    // give
    final var appUser = this.provideSampleUser();
    appUser.setUsername("  " + appUser.getUsername().toUpperCase() + "  ");
    appUser.setEmail("My.Email@AcMe.com");
    appUser.setActive(false);
    appUser.setCreationDate(null);

    when(this.appUserRepo.findByUserNameOrEmail(anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    // when
    this.appUserService.persistAppUser(appUser);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(AppUser.class);
    verify(this.appUserRepo).persist(argumentCaptor.capture());
    final var capturedUser = argumentCaptor.getValue();
    assertThat(capturedUser.getUsername(), is(appUser.getUsername().toLowerCase().trim()));
    assertThat(capturedUser.getEmail(), is(appUser.getEmail().toLowerCase().trim()));
    assertThat(capturedUser.getCreationDate(), notNullValue());
    assertTrue(capturedUser.isActive());
  }

  @RepeatedTest(value = 2, name = "A non-unique appUser should not be persisted")
  void persistUserShouldNotPersistNonUniqueUsers(RepetitionInfo repetitionInfo) {
    Assertions.assertThrows(
        UniquenessViolationException.class,
        () -> {
          // given
          final var appUser = this.provideSampleUser();
          when(this.appUserRepo.findByUserNameOrEmail(anyString(), anyString()))
              .thenReturn(List.of(appUser));

          AppUser copy = null;
          if (repetitionInfo.getCurrentRepetition() == 1) {
            copy = appUser.withEmail("fake123@mail.com");
          } else {
            copy = appUser.withUsername(faker.funnyName().name());
          }

          // when
          this.appUserService.persistAppUser(copy);
        });
  }

  @Test
  @DisplayName("Disable user will return an empty optional if user is not found")
  void disableUserWillReturnEmptyIfUserNotFound() {
    // when
    when(this.appUserRepo.findByUserName(anyString())).thenReturn(Optional.empty());

    // then
    var output = this.appUserService.disableUser("johndoe");

    // then
    assertThat(output, is(Optional.empty()));
  }

  @ParameterizedTest
  @DisplayName("Disable user will ignore if the username is invalid {}")
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  void disableUserWillIgnoreIfInputIsInvalid(String input) {
    // then
    var output = this.appUserService.disableUser(input);

    // then
    assertThat(output, is(Optional.empty()));
  }


  @Test
  @DisplayName("Update user should return an empty optional if user is not found")
  void updateUserShouldReturnEmptyIfUserNotFound() {
    //given
    when(this.appUserRepo.findByUserName(anyString())).thenReturn(Optional.empty());

    // when
    var output = this.appUserService.updateAppUser(provideSampleUser());

    // then
    assertThat(output, is(Optional.empty()));
  }

  @Test
  @DisplayName("Update user should not change data that are considered constant")
  void updateUserShouldNotUpdateConstantData() {
    //given
    var repoUser = provideSampleUser();
    repoUser.setId(1000L);
    var fiveDaysAgo = Instant.now().minus(5, ChronoUnit.DAYS);
    repoUser.setCreationDate(fiveDaysAgo);
    when(this.appUserRepo.findByUserName(anyString())).thenReturn(Optional.of(repoUser));

    final var sampleUser = repoUser.withFirstName("John");
    sampleUser.setLastName("McN'Cheese");
    sampleUser.setUsername("nonono");
    sampleUser.setEmail("john.mcc@acme.com");
    sampleUser.setBiography("Just an old man");
    sampleUser.toggleActive();
    sampleUser.toToday();
    sampleUser.setId(2000L);

    // when
    var output = this.appUserService.updateAppUser(sampleUser);

    // then
    AppUser outputUser = output.get();
    assertThat(outputUser.getCreationDate(), is(fiveDaysAgo));
    assertThat(outputUser.getId(), is(repoUser.getId()));
    assertThat(outputUser.getUsername(), is(repoUser.getUsername()));
    assertTrue(outputUser.isActive());

    assertThat(outputUser.getFirstName(), is(sampleUser.getFirstName()));
    assertThat(outputUser.getLastName(), is(sampleUser.getLastName()));
    assertThat(outputUser.getBiography(), is(sampleUser.getBiography()));
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
}
