/**
 * One-to-one conversations between a student and a teacher of the same branch: opening, sending,
 * editing and deleting messages, read positions, and the sync queries a client uses to catch up.
 * The database is the source of truth; live delivery is a best-effort push by the realtime module.
 */
@ApplicationModule(allowedDependencies = {"shared", "directory::port-in"})
package com.dershane.messagingservice.conversation;

import org.springframework.modulith.ApplicationModule;
