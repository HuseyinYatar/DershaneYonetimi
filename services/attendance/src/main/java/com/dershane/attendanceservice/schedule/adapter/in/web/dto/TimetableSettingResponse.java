package com.dershane.attendanceservice.schedule.adapter.in.web.dto;

import java.time.LocalTime;
import java.util.UUID;

public record TimetableSettingResponse(
    UUID branchId,
    LocalTime firstLessonStart,
    int lessonMinutes,
    int breakMinutes,
    int lessonCount,
    int lunchLesson,
    boolean customized) {}
