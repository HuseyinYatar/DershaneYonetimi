package com.dershane.taskservice.roster.application.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published in-process when a membership copy is recorded for the first time (not on a repeated
 * event), inside the recording transaction. The assignment module hands the student the open tasks
 * of the classroom. It never leaves the service.
 */
public record MembershipOpened(
    UUID membershipId, UUID studentId, UUID classroomId, UUID branchId, LocalDate startDate) {}
