/**
 * Assignments (diagram: GorevAtamasi, Teslim, Degerlendirme): the task handed to each student, the
 * student's submission and the teacher's evaluation. Hands out assignments when a task is created,
 * when the CronJob runs and when a student joins a classroom; cancels them when a student leaves;
 * reminds and marks overdue ones; announces all of it to the notification service.
 */
@ApplicationModule(allowedDependencies = {"shared", "roster::*", "task::*"})
package com.dershane.taskservice.assignment;

import org.springframework.modulith.ApplicationModule;
