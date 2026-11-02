package com.dershane.attendanceservice.session.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;

public interface CreateAdHocSessionUseCase {

  /** A session outside any weekly schedule (make-up lesson, study hour); admins of the branch. */
  SessionView create(Actor actor, CreateAdHocSessionCommand command);
}
