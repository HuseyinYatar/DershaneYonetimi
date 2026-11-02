package com.dershane.attendanceservice.excuse.application.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published in-process while an excuse is being approved, inside the approval transaction. The
 * attendance module turns the student's absences in the range into excused ones. It never leaves
 * the service: it is not an integration event and is not sent to SNS.
 */
public record ExcuseApproved(
    UUID excuseId, UUID studentId, LocalDate fromDate, LocalDate toDate, UUID decidedBy) {}
