/**
 * Who sits in and who teaches a classroom: student classroom memberships and teacher assignments.
 * It sits above user, classroom and subject, and implements the ports they define to learn about
 * enrollment data.
 */
@ApplicationModule(allowedDependencies = {"shared", "user::*", "classroom::*", "subject::*"})
package com.dershane.userservice.enrollment;

import org.springframework.modulith.ApplicationModule;
