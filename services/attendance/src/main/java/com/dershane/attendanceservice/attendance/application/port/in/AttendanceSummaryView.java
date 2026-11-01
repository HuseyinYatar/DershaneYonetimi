package com.dershane.attendanceservice.attendance.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/** Counts of one student's records between two dates (null date = open end). */
public record AttendanceSummaryView(
    UUID studentId,
    LocalDate from,
    LocalDate to,
    long present,
    long absent,
    long late,
    long excused,
    long total) {}
