/**
 * Class sessions: one lesson of a classroom on one day, its status and its teacher. Schedules and
 * attendance build on it.
 */
@ApplicationModule(allowedDependencies = {"shared", "roster::*"})
package com.dershane.attendanceservice.session;

import org.springframework.modulith.ApplicationModule;
