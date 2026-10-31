package com.dershane.attendanceservice.job.adapter.in.job;

import com.dershane.attendanceservice.schedule.application.port.in.GenerateSessionsResult;
import com.dershane.attendanceservice.schedule.application.port.in.GenerateSessionsUseCase;
import com.dershane.attendanceservice.session.application.port.in.ExpireSessionsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the job named by {@code attendance.job} once, then the application exits (see {@code
 * AttendanceServiceApplication}). A failing job ends the process with a non-zero exit code, so the
 * CronJob run shows as failed.
 */
@Slf4j
@Component
@ConditionalOnProperty("attendance.job")
class ScheduledJobRunner implements ApplicationRunner {

  static final String GENERATE_SESSIONS = "generate-sessions";
  static final String EXPIRE_SESSIONS = "expire-sessions";

  private final GenerateSessionsUseCase generateSessions;
  private final ExpireSessionsUseCase expireSessions;
  private final String job;
  private final int horizonDays;

  ScheduledJobRunner(
      GenerateSessionsUseCase generateSessions,
      ExpireSessionsUseCase expireSessions,
      @Value("${attendance.job}") String job,
      @Value("${attendance.schedule-horizon-days:7}") int horizonDays) {
    this.generateSessions = generateSessions;
    this.expireSessions = expireSessions;
    this.job = job;
    this.horizonDays = horizonDays;
  }

  @Override
  public void run(ApplicationArguments args) {
    log.atInfo().addKeyValue("job", job).log("Job started");
    switch (job) {
      case GENERATE_SESSIONS -> {
        GenerateSessionsResult result = generateSessions.generate(horizonDays);
        if (result.failed() > 0) {
          throw new IllegalStateException(
              "Session generation failed for " + result.failed() + " day(s); see the ERROR logs");
        }
      }
      case EXPIRE_SESSIONS -> expireSessions.expireElapsed();
      default ->
          throw new IllegalArgumentException(
              "Unknown job '"
                  + job
                  + "'; expected "
                  + GENERATE_SESSIONS
                  + " or "
                  + EXPIRE_SESSIONS);
    }
    log.atInfo().addKeyValue("job", job).log("Job finished");
  }
}
