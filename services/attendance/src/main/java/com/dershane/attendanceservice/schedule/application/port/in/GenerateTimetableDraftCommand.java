package com.dershane.attendanceservice.schedule.application.port.in;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

/** {@code branchId} defaults to the caller's; {@code days} to Monday–Friday. */
public record GenerateTimetableDraftCommand(UUID branchId, List<DayOfWeek> days) {}
