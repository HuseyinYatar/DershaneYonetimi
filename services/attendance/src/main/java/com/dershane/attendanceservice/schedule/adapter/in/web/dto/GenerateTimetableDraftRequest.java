package com.dershane.attendanceservice.schedule.adapter.in.web.dto;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

/** Both fields are optional: the caller's branch, and Monday–Friday. */
public record GenerateTimetableDraftRequest(UUID branchId, List<DayOfWeek> days) {}
