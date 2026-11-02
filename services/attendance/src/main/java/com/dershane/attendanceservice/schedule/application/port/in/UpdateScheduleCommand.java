package com.dershane.attendanceservice.schedule.application.port.in;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Replaces every changeable field; branch and classroom stay. */
public record UpdateScheduleCommand(
    UUID teacherId,
    UUID subjectId,
    DayOfWeek dayOfWeek,
    LocalTime startsAt,
    LocalTime endsAt,
    LocalDate validFrom,
    LocalDate validTo) {}
