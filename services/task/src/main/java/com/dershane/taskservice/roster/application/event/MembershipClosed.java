package com.dershane.taskservice.roster.application.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published in-process when a membership copy is closed (not on a repeated event), inside the
 * closing transaction. The assignment module cancels the student's open assignments of the
 * classroom. It never leaves the service.
 */
public record MembershipClosed(
    UUID membershipId, UUID studentId, UUID classroomId, LocalDate endDate) {}
