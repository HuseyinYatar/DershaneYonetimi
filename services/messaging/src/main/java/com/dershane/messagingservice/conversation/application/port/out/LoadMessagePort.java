package com.dershane.messagingservice.conversation.application.port.out;

import com.dershane.messagingservice.conversation.domain.Message;
import java.util.Optional;
import java.util.UUID;

public interface LoadMessagePort {

  Optional<Message> findById(UUID messageId);
}
