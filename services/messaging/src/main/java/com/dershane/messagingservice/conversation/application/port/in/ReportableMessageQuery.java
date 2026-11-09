package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.shared.application.Actor;
import java.util.UUID;

public interface ReportableMessageQuery {

  /** The message, if the caller takes part in its conversation. */
  ReportableMessage findForReport(Actor actor, UUID messageId);
}
