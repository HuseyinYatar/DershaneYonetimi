package com.dershane.taskservice.assignment.application.port.in;

/**
 * @param tasks tasks that occurred today
 * @param created assignments newly handed out
 * @param failed tasks whose generation failed unexpectedly (logged at ERROR)
 */
public record GenerateAssignmentsResult(int tasks, int created, int failed) {}
