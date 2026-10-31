package com.dershane.attendanceservice.attendance.application.port.out;

import java.time.LocalDate;
import java.util.UUID;

/** Which records a read query returns. Null fields are not applied; the dates are inclusive. */
public record RecordFilter(
    UUID branchId,
    UUID teacherId,
    UUID studentId,
    UUID classroomId,
    LocalDate from,
    LocalDate to) {}
