package com.dershane.taskservice.task.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The texts are masked in {@code toString} like every free-text object. */
public record UpdateTaskRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String description,
    List<UUID> attachmentFileIds) {

  @Override
  public String toString() {
    return "UpdateTaskRequest[attachments="
        + (attachmentFileIds == null ? 0 : attachmentFileIds.size())
        + "]";
  }
}
