package com.dershane.taskservice.job.adapter.in.job;

import com.dershane.taskservice.assignment.application.port.in.GenerateAssignmentsResult;
import com.dershane.taskservice.assignment.application.port.in.GenerateTodayAssignmentsUseCase;
import com.dershane.taskservice.assignment.application.port.in.ProcessDeadlinesUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the job named by {@code task.job} once, then the application exits (see {@code
 * TaskServiceApplication}). A failing job ends the process with a non-zero exit code, so the
 * CronJob run shows as failed.
 */
@Slf4j
@Component
@ConditionalOnProperty("task.job")
class ScheduledJobRunner implements ApplicationRunner {

  static final String GENERATE_ASSIGNMENTS = "generate-assignments";
  static final String CHECK_DEADLINES = "check-deadlines";

  private final GenerateTodayAssignmentsUseCase generateAssignments;
  private final ProcessDeadlinesUseCase processDeadlines;
  private final String job;

  ScheduledJobRunner(
      GenerateTodayAssignmentsUseCase generateAssignments,
      ProcessDeadlinesUseCase processDeadlines,
      @Value("${task.job}") String job) {
    this.generateAssignments = generateAssignments;
    this.processDeadlines = processDeadlines;
    this.job = job;
  }

  @Override
  public void run(ApplicationArguments args) {
    log.atInfo().addKeyValue("job", job).log("Job started");
    switch (job) {
      case GENERATE_ASSIGNMENTS -> {
        GenerateAssignmentsResult result = generateAssignments.generateToday();
        if (result.failed() > 0) {
          throw new IllegalStateException(
              "Assignment generation failed for "
                  + result.failed()
                  + " task(s); see the ERROR logs");
        }
      }
      case CHECK_DEADLINES -> processDeadlines.processDeadlines();
      default ->
          throw new IllegalArgumentException(
              "Unknown job '"
                  + job
                  + "'; expected "
                  + GENERATE_ASSIGNMENTS
                  + " or "
                  + CHECK_DEADLINES);
    }
    log.atInfo().addKeyValue("job", job).log("Job finished");
  }
}
