package com.dershane.taskservice.roster.application.port.in;

import java.util.UUID;

/** For other modules: whether a guardian may act for a student. */
public interface GuardianAccessQuery {

  boolean isGuardianOf(UUID guardianId, UUID studentId);
}
