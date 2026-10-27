package com.dershane.userservice.enrollment.application.port.out;

import com.dershane.userservice.enrollment.domain.TeacherAssignment;
import java.util.Optional;
import java.util.UUID;

public interface LoadTeacherAssignmentPort {

  boolean exists(UUID teacherId, UUID classroomId, UUID subjectId);

  Optional<TeacherAssignment> findByIdAndTeacherId(UUID assignmentId, UUID teacherId);
}
