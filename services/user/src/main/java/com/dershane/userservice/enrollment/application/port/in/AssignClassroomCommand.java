package com.dershane.userservice.enrollment.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/** {@code startDate} defaults to today (Europe/Istanbul). */
public record AssignClassroomCommand(UUID classroomId, LocalDate startDate) {}
