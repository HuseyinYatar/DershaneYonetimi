package com.dershane.taskservice.assignment.application.port.out;

import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Which assignments a read query returns. Null fields are not applied; the dates bound the
 * occurrence date and are inclusive. {@code teacherId} and {@code branchId} are the task's.
 */
public record AssignmentFilter(
    UUID studentId,
    UUID teacherId,
    UUID branchId,
    UUID taskId,
    AssignmentStatus status,
    LocalDate from,
    LocalDate to) {}
