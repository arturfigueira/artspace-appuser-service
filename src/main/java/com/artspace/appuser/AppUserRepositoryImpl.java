package com.artspace.appuser;

import io.smallrye.mutiny.Uni;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;

/** Concrete implementation of an AppUser repository over a relational database */
@ApplicationScoped
class AppUserRepositoryImpl implements AppUserRepository {

  public Uni<AppUser> findByUserName(String username) {
    return find("username", username).singleResult();
  }

  @Override
  public Uni<List<AppUser>> findByUserNameOrEmail(String username, String email) {
    return find("email = ?1 OR username = ?2", email, username).list();
  }
}
