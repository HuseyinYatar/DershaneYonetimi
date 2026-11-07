/**
 * Tasks (diagram: Gorev): what a teacher gives to a student or a classroom, once, daily or weekly.
 * The assignments handed to each student live in the assignment module, which reacts to {@code
 * TaskCreated}.
 */
@ApplicationModule(allowedDependencies = {"shared", "roster::*"})
package com.dershane.taskservice.task;

import org.springframework.modulith.ApplicationModule;
