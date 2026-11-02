/**
 * Entry point of the Kubernetes CronJobs: the same image started with {@code
 * --attendance.job=<name>} runs one job and exits. It only calls other modules' use cases.
 */
@ApplicationModule(allowedDependencies = {"shared", "schedule::*", "session::*"})
package com.dershane.attendanceservice.job;

import org.springframework.modulith.ApplicationModule;
