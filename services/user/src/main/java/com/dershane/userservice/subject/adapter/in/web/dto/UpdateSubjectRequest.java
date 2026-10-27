package com.dershane.userservice.subject.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSubjectRequest(@NotBlank @Size(max = 120) String name) {}
