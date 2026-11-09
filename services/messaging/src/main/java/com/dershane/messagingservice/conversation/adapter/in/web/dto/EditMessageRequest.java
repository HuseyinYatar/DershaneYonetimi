package com.dershane.messagingservice.conversation.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Free text: masked in {@code toString}. */
public record EditMessageRequest(@NotNull @Size(max = 4000) String content) {

  @Override
  public String toString() {
    return "EditMessageRequest[]";
  }
}
