package com.dershane.taskservice.roster.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CloseMembershipRequest(@NotNull LocalDate endDate) {}
