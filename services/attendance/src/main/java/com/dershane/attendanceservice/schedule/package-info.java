/**
 * Weekly class schedules (diagram: DersProgrami) and the generation of their sessions for the
 * coming days.
 */
@ApplicationModule(allowedDependencies = {"shared", "session::*", "roster::*"})
package com.dershane.attendanceservice.schedule;

import org.springframework.modulith.ApplicationModule;
