package com.dershane.attendanceservice.schedule.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CommitTimetableDraftRequest(@NotNull LocalDate validFrom) {}
