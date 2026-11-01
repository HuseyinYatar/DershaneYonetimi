package com.dershane.attendanceservice.attendance.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AttendanceSummaryResponse(
    UUID studentId,
    LocalDate from,
    LocalDate to,
    long present,
    long absent,
    long late,
    long excused,
    long total) {}
