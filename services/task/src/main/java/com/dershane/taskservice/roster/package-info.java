/**
 * Local copies of data the user service owns: who sits in which classroom on which dates, and which
 * guardian a student has. Announces openings and closings to other modules with in-process events
 * ({@code MembershipOpened}, {@code MembershipClosed}). Filled from the user service's events
 * (SQS), with an admin REST adapter for manual repairs.
 */
@ApplicationModule(allowedDependencies = "shared")
package com.dershane.taskservice.roster;

import org.springframework.modulith.ApplicationModule;
