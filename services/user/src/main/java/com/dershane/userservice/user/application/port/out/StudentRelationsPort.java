package com.dershane.userservice.user.application.port.out;

import java.util.UUID;

/**
 * What the user module needs to know about a student's classroom. The enrollment module owns that
 * data and implements this port.
 */
public interface StudentRelationsPort {

  /** True if the teacher teaches the student's current classroom. */
  boolean teachesStudent(UUID teacherId, UUID studentId);

  boolean hasActiveClassroom(UUID studentId);
}
