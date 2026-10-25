package com.dershane.userservice.subject.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record SubjectResponse(
    UUID id, String name, String code, boolean active, Instant createdAt, Instant updatedAt) {}
