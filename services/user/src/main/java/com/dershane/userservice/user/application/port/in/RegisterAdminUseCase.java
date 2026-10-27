package com.dershane.userservice.user.application.port.in;

/** Branch admins are managed by super admins only (checked at the entry point). */
public interface RegisterAdminUseCase {

  RegistrationResult<UserView> register(RegisterAdminCommand command);
}
