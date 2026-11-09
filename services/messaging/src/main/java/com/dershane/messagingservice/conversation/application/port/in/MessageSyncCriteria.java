package com.dershane.messagingservice.conversation.application.port.in;

import java.time.Instant;

/**
 * Which messages a client wants. At most one of {@code beforeSeq} (scrolling up) and {@code
 * afterSeq} (catching up) is set; with neither, the latest messages are returned.
 *
 * @param updatedSince with {@code afterSeq}: also return older messages edited or deleted after
 *     this instant
 */
public record MessageSyncCriteria(Long beforeSeq, Long afterSeq, Instant updatedSince, int limit) {}
