/**
 * Local copies of data the user service owns: who sits in which classroom on which dates, and which
 * guardian a student has. Filled from the user service's events (SQS), with an admin REST adapter
 * for manual repairs.
 */
@ApplicationModule(allowedDependencies = "shared")
package com.dershane.attendanceservice.roster;

import org.springframework.modulith.ApplicationModule;
