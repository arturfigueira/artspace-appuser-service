package com.artspace.appuser;

import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;

/**
 * Concrete implementation of an AppUser repository over a relational database
 */
@ApplicationScoped
public class AppUserRepositoryImpl implements AppUserRepository {

  public Optional<AppUser> findByUserName(String username) {
    return find("username", username)
        .singleResultOptional();
  }
}
