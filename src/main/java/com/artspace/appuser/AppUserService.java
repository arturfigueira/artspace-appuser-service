package com.artspace.appuser;

import java.util.HashSet;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.jboss.logging.Logger;

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

  final Logger logger;

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

  /**
   * Permanently persist the given {@link AppUser} into the repository. This user must contain only
   * valid attributes, according tho its schema. It must also contain a unique username and email.
   *
   * @param inputAppUser which is required to be persisted
   * @throws UniquenessViolationException if Username or email are not unique
   * @throws javax.validation.ConstraintViolationException if given user contains any invalid data
   * @throws NullPointerException If inputAppUser is null
   */
  public AppUser persistAppUser(final @Valid @NotNull AppUser inputAppUser) {
    final var appUser = inputAppUser.toToday();
    this.normalizeData(appUser);
    this.validateUniqueness(appUser);

    appUserRepo.persist(appUser);
    logger.debugf("User successfully persisted: %s", appUser);
    return appUser;
  }

  private void normalizeData(AppUser appUser) {
    appUser.enableIt();
    appUser.setEmail(appUser.getEmail().toLowerCase().trim());
    appUser.setUsername(appUser.getUsername().toLowerCase().trim());
  }

  private void validateUniqueness(AppUser appUser) {
    final var byUserNameOrEmail =
        this.appUserRepo.findByUserNameOrEmail(appUser.getUsername(), appUser.getEmail());

    final var violations = new HashSet<UniquenessViolation>();
    for (var user : byUserNameOrEmail) {
      if (user.getUsername().equals(appUser.getUsername())) {
        violations.add(new UniquenessViolation("username", "Username must be unique"));
      }

      if (user.getEmail().equals(appUser.getEmail())) {
        violations.add(new UniquenessViolation("email", "E-mail must be unique"));
      }
    }
    if (!violations.isEmpty()) {
      throw new UniquenessViolationException(violations);
    }
  }
}
