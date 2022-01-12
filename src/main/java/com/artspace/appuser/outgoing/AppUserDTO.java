package com.artspace.appuser.outgoing;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Transfer Object used to ship user data to external services
 */
@Data
@NoArgsConstructor
@ToString
public class AppUserDTO {
  private String username;
  private String firstName;
  private String lastName;
  private boolean isActive;
}
