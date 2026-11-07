package com.dershane.taskservice.assignment.application.port.in;

import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.shared.application.Actor;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListAssignmentsQuery {

  /**
   * Students list their own, guardians their child's ({@code studentId} is required), teachers the
   * assignments of their tasks, admins those of their branch. Null filters are not applied; the
   * dates bound the occurrence date and are inclusive.
   */
  Page<AssignmentSummaryView> list(
      Actor actor,
      UUID studentId,
      AssignmentStatus status,
      LocalDate from,
      LocalDate to,
      Pageable pageable);

  /** The assignments of one task, for its teacher or a branch admin. */
  Page<AssignmentSummaryView> listOfTask(
      Actor actor,
      UUID taskId,
      LocalDate occurrenceDate,
      AssignmentStatus status,
      Pageable pageable);
}
