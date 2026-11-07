package com.dershane.taskservice.assignment.adapter.out.persistence;

import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.assignment.domain.Evaluation;
import com.dershane.taskservice.assignment.domain.Submission;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface AssignmentPersistenceMapper {

  AssignmentJpaEntity toEntity(Assignment assignment);

  /** Only the status and the reminder flag change after an assignment is handed out. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "taskId", ignore = true)
  @Mapping(target = "studentId", ignore = true)
  @Mapping(target = "occurrenceDate", ignore = true)
  @Mapping(target = "dueAt", ignore = true)
  @Mapping(target = "assignedAt", ignore = true)
  void update(@MappingTarget AssignmentJpaEntity entity, Assignment assignment);

  Submission toDomain(SubmissionJpaEntity entity);

  Evaluation toDomain(EvaluationJpaEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "assignmentId", ignore = true)
  void update(@MappingTarget SubmissionJpaEntity entity, Submission submission);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "submissionId", ignore = true)
  void update(@MappingTarget EvaluationJpaEntity entity, Evaluation evaluation);

  /** Written by hand: the aggregate is assembled from three rows. */
  default Assignment toDomain(
      AssignmentJpaEntity entity, Submission submission, Evaluation evaluation) {
    return new Assignment(
        entity.getId(),
        entity.getVersion(),
        entity.getTaskId(),
        entity.getStudentId(),
        entity.getOccurrenceDate(),
        entity.getDueAt(),
        entity.getStatus(),
        entity.isReminderSent(),
        entity.getAssignedAt(),
        submission,
        evaluation);
  }

  default UUID[] idsToArray(List<UUID> ids) {
    return ids.toArray(UUID[]::new);
  }

  default List<UUID> arrayToIds(UUID[] ids) {
    return List.of(ids);
  }
}
