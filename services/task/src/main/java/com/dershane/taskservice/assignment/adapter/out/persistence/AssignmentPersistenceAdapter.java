package com.dershane.taskservice.assignment.adapter.out.persistence;

import com.dershane.taskservice.assignment.application.port.out.LoadAssignmentPort;
import com.dershane.taskservice.assignment.application.port.out.SaveAssignmentPort;
import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.assignment.domain.Evaluation;
import com.dershane.taskservice.assignment.domain.Submission;
import com.dershane.taskservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.shared.domain.Rejections;
import com.dershane.taskservice.task.domain.TargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Stores the assignment, its submission and its evaluation as three rows. The assignment row's
 * version guards all three, so every save forces a version increment even when only the submission
 * or the evaluation changed.
 */
@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AssignmentPersistenceAdapter implements LoadAssignmentPort, SaveAssignmentPort {

  private final AssignmentJpaRepository assignments;
  private final SubmissionJpaRepository submissions;
  private final EvaluationJpaRepository evaluations;
  private final AssignmentPersistenceMapper mapper;
  private final EntityManager entityManager;

  @Override
  public Optional<Assignment> findById(UUID assignmentId) {
    return assignments.findById(assignmentId).map(this::assemble);
  }

  @Override
  public Set<UUID> findStudentIdsWithAssignment(UUID taskId, LocalDate occurrenceDate) {
    return new HashSet<>(assignments.findStudentIds(taskId, occurrenceDate));
  }

  @Override
  public List<Assignment> findOpenForClassroom(UUID studentId, UUID classroomId, Instant now) {
    return assignments
        .findOpenForTarget(
            studentId, classroomId, TargetType.CLASSROOM, AssignmentStatus.ASSIGNED, now)
        .stream()
        .map(this::assemble)
        .toList();
  }

  @Override
  public List<Assignment> findReminderCandidates(Instant now, Duration leadTime, int limit) {
    return assignments
        .findReminderCandidates(now, now.plus(leadTime), leadTime.toSeconds(), limit)
        .stream()
        .map(this::assemble)
        .toList();
  }

  @Override
  public List<Assignment> findOverdueCandidates(Instant now, int limit) {
    return assignments
        .findDueBefore(AssignmentStatus.ASSIGNED, now, PageRequest.of(0, limit))
        .stream()
        .map(this::assemble)
        .toList();
  }

  @Override
  public Assignment save(Assignment assignment) {
    if (assignment.getVersion() == null) {
      AssignmentJpaEntity created = assignments.saveAndFlush(mapper.toEntity(assignment));
      return mapper.toDomain(created, null, null);
    }
    AssignmentJpaEntity entity =
        assignments
            .findById(assignment.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.ASSIGNMENT_NOT_FOUND,
                        "Assignment to save no longer exists",
                        "assignmentId",
                        assignment.getId()));
    VersionChecks.requireCurrent(
        log, "Assignment", assignment.getId(), assignment.getVersion(), entity.getVersion());
    mapper.update(entity, assignment);
    entityManager.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    SubmissionJpaEntity submission = saveSubmission(assignment);
    saveEvaluation(assignment, submission);
    // The read model uses plain SQL, which does not trigger Hibernate's auto-flush; without this
    // the
    // view built right after a command would show the state before it.
    entityManager.flush();
    return mapper.toDomain(entity, assignment.getSubmission(), assignment.getEvaluation());
  }

  private SubmissionJpaEntity saveSubmission(Assignment assignment) {
    Submission submission = assignment.getSubmission();
    if (submission == null) {
      return null;
    }
    SubmissionJpaEntity entity =
        submissions
            .findByAssignmentId(assignment.getId())
            .orElseGet(
                () -> {
                  SubmissionJpaEntity created = new SubmissionJpaEntity();
                  created.setAssignmentId(assignment.getId());
                  return created;
                });
    mapper.update(entity, submission);
    return submissions.save(entity);
  }

  private void saveEvaluation(Assignment assignment, SubmissionJpaEntity submission) {
    Evaluation evaluation = assignment.getEvaluation();
    if (evaluation == null) {
      return;
    }
    EvaluationJpaEntity entity =
        evaluations
            .findBySubmissionId(submission.getId())
            .orElseGet(
                () -> {
                  EvaluationJpaEntity created = new EvaluationJpaEntity();
                  created.setSubmissionId(submission.getId());
                  return created;
                });
    mapper.update(entity, evaluation);
    evaluations.save(entity);
  }

  private Assignment assemble(AssignmentJpaEntity entity) {
    Optional<SubmissionJpaEntity> submission = submissions.findByAssignmentId(entity.getId());
    Submission submissionDomain = submission.map(mapper::toDomain).orElse(null);
    Evaluation evaluationDomain =
        submission
            .flatMap(s -> evaluations.findBySubmissionId(s.getId()))
            .map(mapper::toDomain)
            .orElse(null);
    return mapper.toDomain(entity, submissionDomain, evaluationDomain);
  }
}
