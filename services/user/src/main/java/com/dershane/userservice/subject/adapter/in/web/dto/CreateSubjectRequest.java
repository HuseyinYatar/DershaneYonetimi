package com.dershane.userservice.subject.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{2,16}$") String code) {}
