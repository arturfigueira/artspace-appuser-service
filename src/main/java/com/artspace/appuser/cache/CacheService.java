package com.artspace.appuser.cache;

import io.smallrye.mutiny.Uni;
import java.util.Optional;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * {@link CacheService} offers meanings to cache user data for quick access and easy, instead of a
 * more traditional, and time-consuming, approach such as a regular database.
 */
public interface CacheService {

  /**
   * Insert a {@link CacheUserDTO} into the cache.
   *
   * @param cacheUser non-null user data to be cached
   */
  Uni<Boolean> persist(@NotNull final CacheUserDTO cacheUser);

  /**
   * Find a {@link CacheUserDTO} by its cache id
   *
   * @param cacheId non-blank cache identifier
   */
  Uni<Optional<CacheUserDTO>> find(@NotBlank String cacheId);

  /**
   * Removes a {@link CacheUserDTO} from the cache.
   *
   * @param cacheId non-blank cache identifier
   */
  Uni<Boolean> remove(@NotBlank String cacheId);
}
