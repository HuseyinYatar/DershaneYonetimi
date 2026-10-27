package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface GetStudentQuery {

  /** The student, their branch admins, their teachers and their guardian. */
  StudentView get(Actor actor, UUID studentId);
}
