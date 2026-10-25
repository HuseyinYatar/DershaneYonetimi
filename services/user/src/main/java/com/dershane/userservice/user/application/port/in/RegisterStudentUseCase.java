package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;

public interface RegisterStudentUseCase {

  /** Admins of the target branch. Opens the student's login account. */
  RegistrationResult<StudentView> register(Actor actor, RegisterStudentCommand command);
}
