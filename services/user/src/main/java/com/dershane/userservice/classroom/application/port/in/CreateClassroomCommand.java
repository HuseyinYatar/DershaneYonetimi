package com.dershane.userservice.classroom.application.port.in;

import java.util.UUID;

public record CreateClassroomCommand(UUID branchId, String name, int gradeLevel, String term) {}
