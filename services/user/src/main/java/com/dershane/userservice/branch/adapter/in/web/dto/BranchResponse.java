package com.dershane.userservice.branch.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Branch data is not personal data. */
public record BranchResponse(
    UUID id, String name, String code, boolean active, Instant createdAt, Instant updatedAt) {}
