package com.dershane.userservice.classroom.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateClassroomRequest(
    @NotNull UUID branchId,
    @NotBlank @Size(max = 60) String name,
    @NotNull @Min(1) @Max(13) Integer gradeLevel,
    @NotBlank @Pattern(regexp = ClassroomTerm.PATTERN) String term) {}
