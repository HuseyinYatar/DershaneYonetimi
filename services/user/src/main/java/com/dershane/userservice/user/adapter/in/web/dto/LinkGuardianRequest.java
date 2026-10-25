package com.dershane.userservice.user.adapter.in.web.dto;

import com.dershane.userservice.user.domain.Relationship;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkGuardianRequest(@NotNull UUID guardianId, @NotNull Relationship relationship) {}
