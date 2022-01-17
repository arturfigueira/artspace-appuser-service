package com.artspace.appuser;

import static com.artspace.appuser.AppUserResource.CORRELATION_HEADER;
import static com.artspace.appuser.ExtStatus.UNPROCESSABLE_ENTITY;
import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.github.javafaker.Faker;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Duration;
import java.util.UUID;
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

  private static final Duration FIVE_SECONDS = Duration.ofSeconds(5);

  @Inject AppUserService userService;

  @BeforeAll
  static void setup() {
    FAKER = new Faker();
  }

  @Test
  @DisplayName("An OPEN Api resource should be available")
  void shouldPingOpenAPI() {
    given()
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
        .header(ACCEPT, APPLICATION_JSON)
        .when()
        .get("/q/openapi")
        .then()
        .statusCode(OK.getStatusCode());
  }

  @Test
  @DisplayName("An empty response should be returned when user is not found")
  void shouldReturnEmptyResponseWhenUserNotFound() {

    given()
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
        .when()
        .pathParam("username", "not-one")
        .get("/api/appusers/{username}")
        .then()
        .statusCode(NO_CONTENT.getStatusCode());
  }

  @Test
  @DisplayName("An existing user should be returned by its user name")
  void shouldGetExistingAppUserByUserName() {

    final var user =
        userService.getUserByUserName(DEFAULT_USERNAME).await().atMost(FIVE_SECONDS).get();

    given()
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
        .when()
        .pathParam("username", DEFAULT_USERNAME)
        .get("/api/appusers/{username}")
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
            .header(CORRELATION_HEADER, generateSampleCorrelationId())
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
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
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
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
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
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
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
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
        .body(appUser)
        .when()
        .post("/api/appusers")
        .then()
        .statusCode(CREATED.getStatusCode());

    given()
        .header(CONTENT_TYPE, JSON)
        .header(ACCEPT, JSON)
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
        .body(appUser)
        .when()
        .post("/api/appusers")
        .then()
        .statusCode(UNPROCESSABLE_ENTITY.getStatusCode());
  }

  @Test
  @DisplayName("Disable user should effectively disable required user")
  void shouldDisableAppUser() {
    // Given
    var appUser =
        this.userService
            .persistAppUser(generateSampleUser(), UUID.randomUUID().toString())
            .await()
            .atMost(FIVE_SECONDS);

    // when
    given()
        .pathParam("username", appUser.getUsername())
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
        .when()
        .put("/api/appusers/{username}/disable")
        .then()
        .statusCode(OK.getStatusCode());

    // then
    given()
        .pathParam("username", appUser.getUsername())
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
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

  @Test
  @DisplayName("A AppUser should be able to be updated")
  void shouldUpdateAppUser() {
    var appUser = new AppUser();
    appUser.setUsername("jakedoe");
    appUser.setFirstName("Jak");
    appUser.setLastName("Doe");
    appUser.setEmail("jakedoe@acme.com");

    given()
        .header(CONTENT_TYPE, JSON)
        .header(ACCEPT, JSON)
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
        .body(appUser)
        .when()
        .post("/api/appusers")
        .then()
        .statusCode(CREATED.getStatusCode());

    appUser.setFirstName("Jake");
    appUser.setLastName("Doe The Second");
    appUser.setEmail("jake-doe@acme.com");
    appUser.setBiography("Just a sample user");

    given()
        .when()
        .header(CONTENT_TYPE, JSON)
        .header(ACCEPT, JSON)
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
        .body(appUser)
        .put("/api/appusers")
        .then()
        .statusCode(OK.getStatusCode())
        .header(CONTENT_TYPE, JSON)
        .body("id", notNullValue())
        .body("username", Is.is("jakedoe"))
        .body("firstName", Is.is("Jake"))
        .body("active", Is.is(true))
        .body("email", Is.is("jake-doe@acme.com"))
        .body("biography", Is.is("Just a sample user"))
        .body("lastName", Is.is("Doe The Second"));
  }

  @Test
  @DisplayName("An update should return no content when user does not exists")
  void shouldReturnNotContentWhenUserDoesNotExists() {
    var appUser = this.generateSampleUser();

    given()
        .when()
        .header(CONTENT_TYPE, JSON)
        .header(ACCEPT, JSON)
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
        .body(appUser)
        .put("/api/appusers")
        .then()
        .statusCode(NO_CONTENT.getStatusCode());
  }

  @Test
  @DisplayName("An invalid AppUser should not be updated")
  void updateAppUserShouldNotPersistInvalid() {
    var appUser = this.generateSampleUser();
    appUser.setFirstName("");
    appUser.setEmail("abc");

    given()
        .header(CONTENT_TYPE, JSON)
        .header(ACCEPT, JSON)
        .header(CORRELATION_HEADER, generateSampleCorrelationId())
        .body(appUser)
        .when()
        .put("/api/appusers")
        .then()
        .statusCode(BAD_REQUEST.getStatusCode());
  }

  private String generateSampleCorrelationId() {
    return UUID.randomUUID().toString();
  }

  private AppUser generateSampleUser() {
    var appUser = new AppUser();
    appUser.setUsername(FAKER.name().username());
    appUser.setFirstName(FAKER.name().firstName());
    appUser.setLastName(FAKER.name().lastName());
    appUser.setEmail(appUser.getUsername() + "@fake-mail.com");
    return appUser;
  }
}
