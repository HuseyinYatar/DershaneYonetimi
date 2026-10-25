package com.dershane.userservice.enrollment.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface AssignStudentToClassroomUseCase {

  /**
   * Puts a student into a classroom. If the student is in another classroom, that membership is
   * closed first ({@code StudentRemovedFromClassroom}, then {@code StudentAddedToClassroom}).
   * Repeating the call with the same classroom changes nothing.
   */
  MembershipView assign(Actor actor, UUID studentId, AssignClassroomCommand command);
}
