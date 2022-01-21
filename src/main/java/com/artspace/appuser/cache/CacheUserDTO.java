package com.artspace.appuser.cache;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@Data
@ToString
public class CacheUserDTO {
  private String id;
  private String username;
  private String email;
  private String firstName;
  private String lastName;
  private String biography;
  private Long createdDate;
  private boolean active;
}
