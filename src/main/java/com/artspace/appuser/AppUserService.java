package com.artspace.appuser;

import com.artspace.appuser.cache.CacheService;
import com.artspace.appuser.outgoing.AppUserDTO;
import com.artspace.appuser.outgoing.DataEmitter;
import io.quarkus.hibernate.reactive.panache.common.runtime.ReactiveTransactional;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.i18n.ProviderLogging;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
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

  final CacheService cacheService;

  final AppUserMapper appUserMapper;

  final Logger logger;

  /**
   * Searches for a {@link AppUser} by its unique identifier.
   *
   * <p>This method does not access any cache and goes directory to where the data is stored.
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
  public Uni<Optional<AppUser>> getUserByUserName(String userName, final String correlationId) {
    return this.fromCache(userName, correlationId)
        .chain(
            optionalUser ->
                optionalUser
                    .map(u -> Uni.createFrom().item(optionalUser))
                    .orElseGet(() -> retrieveAndCache(userName, correlationId)));
  }

  private Uni<Optional<AppUser>> retrieveAndCache(String userName, String correlationId) {
    return this.getUserByUserNameFromDataBase(userName)
        .invoke(
            optionalAppUser ->
                optionalAppUser.ifPresent(appUser -> this.cacheUser(appUser, correlationId)));
  }

  protected Uni<Optional<AppUser>> getUserByUserNameFromDataBase(String userName) {
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
   * Disable a specific user by its username.
   *
   * <p>If the user is already disable nothing will occur. At the end of the action the username of
   * the user will be returned as proof that the action worked, an empty optional will be returned
   * in case of a non-existent user.
   *
   * <p>Disabling the user will also trigger a message emission to propagate this change to external
   * services that uses ser data
   *
   * <p>This will also clean any cached user data, if it exists
   *
   * @param userName Non-null, nor empty, username of the required User to be disabled
   * @param correlationId Trasnsit id to identify the request between services
   * @return {@link Uni} that will resolve into an Optional with the username of the disabled user,
   *     an empty optional will be returned if no user was found
   */
  public Uni<Optional<String>> disableUser(String userName, final String correlationId) {
    return this.getUserByUserNameFromDataBase(userName)
        .invoke(appUser -> appUser.ifPresent(AppUser::toggleActive))
        .invoke(
            optionalAppUser ->
                optionalAppUser.ifPresent(u -> this.clearCache(u.getUsername(), correlationId)))
        .invoke(
            userOptional -> userOptional.ifPresent(u -> this.broadcastChanges(u, correlationId)))
        .map(appUser -> appUser.map(AppUser::getUsername));
  }

  /**
   * Permanently persist the given {@link AppUser} into the repository.
   *
   * <p>This user must contain only valid attributes, according tho its schema. It must also contain
   * a unique username and email.
   *
   * <p>If successful this will broadcast an event indicating that a new user has been created. This
   * will also cache this new user data for quicker access later.
   *
   * @param inputAppUser which is required to be persisted
   * @param correlationId Trasnsit id to identify the request between services
   * @throws UniquenessViolationException if Username or email are not unique
   * @throws javax.validation.ConstraintViolationException if given user contains any invalid data
   * @throws NullPointerException If inputAppUser is null or correlationId is blank
   * @return An {@link Uni} that will be resolved into the persisted AppUser
   */
  public Uni<AppUser> persistAppUser(
      final @Valid @NotNull AppUser inputAppUser, @NotBlank final String correlationId) {
    final var appUser = inputAppUser.toToday();
    this.normalizeData(appUser);
    return this.validateUniqueness(appUser)
        .chain(appUserRepo::persist)
        .invoke(
            entity -> logger.debugf("[%s] User successfully persisted: %s", correlationId, entity))
        .invoke(u -> this.cacheUser(u, correlationId))
        .invoke(u -> this.broadcastChanges(u, correlationId));
  }

  /**
   * Updates the given {@link AppUser}.
   *
   * <p>Not all attributes will be updated. Id, username, creation date and isActive won't be
   * updated via this method. The former three are constants, and It's important to not update them,
   * as it is used by the entire application as a data bound.
   *
   * <p>Updates will ignore cached data, and will retrieve the current user state directly from its
   * repository. If the update is successful, then the cache will be updated.
   *
   * <p>Also, an event will be broadcast to indicate that the given user was updated
   *
   * <p>Disable a appUser can be achieved with {@link #disableUser(String, String)}}}
   *
   * @param inputAppUser instance with the updated data
   * @param correlationId Trasnsit id to identify the request between services
   * @throws javax.validation.ConstraintViolationException if given user contains any invalid data
   * @throws NullPointerException If specified user is null
   * @return An {@link Uni} that will resolve into an optional with the updated AppUser data or an
   *     empty optional if the user was not found.
   */
  public Uni<Optional<AppUser>> updateAppUser(
      final @Valid @NotNull AppUser inputAppUser, final String correlationId) {
    return this.getUserByUserNameFromDataBase(inputAppUser.getUsername())
        .chain(
            userOptional ->
                userOptional.isPresent()
                    ? validateAndMerge(inputAppUser, userOptional.get(), correlationId)
                    : ignoreUpdate(inputAppUser, correlationId));
  }

  private Uni<Optional<AppUser>> validateAndMerge(
      final AppUser inputAppUser, final AppUser dbUser, final String correlationId) {
    return this.validateMailUniqueness(inputAppUser)
        .map(appUser -> updateAndFlush(appUser, dbUser))
        .invoke(u -> logger.debugf("[%s] User successfully updated: %s", correlationId, u))
        .invoke(u -> cacheUser(u, correlationId))
        .invoke(u -> broadcastChanges(u, correlationId))
        .map(Optional::of);
  }

  private Uni<Optional<AppUser>> ignoreUpdate(
      final AppUser inputAppUser, final String correlationId) {
    logger.debugf(
        "[%s] User %s not found. Updated ignored", correlationId, inputAppUser.getUsername());
    return Uni.createFrom().item(Optional.empty());
  }

  /**
   * * We can`t let the context perceive, by it own, that this element was updated, and onliest than
   * update its content into the database. We must certify that changes can be merged right on,
   * before propagating the update to other services
   */
  private AppUser updateAndFlush(final AppUser inputAppUser, final AppUser storedUser) {
    this.normalizeData(inputAppUser);
    storedUser.setEmail(inputAppUser.getEmail());
    storedUser.setFirstName(inputAppUser.getFirstName());
    storedUser.setLastName(inputAppUser.getLastName());
    storedUser.setBiography(inputAppUser.getBiography());
    this.appUserRepo.flush();
    return storedUser;
  }

  private void clearCache(String username, final String correlationId) {
    this.cacheService
        .remove(username)
        .invoke(
            aBoolean ->
                logger.infof(
                    "[%s] Caching del request finished. Result: %s", correlationId, aBoolean))
        .onFailure()
        .invoke(
            e ->
                logger.errorf(
                    "[%s] A failure occurred, which prevented the caching cleaning. %s",
                    correlationId, e))
        .subscribe()
        .with(x -> {}, ProviderLogging.log::failureEmittingMessage);
  }

  private Uni<Optional<AppUser>> fromCache(String username, final String correlationId) {
    return this.cacheService
        .find(username)
        .map(result -> result.map(this.appUserMapper::toEntity))
        .invoke(
            optionalAppUser ->
                optionalAppUser.ifPresentOrElse(
                    appUser ->
                        logger.infof("[%s] Found cached user for %s", correlationId, username),
                    () ->
                        logger.infof(
                            "[%s] Not found a cached user for %s", correlationId, username)));
  }

  private void cacheUser(final AppUser appUser, final String correlationId) {
    final var cacheUserDTO = this.appUserMapper.toCache(appUser);
    this.cacheService
        .persist(cacheUserDTO)
        .invoke(
            aBoolean ->
                logger.infof(
                    "[%s] Caching set request finished. Result: %s", correlationId, aBoolean))
        .onFailure()
        .invoke(
            e ->
                logger.errorf(
                    "[%s] A failure occurred, which prevented the caching. %s", correlationId, e))
        .subscribe()
        .with(x -> {}, ProviderLogging.log::failureEmittingMessage);
  }

  private void broadcastChanges(final AppUser appUser, final String correlationId) {
    final var appUserDTO = toDTO(appUser);
    dataEmitter.emit(correlationId, appUserDTO);
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

  private Uni<AppUser> validateMailUniqueness(final AppUser appUser) {
    return this.appUserRepo
        .findByUserNameOrEmail(appUser.getUsername(), appUser.getEmail())
        .map(
            entities -> {
              if (entities.size() > 1) {
                final var violation = new UniquenessViolation("email", "E-mail must be unique");
                throw new UniquenessViolationException(Set.of(violation));
              }
              return appUser;
            });
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
