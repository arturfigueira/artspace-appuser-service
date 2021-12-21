package com.artspace.appuser;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

  @Mock private AppUserRepository appUserRepo;

  private AppUserService appUserService;

  @BeforeEach
  void setUp() {
    this.appUserService = new AppUserService(appUserRepo);
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
    var username = "johndoe";
    var id = 1L;
    var name = "John";
    var email = "johndoe@acme.com";

    var user = new AppUser();
    user.setFirstName(name);
    user.setEmail(email);
    user.setUsername(username);
    user.setId(id);

    when(appUserRepo.findByUserName(Mockito.anyString())).thenReturn(Optional.of(user));

    // when
    var foundUser = this.appUserService.getUserByUserName("jhondoe").get();

    // then
    assertThat(foundUser.getUsername(), is(username));
    assertThat(foundUser.getFirstName(), is(name));
    assertThat(foundUser.getId(), is(id));
    assertThat(foundUser.getEmail(), is(email));
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
}
