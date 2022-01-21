package com.artspace.appuser.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.client.reactive.ReactiveRedisClient;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import io.smallrye.faulttolerance.api.FibonacciBackoff;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.Response;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

/**
 * Singleton implementation of a {@link CacheService} using redis.
 *
 * <p>Fault Tolerance, such as timeout and retry are applied over all available methods, to
 * guarantee that the service continue to operate without breaking the core features of the service
 *
 * <p>Cache can be disabled via configuration property {@code cache.service.enabled}, set through
 * application.properties
 *
 * <p>Retention time can also be configured via configuration property {@code
 * cache.service.retention}, set through application.properties
 */
@Singleton
public class RedisCacheService implements CacheService {

  @Inject ReactiveRedisClient redisClient;

  @Inject ObjectMapper objectMapper;

  @Inject Logger logger;

  @ConfigProperty(name = "cache.service.enabled", defaultValue = "true")
  boolean cacheEnabled;

  @ConfigProperty(name = "cache.service.retention", defaultValue = "3600")
  long cacheRetentionTime;

  /**
   * {@inheritDoc}
   *
   * <p>Persist will use the <b>username</b> as cacheId
   *
   * <p>If cache is disabled it will automatically return a {@code Uni} that will resolve to true.
   *
   * <p>All cached entities will have a TTL defined by this class property {@code
   * RedisCacheService#cacheRetentionTime}
   *
   * @param cacheUser non-null user data to be cached
   * @return a {@link Uni} that will resolve into {@code true} if successful, {@code false}
   *     otherwise.
   * @throws javax.validation.ValidationException if argument is null
   * @throws IllegalArgumentException if {@link CacheUserDTO#getUsername()} is blank
   * @throws CacheException if given cacheUser cannot be serialized to be cached
   */
  @Timeout()
  @CircuitBreakerName("user-cache-persist")
  @CircuitBreaker(
      requestVolumeThreshold = 10,
      delay = 500L,
      skipOn = {ValidationException.class, CacheException.class})
  @Retry(
      maxRetries = 5,
      delay = 100,
      abortOn = {ValidationException.class, CacheException.class})
  @FibonacciBackoff
  @Bulkhead()
  @Fallback(fallbackMethod = "persistFallback")
  @Override
  public Uni<Boolean> persist(@NotNull final CacheUserDTO cacheUser) {
    if (!cacheEnabled) {
      logger.warn("Caching is disabled. Ignoring cache request and returning default value true");
      return Uni.createFrom().item(true);
    }

    final var cacheId =
        Optional.ofNullable(cacheUser.getUsername())
            .filter(s -> !s.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("Invalid Id for given cacheUser"));

    try {
      final var valueAsString = objectMapper.writeValueAsString(cacheUser);
      return this.redisClient
          .setex(cacheId, Long.toString(this.cacheRetentionTime), valueAsString)
          .map(Optional::ofNullable)
          .map(Optional::isPresent);
    } catch (JsonProcessingException e) {
      throw new CacheException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>The username is used as cacheId
   *
   * <p>If cache is disabled it will automatically return a {@code Uni} that will resolve {@code
   * Optional.empty()}.
   *
   * @param cacheId non-blank cache identifier
   * @return a {@link Uni} that will resolve into {@code Optional<CacheUserDTO>} if found, {@code
   *     Optional.empty()}} if not found.
   * @throws javax.validation.ValidationException if cacheId is blank
   * @throws CacheException if given cached data cannot be deserialized
   */
  @Timeout()
  @CircuitBreakerName("user-cache-find")
  @CircuitBreaker(
      requestVolumeThreshold = 10,
      delay = 500L,
      skipOn = {ValidationException.class, CacheException.class})
  @Bulkhead()
  @Fallback(fallbackMethod = "findFallback")
  public Uni<Optional<CacheUserDTO>> find(@NotBlank String cacheId) {
    if (!cacheEnabled) {
      logger.warn("Caching is disabled. Ignoring cache request and returning Empty value");
      return findFallback(cacheId);
    }

    return this.redisClient
        .get(cacheId)
        .map(Optional::ofNullable)
        .map(optionalResponse -> optionalResponse.map(this::getCacheUserDTO));
  }

  /**
   * {@inheritDoc}
   *
   * <p>The username is used as cacheId
   *
   * <p>If cache is disabled it will automatically return a {@code Uni} that will resolve to {@code
   * true}.
   *
   * @param cacheId non-blank cache identifier
   * @return a {@link Uni} that will resolve into {@code true} if successful, {@code false}
   *     otherwise.
   * @throws javax.validation.ValidationException if cacheId is blank
   */
  @Timeout()
  @CircuitBreakerName("user-cache-remove")
  @CircuitBreaker(
      requestVolumeThreshold = 10,
      delay = 500L,
      skipOn = {ValidationException.class})
  @Retry(
      maxRetries = 5,
      delay = 100,
      abortOn = {ValidationException.class})
  @FibonacciBackoff
  @Bulkhead()
  @Fallback(fallbackMethod = "removeFallback")
  @Override
  public Uni<Boolean> remove(@NotBlank String cacheId) {
    if (!cacheEnabled) {
      logger.warn("Caching is disabled. Ignoring cache request and returning default value true");
      return Uni.createFrom().item(true);
    }

    return this.redisClient
        .del(List.of(cacheId))
        .map(Optional::ofNullable)
        .map(Optional::isPresent);
  }

  protected Uni<Boolean> removeFallback(String cacheId) {
    logger.warnf("Fallback active while removing data with cacheId %s", cacheId);
    return Uni.createFrom().item(false);
  }

  protected Uni<Boolean> persistFallback(final CacheUserDTO cacheUserDTO) {
    logger.warnf("Fallback active while persisting data %s", cacheUserDTO);
    return Uni.createFrom().item(false);
  }

  protected Uni<Optional<CacheUserDTO>> findFallback(String cacheId) {
    logger.warnf("Fallback active while retrieving data with cacheId %s", cacheId);
    return Uni.createFrom().item(Optional.empty());
  }

  private CacheUserDTO getCacheUserDTO(final Response response) {
    try {
      return objectMapper.readValue(response.toString(), CacheUserDTO.class);
    } catch (JsonProcessingException e) {
      throw new CacheException(e);
    }
  }
}
