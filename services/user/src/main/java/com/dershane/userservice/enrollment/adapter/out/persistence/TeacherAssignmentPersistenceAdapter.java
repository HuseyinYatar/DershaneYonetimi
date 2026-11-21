package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.enrollment.application.port.out.LoadTeacherAssignmentPort;
import com.dershane.userservice.enrollment.application.port.out.SaveTeacherAssignmentPort;
import com.dershane.userservice.enrollment.domain.TeacherAssignment;
import com.dershane.userservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
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

  /** A new assignment is inserted; a saved one only has its weekly hours updated. */
  @Override
  public TeacherAssignment save(TeacherAssignment assignment) {
    if (assignment.getVersion() == null) {
      return mapper.toDomain(repository.save(mapper.toEntity(assignment)));
    }
    TeacherAssignmentJpaEntity entity =
        repository
            .findById(assignment.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.TEACHER_ASSIGNMENT_NOT_FOUND,
                        "Assignment to save no longer exists",
                        "assignmentId",
                        assignment.getId()));
    VersionChecks.requireCurrent(
        log, "TeacherAssignment", assignment.getId(), assignment.getVersion(), entity.getVersion());
    mapper.update(entity, assignment);
    return mapper.toDomain(entity);
  }

  @Override
  public void delete(TeacherAssignment assignment) {
    repository.deleteById(assignment.getId());
  }
}
