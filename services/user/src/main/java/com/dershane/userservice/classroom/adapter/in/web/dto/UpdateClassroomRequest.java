package com.dershane.userservice.classroom.adapter.in.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Null fields are left unchanged. The grade level cannot change once students are enrolled. */
public record UpdateClassroomRequest(
    @Size(min = 1, max = 60) String name, @Pattern(regexp = ClassroomTerm.PATTERN) String term) {}
