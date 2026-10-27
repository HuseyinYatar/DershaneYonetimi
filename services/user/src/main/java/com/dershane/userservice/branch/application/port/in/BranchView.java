package com.dershane.userservice.branch.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** Branch data is not personal data. */
public record BranchView(
    UUID id, String name, String code, boolean active, Instant createdAt, Instant updatedAt) {}
