package com.dershane.messagingservice.conversation.application.port.out;

public interface RealtimePublisherPort {

  /** Never throws: delivery is best-effort and a failure must not undo a stored message. */
  void publish(RealtimeEvent event);
}
