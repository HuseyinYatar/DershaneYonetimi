package com.dershane.userservice.user.application.port.in;

import java.util.UUID;

public interface UpdateAdminUseCase {

  UserView update(UUID adminId, UpdateAdminCommand command);
}
