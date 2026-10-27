package com.dershane.userservice.branch.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBranchRequest(@NotBlank @Size(max = 120) String name) {}
