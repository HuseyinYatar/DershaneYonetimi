package com.dershane.attendanceservice.roster.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GuardianLinkRequest(@NotNull UUID guardianId) {}
