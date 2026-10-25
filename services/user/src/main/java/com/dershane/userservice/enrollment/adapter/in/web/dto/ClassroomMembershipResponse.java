package com.dershane.userservice.enrollment.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Result of assigning a student to a classroom.
 *
 * @param closedMembershipId the membership closed by this move, or null if there was none
 */
public record ClassroomMembershipResponse(
    UUID membershipId,
    UUID studentId,
    UUID classroomId,
    LocalDate startDate,
    LocalDate endDate,
    UUID closedMembershipId) {}
