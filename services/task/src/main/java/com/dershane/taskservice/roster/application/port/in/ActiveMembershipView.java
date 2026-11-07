package com.dershane.taskservice.roster.application.port.in;

import java.util.UUID;

/** The classroom a student sits in on a date and the branch it belongs to. */
public record ActiveMembershipView(UUID classroomId, UUID branchId) {}
