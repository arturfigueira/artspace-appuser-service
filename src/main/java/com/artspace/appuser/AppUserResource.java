package com.artspace.appuser;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.smallrye.mutiny.Uni;
import java.net.URI;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.AllArgsConstructor;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestPath;

@Path("/api/appusers")
@Tag(name = "appusers")
@AllArgsConstructor
class AppUserResource {
  protected static final String CORRELATION_HEADER = "X-Request-ID";

  final AppUserService appUserService;
  final Logger logger;

  @Operation(summary = "Returns an app user for a given username")
  @GET
  @Path("/{username}")
  @APIResponse(
      responseCode = "200",
      content =
          @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = AppUser.class)))
  @APIResponse(responseCode = "204", description = "The app user is not found for a given username")
  public Uni<Response> getAppUser(@RestPath String username,
      @NotBlank @HeaderParam(CORRELATION_HEADER) String correlationId) {
    try{
      var user = appUserService.getUserByUserName(username);

      return user.map(
          appUser -> {
            var response = Response.noContent().build();
            if (appUser.isPresent()) {
              logger.debugf("[%s] Found user %s", correlationId, appUser.get());
              response = Response.ok(appUser.get()).build();
            } else {
              logger.debugf("[%s] User not found with username %s", correlationId, username);
            }

            return response;
          });
    }catch (Exception e){
      return null;
    }

  }

  @Operation(summary = "Creates a valid AppUser")
  @POST
  @APIResponse(
      responseCode = "201",
      description = "The URI of the created AppUser",
      content =
          @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = URI.class)))
  @APIResponse(
      responseCode = "422",
      description = "The AppUser entity was not able to be persisted due to a conflict")
  public Uni<Response> createAppUser(@Valid @NotNull AppUser appUser,
      @NotBlank @HeaderParam(CORRELATION_HEADER) String correlationId,
      @Context final UriInfo uriInfo) {
    var persistedUser = appUserService.persistAppUser(appUser, correlationId);
    return persistedUser.map(
        entity -> {
          var builder = uriInfo.getAbsolutePathBuilder().path(entity.getUsername());
          logger.debugf("[%s] New AppUser created with URI %s", correlationId, builder.build().toString());
          return Response.created(builder.build()).build();
        });
  }

  @Operation(summary = "Updates an existing AppUser")
  @PUT
  @APIResponse(responseCode = "200", description = "AppUser successfully updated")
  @APIResponse(responseCode = "204", description = "No AppUser found for the given identifier")
  public Uni<Response> updateAppUser(@Valid @NotNull AppUser toBeUpdated,
      @NotBlank @HeaderParam(CORRELATION_HEADER) String correlationId) {
    final var updatedUser = this.appUserService.updateAppUser(toBeUpdated, correlationId);
    return updatedUser.map(
        userOptional -> {
          var response = Response.noContent().build();
          if (userOptional.isPresent()) {
            final var entity = userOptional.get();
            logger.debugf("[%s] AppUser %s successfully updated", correlationId, entity.getUsername());
            response = Response.ok(entity).build();
          } else {
            logger.debugf(
                "[%s] AppUser %s not disable due to being not found", correlationId, toBeUpdated.getUsername());
          }

          return response;
        });
  }

  @Operation(summary = "Disable a AppUser")
  @PUT
  @Path("/{username}/disable")
  @APIResponse(responseCode = "200", description = "AppUser successfully disabled")
  @APIResponse(responseCode = "204", description = "No AppUser found for the given identifier")
  public Uni<Response> disableAppUser(@RestPath String username,
      @NotBlank @HeaderParam(CORRELATION_HEADER) String correlationId) {
    final var disabledUser = this.appUserService.disableUser(username, correlationId);

    return disabledUser.map(
        userNameOptional -> {
          var response = Response.noContent().build();
          if (userNameOptional.isPresent()) {
            logger.debugf("[%s] AppUser %s successfully disabled", correlationId, userNameOptional.get());
            response = Response.ok().build();
          } else {
            logger.debugf("[%s] AppUser %s not disable due to being not found", correlationId, username);
          }

          return response;
        });
  }
}
