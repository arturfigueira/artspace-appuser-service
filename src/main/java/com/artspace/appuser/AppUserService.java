package com.artspace.appuser;

import static javax.transaction.Transactional.TxType.SUPPORTS;

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
  @Transactional(SUPPORTS)
  public Optional<AppUser> getUserById(final long id) {
    return appUserRepo.findByIdOptional(id);
  }

  /**
   * Searches for a {@link AppUser} by its unique username
   *
   * @param userName Non-null, nor empty, username of the required User
   * @return An {@code Optional<User>} if its found, or {@code Optional.empty()} otherwise
   */
  @Transactional(SUPPORTS)
  public Optional<AppUser> getUserByUserName(String userName) {
    return Optional.ofNullable(userName)
        .filter(value -> !value.isBlank())
        .flatMap(appUserRepo::findByUserName);
  }

  /**
   * Disable a specific user by its username. If the user is already disable nothing will occur. At
   * the end of the action the username of the user will be returned as proof that the action
   * worked, an empty optional will be returned in case of a non-existent user
   *
   * @param userName Non-null, nor empty, username of the required User to be disabled
   * @return Optional with the username or empty optional if the user was not found
   */
  public Optional<String> disableUser(String userName) {
    final var userOptional = this.getUserByUserName(userName);

    if (userOptional.isPresent() && userOptional.get().isActive()) {
      userOptional.get().toggleActive();
    }

    return userOptional.map(AppUser::getUsername);
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

  /**
   * Updates the given {@link AppUser}. Not all attributes will be updated. Id, username, creation
   * date and isActive won't be updated via this method. The former three are constants, and It's
   * important to not update them, as it is used by the entire application as a data bound.
   *
   * Disable a appUser can be achieved with {@link  #disableUser(String)}}
   *
   * @param inputAppUser instance with the updated data
   * @throws javax.validation.ConstraintViolationException if given user contains any invalid data
   * @throws NullPointerException If specified user is null
   * @return An optional with the updated AppUser data or empty if the user was not found.
   */
  public Optional<AppUser> updateAppUser(final @Valid @NotNull AppUser inputAppUser) {
    final var byUserName = this.appUserRepo.findByUserName(inputAppUser.getUsername());

    if (byUserName.isPresent()) {
      final var entity = byUserName.get();
      this.normalizeData(inputAppUser);
      entity.setEmail(inputAppUser.getEmail());
      entity.setFirstName(inputAppUser.getFirstName());
      entity.setLastName(inputAppUser.getLastName());
      entity.setBiography(inputAppUser.getBiography());
      logger.debugf("User successfully updated: %s", entity);
    }

    return byUserName;
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
