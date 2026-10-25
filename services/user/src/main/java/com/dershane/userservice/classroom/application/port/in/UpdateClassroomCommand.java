package com.dershane.userservice.classroom.application.port.in;

/** Null fields are left unchanged. The grade level cannot change. */
public record UpdateClassroomCommand(String name, String term) {}
