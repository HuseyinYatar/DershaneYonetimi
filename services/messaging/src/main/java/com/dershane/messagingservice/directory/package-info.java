/**
 * Local copy of the students and teachers the user service owns (name, role, branch, active), kept
 * from its {@code UserUpdated} events. Other modules ask it who somebody is through {@code
 * UserDirectoryQuery}; nobody else writes to it.
 */
@ApplicationModule(allowedDependencies = "shared")
package com.dershane.messagingservice.directory;

import org.springframework.modulith.ApplicationModule;
