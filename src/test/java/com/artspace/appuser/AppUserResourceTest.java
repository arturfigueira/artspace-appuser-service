package com.artspace.appuser;

import static com.artspace.appuser.ExtStatus.UNPROCESSABLE_ENTITY;
import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.github.javafaker.Faker;
import io.quarkus.test.junit.QuarkusTest;
import javax.inject.Inject;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

// FIXME: Move this from UnitTest to an IntegrationTest. Too "slow" to be considered a unit test
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppUserResourceTest {

  private static final String JSON = "application/json;charset=UTF-8";

  private static final String DEFAULT_USERNAME = "arturfigueira";

  private static Faker FAKER;

  @Inject AppUserService userService;

  @BeforeAll
  static void setup() {
    FAKER = new Faker();
  }

  @Test
  @DisplayName("An OPEN Api resource should be available")
  void shouldPingOpenAPI() {
    given()
        .header(ACCEPT, APPLICATION_JSON)
        .when()
        .get("/q/openapi")
        .then()
        .statusCode(OK.getStatusCode());
  }

  @Test
  @DisplayName("An existing user should be returned by its user name")
  void shouldGetExistingAppUserByUserName() {

    final var user = userService.getUserByUserName(DEFAULT_USERNAME).get();

    given()
        .when()
        .get("/api/appusers/" + DEFAULT_USERNAME)
        .then()
        .statusCode(OK.getStatusCode())
        .header(CONTENT_TYPE, JSON)
        .body("username", Is.is(DEFAULT_USERNAME))
        .body("firstName", Is.is(user.getFirstName()))
        .body("lastName", Is.is(user.getLastName()))
        .body("biography", Is.is(user.getBiography()))
        .body("email", Is.is(user.getEmail()))
        .body("active", Is.is(user.isActive()));
  }

  @Test
  @DisplayName("A new AppUser should be able to be persisted")
  void shouldPersistNewAppUser() {
    var appUser = new AppUser();
    appUser.setUsername("johndoejr");
    appUser.setFirstName("John");
    appUser.setLastName("Doe Jr.");
    appUser.setEmail("johndoejr@mail.com");

    var location =
        given()
            .header(CONTENT_TYPE, JSON)
            .header(ACCEPT, JSON)
            .body(appUser)
            .when()
            .post("/api/appusers")
            .then()
            .statusCode(CREATED.getStatusCode())
            .extract()
            .header("Location");

    // then
    assertThat(location, endsWith("/api/appusers/johndoejr"));

    given()
        .pathParam("username", "johndoejr")
        .when()
        .get("/api/appusers/{username}")
        .then()
        .statusCode(OK.getStatusCode())
        .header(CONTENT_TYPE, JSON)
        .body("id", notNullValue())
        .body("username", Is.is(appUser.getUsername()))
        .body("firstName", Is.is(appUser.getFirstName()))
        .body("active", Is.is(true))
        .body("email", Is.is(appUser.getEmail()))
        .body("biography", nullValue())
        .body("lastName", Is.is(appUser.getLastName()));
  }

  @Test
  @DisplayName("An invalid AppUser should not be persisted")
  void createAppUserShouldNotPersistInvalid() {
    var appUser = new AppUser();
    appUser.setUsername("");
    appUser.setFirstName("John");
    appUser.setLastName("Doe Jr.");
    appUser.setEmail("");

    given()
        .header(CONTENT_TYPE, JSON)
        .header(ACCEPT, JSON)
        .body(appUser)
        .when()
        .post("/api/appusers")
        .then()
        .statusCode(BAD_REQUEST.getStatusCode());
  }

  @Test
  @DisplayName("An empty request should not be persisted")
  void createAppUserShouldNotPersistEmpty() {
    given()
        .header(CONTENT_TYPE, JSON)
        .header(ACCEPT, JSON)
        .when()
        .post("/api/appusers")
        .then()
        .statusCode(BAD_REQUEST.getStatusCode());
  }

  @Test
  @DisplayName("An AppUser should not be persisted twice")
  void createAppUserShouldNotPersistTwiceSameEntity() {
    AppUser appUser = generateSampleUser();

    given()
        .header(CONTENT_TYPE, JSON)
        .header(ACCEPT, JSON)
        .body(appUser)
        .when()
        .post("/api/appusers")
        .then()
        .statusCode(CREATED.getStatusCode());

    given()
        .header(CONTENT_TYPE, JSON)
        .header(ACCEPT, JSON)
        .body(appUser)
        .when()
        .post("/api/appusers")
        .then()
        .statusCode(UNPROCESSABLE_ENTITY.getStatusCode());
  }

  private AppUser generateSampleUser() {
    var appUser = new AppUser();
    appUser.setUsername(FAKER.name().username());
    appUser.setFirstName(FAKER.name().firstName());
    appUser.setLastName(FAKER.name().lastName());
    appUser.setEmail(appUser.getUsername() + "@fake-mail.com");
    return appUser;
  }

  @Test
  @DisplayName("Disable user should effectively disable required user")
  void shouldDisableAppUser() {
    // Given
    var appUser = this.userService.persistAppUser(generateSampleUser());
    ;
    // when
    given()
        .pathParam("username", appUser.getUsername())
        .when()
        .put("/api/appusers/{username}/disable")
        .then()
        .statusCode(OK.getStatusCode());

    // then
    given()
        .pathParam("username", appUser.getUsername())
        .when()
        .get("/api/appusers/{username}")
        .then()
        .statusCode(OK.getStatusCode())
        .header(CONTENT_TYPE, JSON)
        .body("active", Is.is(false))
        .body("id", Is.is(appUser.getId().intValue()))
        .body("username", Is.is(appUser.getUsername()))
        .body("firstName", Is.is(appUser.getFirstName()))
        .body("lastName", Is.is(appUser.getLastName()))
        .body("email", Is.is(appUser.getEmail()))
        .body("biography", Is.is(appUser.getBiography()));
  }
}
