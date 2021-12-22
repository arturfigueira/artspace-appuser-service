package com.artspace.appuser;

import java.util.List;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;

/** Concrete implementation of an AppUser repository over a relational database */
@ApplicationScoped
public class AppUserRepositoryImpl implements AppUserRepository {

  public Optional<AppUser> findByUserName(String username) {
    return find("username", username).singleResultOptional();
  }

  @Override
  public List<AppUser> findByUserNameOrEmail(String username, String email) {
    return find("email = ?1 OR username = ?2", email, username).list();
  }
}
