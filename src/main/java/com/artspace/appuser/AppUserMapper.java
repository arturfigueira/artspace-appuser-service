package com.artspace.appuser;

import com.artspace.appuser.cache.CacheUserDTO;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
interface AppUserMapper {

  @Mapping(source = "creationDate", target = "createdDate")
  CacheUserDTO toCache(final AppUser entity);

  /**
   * FIXME: Using straight setter added due issues with MapInstruct which can access protected setters;
   */
  default AppUser toEntity(final CacheUserDTO dto){
    final var appUser = new AppUser();
    appUser.setEmail(dto.getEmail());
    appUser.setUsername(dto.getUsername());
    appUser.setBiography(dto.getBiography());
    appUser.setFirstName(dto.getFirstName());
    appUser.setLastName(dto.getLastName());
    appUser.setId(Long.parseLong(dto.getId()));
    appUser.setCreationDate(fromMillis(dto.getCreatedDate()));
    return appUser;
  }

  default Long toMillis(final Instant instant) {
    return instant == null ? null : instant.toEpochMilli();
  }

  default Instant fromMillis(final Long aLong) {
    return aLong == null ? null : Instant.ofEpochMilli(aLong);
  }
}
