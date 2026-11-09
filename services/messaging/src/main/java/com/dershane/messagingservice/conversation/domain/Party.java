package com.dershane.messagingservice.conversation.domain;

import java.util.UUID;

/** One side of a prospective conversation. */
public record Party(UUID id, PartyRole role, UUID branchId, boolean active) {}
