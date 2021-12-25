package com.artspace.appuser;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import java.util.List;

/**
 * A repository that has the capabilities to store {@link AppUser} data, providing methods to modify
 * this group of data.
 *
 * <p>This interface is an extension of <b>reactive</b> {@link PanacheRepository} which adds,
 * automatically, all of its methods to this interface. Therefore, this will provide basic methods
 * such as persist, delete, findById and so on.
 *
 * @since 1.0.0
 * @see PanacheRepository
 */
interface AppUserRepository extends PanacheRepository<AppUser> {

  /**
   * Searches for a {@link AppUser} by its unique username
   *
   * @param username of the element to be searched
   * @return an {@link Uni} that might resolve with found {@link AppUser} or as failure wrapping a
   *     {@link javax.persistence.NoResultException} exception.
   */
  Uni<AppUser> findByUserName(String username);

  /**
   * Search for {@link AppUser} that has either the specified username or the email
   *
   * @param username of the element to be searched for
   * @param email of the element to be searched for
   * @return a {@link Uni} that will resolve into a list with all users that matches specified
   *     arguments. If no results are found the list will be empty
   */
  Uni<List<AppUser>> findByUserNameOrEmail(String username, String email);
}
