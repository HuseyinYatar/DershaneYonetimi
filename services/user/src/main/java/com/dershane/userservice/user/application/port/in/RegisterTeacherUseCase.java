package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;

public interface RegisterTeacherUseCase {

  /** Admins of the target branch. E-mail is required. */
  RegistrationResult<TeacherView> register(Actor actor, RegisterTeacherCommand command);
}
