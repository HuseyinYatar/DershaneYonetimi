package com.dershane.attendanceservice.roster.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CloseMembershipRequest(@NotNull LocalDate endDate) {}
