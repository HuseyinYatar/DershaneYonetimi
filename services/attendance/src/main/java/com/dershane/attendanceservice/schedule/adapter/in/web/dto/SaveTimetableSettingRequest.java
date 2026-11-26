package com.dershane.attendanceservice.schedule.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A full replacement (PUT). Ranges are checked by the domain; {@code branchId} defaults to the
 * caller's. {@code lunchLesson} is optional (absent means no lunch break).
 */
public record SaveTimetableSettingRequest(
    UUID branchId,
    @NotNull LocalTime firstLessonStart,
    @NotNull Integer lessonMinutes,
    @NotNull Integer breakMinutes,
    @NotNull Integer lessonCount,
    Integer lunchLesson) {}
