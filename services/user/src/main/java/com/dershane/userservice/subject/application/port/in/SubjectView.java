package com.dershane.userservice.subject.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record SubjectView(
    UUID id, String name, String code, boolean active, Instant createdAt, Instant updatedAt) {}
