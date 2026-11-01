/**
 * Local copies of data the user service owns: who sits in which classroom on which dates, and which
 * guardian a student has. Filled from the user service's events (a temporary REST adapter until the
 * SQS consumer exists).
 */
@ApplicationModule(allowedDependencies = "shared")
package com.dershane.attendanceservice.roster;

import org.springframework.modulith.ApplicationModule;
