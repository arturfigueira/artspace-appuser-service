package com.artspace.appuser;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.net.URI;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.AllArgsConstructor;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
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

  final AppUserService appUserService;
  final Logger logger;

  @Operation(summary = "Returns an app user for a given username")
  @GET
  @Path("/{username}")
  @Timeout()
  @Bulkhead(20)
  @APIResponse(
      responseCode = "200",
      content =
          @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = AppUser.class)))
  @APIResponse(responseCode = "204", description = "The app user is not found for a given username")
  public Response getAppUser(@RestPath String username) {
    var user = appUserService.getUserByUserName(username);

    if (user.isPresent()) {
      logger.tracef("Found user %s", user.get());
      return Response.ok(user.get()).build();
    } else {
      logger.tracef("User not found with username %s", username);
      return Response.noContent().build();
    }
  }

  @Operation(summary = "Creates a valid AppUser")
  @POST
  @Timeout()
  @Bulkhead()
  @APIResponse(
      responseCode = "201",
      description = "The URI of the created AppUser",
      content =
          @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = URI.class)))
  @APIResponse(
      responseCode = "422",
      description = "The AppUser entity was not able to be persisted due to a conflict")
  public Response createAppUser(@Valid @NotNull AppUser appUser, @Context UriInfo uriInfo) {
    var persistedUser = appUserService.persistAppUser(appUser);
    var builder = uriInfo.getAbsolutePathBuilder().path(persistedUser.getUsername());
    logger.debugf("New AppUser created with URI %s", builder.build().toString());
    return Response.created(builder.build()).build();
  }

  @Operation(summary = "Updates an existing AppUser")
  @PUT
  @Timeout()
  @APIResponse(responseCode = "200", description = "AppUser successfully updated")
  @APIResponse(responseCode = "204", description = "No AppUser found for the given identifier")
  public Response updateAppUser(@Valid @NotNull AppUser toBeUpdated) {
    final var updatedUser = this.appUserService.updateAppUser(toBeUpdated);
    if (updatedUser.isPresent()) {
      final var entity = updatedUser.get();
      logger.debugf("AppUser %s successfully updated", entity.getUsername());
      return Response.ok(entity).build();
    } else {
      logger.debugf("AppUser %s not disable due to being not found", toBeUpdated.getUsername());
      return Response.noContent().build();
    }
  }

  @Operation(summary = "Disable a AppUser")
  @PUT
  @Path("/{username}/disable")
  @Timeout()
  @APIResponse(responseCode = "200", description = "AppUser successfully disabled")
  @APIResponse(responseCode = "204", description = "No AppUser found for the given identifier")
  public Response disableAppUser(@RestPath String username) {
    Optional<String> disableUser = this.appUserService.disableUser(username);

    if (disableUser.isPresent()) {
      logger.debugf("AppUser %s successfully disabled", username);
      return Response.ok().build();
    } else {
      logger.debugf("AppUser %s not disable due to being not found", username);
      return Response.noContent().build();
    }
  }
}
