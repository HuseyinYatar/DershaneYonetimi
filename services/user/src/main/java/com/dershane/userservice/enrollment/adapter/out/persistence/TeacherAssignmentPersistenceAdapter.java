package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.enrollment.application.port.out.LoadTeacherAssignmentPort;
import com.dershane.userservice.enrollment.application.port.out.SaveTeacherAssignmentPort;
import com.dershane.userservice.enrollment.domain.TeacherAssignment;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class TeacherAssignmentPersistenceAdapter
    implements LoadTeacherAssignmentPort, SaveTeacherAssignmentPort {

  private final TeacherAssignmentJpaRepository repository;
  private final EnrollmentPersistenceMapper mapper;

  @Override
  public boolean exists(UUID teacherId, UUID classroomId, UUID subjectId) {
    return repository.existsByTeacherIdAndClassroomIdAndSubjectId(
        teacherId, classroomId, subjectId);
  }

  @Override
  public Optional<TeacherAssignment> findByIdAndTeacherId(UUID assignmentId, UUID teacherId) {
    return repository.findByIdAndTeacherId(assignmentId, teacherId).map(mapper::toDomain);
  }

  /** Assignments are created and deleted, never edited. */
  @Override
  public TeacherAssignment save(TeacherAssignment assignment) {
    return mapper.toDomain(repository.save(mapper.toEntity(assignment)));
  }

  @Override
  public void delete(TeacherAssignment assignment) {
    repository.deleteById(assignment.getId());
  }
}
