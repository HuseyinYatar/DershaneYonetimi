/**
 * Entry point of the Kubernetes CronJobs: the same image started with {@code --task.job=<name>}
 * runs one job and exits. It only calls other modules' use cases.
 */
@ApplicationModule(allowedDependencies = {"shared", "assignment::*"})
package com.dershane.taskservice.job;

import org.springframework.modulith.ApplicationModule;
