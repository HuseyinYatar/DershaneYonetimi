package com.dershane.attendanceservice.schedule.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** The new day and hours of a draft lesson; the hours must line up with the branch's grid. */
public record MoveDraftLessonRequest(
    @NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startsAt, @NotNull LocalTime endsAt) {}
