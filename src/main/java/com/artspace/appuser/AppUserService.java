package com.artspace.appuser;

import com.artspace.appuser.outgoing.AppUserDTO;
import com.artspace.appuser.outgoing.DataEmitter;
import io.quarkus.hibernate.reactive.panache.common.runtime.ReactiveTransactional;
import io.smallrye.mutiny.Uni;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.persistence.NoResultException;
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
@AllArgsConstructor
@ReactiveTransactional
public class AppUserService {

  final AppUserRepository appUserRepo;

  final DataEmitter<AppUserDTO> dataEmitter;

  final Logger logger;

  /**
   * Searches for a {@link AppUser} by its unique identifier.
   *
   * @param id long value that represents the unique identifier of the required user
   * @return An {@link Uni} that will resolve into an Optional with found user or empty otherwise
   */
  @Transactional(TxType.SUPPORTS)
  public Uni<Optional<AppUser>> getUserById(final long id) {
    return appUserRepo
        .findById(id)
        .map(Optional::of)
        .onFailure(NoResultException.class)
        .recoverWithItem(Optional.empty());
  }

  /**
   * Searches for a {@link AppUser} by its unique username
   *
   * @param userName Non-null, nor empty, username of the required User
   * @return An {@link Uni} that will resolve into an Optional with found user or empty otherwise
   */
  @Transactional(TxType.SUPPORTS)
  public Uni<Optional<AppUser>> getUserByUserName(String userName) {
    return Optional.ofNullable(userName)
        .filter(value -> !value.isBlank())
        .map(
            un ->
                this.appUserRepo
                    .findByUserName(un)
                    .map(Optional::of)
                    .onFailure(NoResultException.class)
                    .recoverWithItem(Optional.empty()))
        .orElse(Uni.createFrom().item(Optional.empty()));
  }

  /**
   * Disable a specific user by its username. If the user is already disable nothing will occur. At
   * the end of the action the username of the user will be returned as proof that the action
   * worked, an empty optional will be returned in case of a non-existent user.
   *
   * <p>Disabling the user will also trigger a message emission to propagate this change to
   * external services that uses ser data
   *
   * @param userName Non-null, nor empty, username of the required User to be disabled
   * @return {@link Uni} that will resolve into an Optional with the username of the disabled user,
   *     an empty optional will be returned if no user was found
   */
  public Uni<Optional<String>> disableUser(String userName) {
    return this.getUserByUserName(userName)
        .invoke(appUser -> appUser.ifPresent(AppUser::toggleActive))
        .onItem()
        .invoke(userOptional -> userOptional.ifPresent(this::broadcastChanges))
        .map(appUser -> appUser.map(AppUser::getUsername));
  }

  /**
   * Permanently persist the given {@link AppUser} into the repository. This user must contain only
   * valid attributes, according tho its schema. It must also contain a unique username and email.
   *
   * @param inputAppUser which is required to be persisted
   * @throws UniquenessViolationException if Username or email are not unique
   * @throws javax.validation.ConstraintViolationException if given user contains any invalid data
   * @throws NullPointerException If inputAppUser is null
   * @return An {@link Uni} that will be resolved into the persisted AppUser
   */
  public Uni<AppUser> persistAppUser(final @Valid @NotNull AppUser inputAppUser) {
    final var appUser = inputAppUser.toToday();
    this.normalizeData(appUser);
    return this.validateUniqueness(appUser)
        .chain(appUserRepo::persist)
        .invoke(entity -> logger.debugf("User successfully persisted: %s", entity))
        .onItem()
        .invoke(this::broadcastChanges);
  }

  /**
   * Updates the given {@link AppUser}. Not all attributes will be updated. Id, username, creation
   * date and isActive won't be updated via this method. The former three are constants, and It's
   * important to not update them, as it is used by the entire application as a data bound.
   *
   * <p>Disable a appUser can be achieved with {@link #disableUser(String)}}
   *
   * @param inputAppUser instance with the updated data
   * @throws javax.validation.ConstraintViolationException if given user contains any invalid data
   * @throws NullPointerException If specified user is null
   * @return An {@link Uni} that will resolve into an optional with the updated AppUser data or an
   *     empty optional if the user was not found.
   */
  public Uni<Optional<AppUser>> updateAppUser(final @Valid @NotNull AppUser inputAppUser) {
    return this.getUserByUserName(inputAppUser.getUsername())
        .map(
            userOptional -> {
              if (userOptional.isPresent()) {
                final var entity = userOptional.get();
                this.normalizeData(inputAppUser);
                entity.setEmail(inputAppUser.getEmail());
                entity.setFirstName(inputAppUser.getFirstName());
                entity.setLastName(inputAppUser.getLastName());
                entity.setBiography(inputAppUser.getBiography());
                logger.debugf("User successfully updated: %s", entity);
              }
              return userOptional;
            })
        .onItem()
        .invoke(userOptional -> userOptional.ifPresent(this::broadcastChanges));
  }

  private void broadcastChanges(final AppUser appUser) {
    final var appUserDTO = toDTO(appUser);
    dataEmitter.emit(UUID.randomUUID().toString(), appUserDTO);
  }

  private static AppUserDTO toDTO(final AppUser appUser) {
    final var appUserDTO = new AppUserDTO();
    appUserDTO.setActive(appUser.isActive());
    appUserDTO.setUsername(appUser.getUsername());
    appUserDTO.setFirstName(appUser.getFirstName());
    appUserDTO.setLastName(appUser.getLastName());
    return appUserDTO;
  }

  private void normalizeData(AppUser appUser) {
    appUser.enableIt();
    appUser.setEmail(appUser.getEmail().toLowerCase().trim());
    appUser.setUsername(appUser.getUsername().toLowerCase().trim());
  }

  private Uni<AppUser> validateUniqueness(AppUser appUser) {
    return this.appUserRepo
        .findByUserNameOrEmail(appUser.getUsername(), appUser.getEmail())
        .map(
            entities -> {
              final var violations = new HashSet<UniquenessViolation>();

              for (var user : entities) {
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

              return appUser;
            });
  }
}
