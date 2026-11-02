package com.dershane.attendanceservice.schedule.application.port.in;

/**
 * @param created sessions newly created
 * @param alreadyExisting days that already had a session
 * @param skippedConflicts days skipped because the classroom or teacher was busy
 * @param failed days that failed unexpectedly (logged at ERROR)
 */
public record GenerateSessionsResult(
    int created, int alreadyExisting, int skippedConflicts, int failed) {}
