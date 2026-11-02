package com.dershane.attendanceservice.session.application.port.in;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateAdHocSessionCommand(
    UUID branchId,
    UUID classroomId,
    UUID teacherId,
    UUID subjectId,
    LocalDate sessionDate,
    LocalTime startsAt,
    LocalTime endsAt) {}
