package com.artspace.appuser;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * This represents a real human being for the system. Contains information to identify and
 * differentiate users across the application, including when it was added to the application and if
 * is active or not.
 *
 * <p>All users will have a unique <i>username</i> and <i>email address</i>.
 *
 * @since 1.0.0
 */
@Entity
@Schema(description = "An application user")
@Data
@ToString
@NoArgsConstructor
class AppUser {

  @Id
  @GeneratedValue
  private Long id;

  @NotNull
  @Column(unique = true)
  @Size(min = 3, max = 50)
  private String username;

  @NotNull
  @Column(unique = true)
  private String email;

  @NotNull
  @Size(min = 3, max = 50)
  private String firstName;

  private String lastName;

  @Column(columnDefinition = "TEXT")
  private String biography;

  @NotNull private Instant creationDate = Instant.now();

  @NotNull private boolean isActive = true;
}
