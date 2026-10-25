package com.dershane.userservice.enrollment.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Result of putting a student into, or removing them from, a classroom.
 *
 * @param closedMembershipId the membership closed by this call, or null if there was none
 */
public record MembershipView(
    UUID membershipId,
    UUID studentId,
    UUID classroomId,
    LocalDate startDate,
    LocalDate endDate,
    UUID closedMembershipId) {}
