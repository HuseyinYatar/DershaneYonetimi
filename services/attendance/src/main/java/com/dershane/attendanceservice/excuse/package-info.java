/**
 * Excuses (diagram: Mazeret): a guardian reports that a student will miss lessons on given days; an
 * admin approves or rejects it. An approved excuse turns absences on those days into excused ones.
 */
@ApplicationModule(allowedDependencies = {"shared", "roster::*"})
package com.dershane.attendanceservice.excuse;

import org.springframework.modulith.ApplicationModule;
