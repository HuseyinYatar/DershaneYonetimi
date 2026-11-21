package com.dershane.attendanceservice.schedule.application.port.in;

import java.time.LocalTime;
import java.util.UUID;

/** A null {@code branchId} means the caller's own branch. */
public record SaveTimetableSettingCommand(
    UUID branchId,
    LocalTime firstLessonStart,
    int lessonMinutes,
    int breakMinutes,
    int lessonCount,
    int lunchLesson) {}
