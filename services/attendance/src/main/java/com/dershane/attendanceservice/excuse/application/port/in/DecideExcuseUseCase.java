package com.dershane.attendanceservice.excuse.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

/** Admins of the excuse's branch. A decided excuse cannot be decided again. */
public interface DecideExcuseUseCase {

  /** Approval turns the student's absences in the range into excused ones (same transaction). */
  ExcuseView approve(Actor actor, UUID excuseId, String note);

  ExcuseView reject(Actor actor, UUID excuseId, String note);
}
