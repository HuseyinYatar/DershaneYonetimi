package com.dershane.taskservice.assignment.application.port.out;

import com.dershane.taskservice.assignment.domain.Assignment;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LoadAssignmentPort {

  Optional<Assignment> findById(UUID assignmentId);

  /** Students who already have an assignment of the task for the occurrence. */
  Set<UUID> findStudentIdsWithAssignment(UUID taskId, LocalDate occurrenceDate);

  /** Open ({@code ASSIGNED}, not due yet) assignments of tasks given to the classroom. */
  List<Assignment> findOpenForClassroom(UUID studentId, UUID classroomId, Instant now);

  /**
   * Open assignments inside the reminder window that were handed out before it opened and were not
   * reminded yet; at most {@code limit}, earliest due first.
   */
  List<Assignment> findReminderCandidates(Instant now, Duration leadTime, int limit);

  /** Open assignments whose due time has passed; at most {@code limit}, earliest due first. */
  List<Assignment> findOverdueCandidates(Instant now, int limit);
}
