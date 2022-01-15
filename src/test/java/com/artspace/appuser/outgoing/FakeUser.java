package com.artspace.appuser.outgoing;

import com.artspace.appuser.AppUser;

public class FakeUser extends AppUser {
  public static FakeUser from(final AppUserDTO dto){
    final var fakeUser = new FakeUser();
    fakeUser.setEmail("");
    fakeUser.setUsername(dto.getUsername());
    fakeUser.setFirstName(dto.getFirstName());
    fakeUser.setLastName(dto.getLastName());
    fakeUser.setActive(dto.isActive());
    return fakeUser;
  }
}
