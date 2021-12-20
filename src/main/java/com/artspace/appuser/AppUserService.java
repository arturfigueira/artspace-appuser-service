package com.artspace.appuser;

import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import lombok.AllArgsConstructor;

/**
 * Service class to provide access to User data, stored on an external repository
 *
 * @since 1.0.0
 */
@ApplicationScoped
@Transactional(TxType.REQUIRED)
@AllArgsConstructor
class AppUserService {

  final AppUserRepository appUserRepo;

  /**
   * Searches for a {@link AppUser} by its unique identifier.
   *
   * @param id long value that represents the unique identifier of the required user
   * @return An {@code Optional<User>} if its found, or {@code Optional.empty()} otherwise
   */
  public Optional<AppUser> getUserById(final long id) {
    return appUserRepo.findByIdOptional(id);
  }

  /**
   * Searches for a {@link AppUser} by its unique username
   *
   * @param userName Non-null, nor empty, username of the required User
   * @return An {@code Optional<User>} if its found, or {@code Optional.empty()} otherwise
   */
  public Optional<AppUser> getUserByUserName(String userName) {
    return Optional.ofNullable(userName)
        .filter(value -> !value.isBlank())
        .flatMap(appUserRepo::findByUserName);
  }
}
