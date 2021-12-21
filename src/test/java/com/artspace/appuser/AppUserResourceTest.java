package com.artspace.appuser;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;

import io.quarkus.test.junit.QuarkusTest;
import javax.inject.Inject;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

//FIXME: Move this from UnitTest to an IntegrationTest. Too "slow" to be considered a unit test
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppUserResourceTest {

  private static final String JSON = "application/json;charset=UTF-8";

  private static final String DEFAULT_USERNAME = "arturfigueira";

  @Inject
  AppUserService userService;


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
        .get("/api/appusers/"+DEFAULT_USERNAME)
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
}
