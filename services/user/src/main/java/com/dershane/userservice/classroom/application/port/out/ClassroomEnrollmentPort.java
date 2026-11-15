package com.dershane.userservice.classroom.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What the classroom module needs to know about who sits in or teaches a classroom. The enrollment
 * module owns that data and implements this port.
 */
public interface ClassroomEnrollmentPort {

  boolean hasActiveStudents(UUID classroomId);

  long countActiveStudents(UUID classroomId);

  /** Active student counts of many classrooms in one query; every given id gets an entry. */
  Map<UUID, Long> countActiveStudents(Collection<UUID> classroomIds);

  boolean isStudentOf(UUID studentId, UUID classroomId);

  boolean isTeacherOf(UUID teacherId, UUID classroomId);
}
