package com.dershane.attendanceservice.schedule.application.port.in;

import java.time.LocalTime;
import java.util.UUID;

/** {@code customized} is false when the branch never saved one and the defaults are returned. */
public record TimetableSettingView(
    UUID branchId,
    LocalTime firstLessonStart,
    int lessonMinutes,
    int breakMinutes,
    int lessonCount,
    int lunchLesson,
    boolean customized) {}
