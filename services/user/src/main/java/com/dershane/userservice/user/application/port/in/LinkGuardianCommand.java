package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.user.domain.Relationship;
import java.util.UUID;

public record LinkGuardianCommand(UUID guardianId, Relationship relationship) {}
