/**
 * Participants report a message; the administrator of the branch (or a super admin) then reads the
 * whole reported conversation, including deleted content and edit history, and closes the report.
 * Without a report nobody but the participants can read a conversation.
 */
@ApplicationModule(
    allowedDependencies = {
      "shared",
      "conversation::port-in",
      "conversation::domain",
      "directory::port-in"
    })
package com.dershane.messagingservice.report;

import org.springframework.modulith.ApplicationModule;
