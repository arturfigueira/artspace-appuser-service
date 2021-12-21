package com.artspace.appuser;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
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
  public Response getUser(@RestPath String username) {
    var user = appUserService.getUserByUserName(username);

    if (user.isPresent()) {
      logger.debugf("Found user {}", user.get());
      return Response.ok(user.get()).build();
    } else {
      logger.debugf("User not found with username {}", username);
      return Response.noContent().build();
    }
  }
}
