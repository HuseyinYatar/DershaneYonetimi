package com.dershane.taskservice.assignment.application.service;

import com.dershane.taskservice.assignment.application.port.in.ProcessDeadlinesUseCase;
import com.dershane.taskservice.shared.application.TaskProperties;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Not transactional on purpose: every batch commits on its own. A batch that fails stops the job
 * with an exception (the CronJob run shows as failed and the next run starts again).
 */
@Slf4j
@Service
@RequiredArgsConstructor
class DeadlineService implements ProcessDeadlinesUseCase {

  static final int BATCH_SIZE = 500;

  private final DeadlineBatch batch;
  private final TaskProperties properties;

  @Override
  @Observed(name = "assignment.process-deadlines")
  public DeadlineResult processDeadlines() {
    int reminded = 0;
    int batchCount;
    do {
      batchCount = batch.remind(properties.reminder().leadTime(), BATCH_SIZE);
      reminded += batchCount;
    } while (batchCount == BATCH_SIZE);
    int overdue = 0;
    do {
      batchCount = batch.markOverdue(BATCH_SIZE);
      overdue += batchCount;
    } while (batchCount == BATCH_SIZE);
    if (reminded == 0 && overdue == 0) {
      log.debug("No reminders due and no assignments overdue");
    } else {
      log.atInfo()
          .addKeyValue("reminded", reminded)
          .addKeyValue("overdue", overdue)
          .log("Deadlines processed");
    }
    return new DeadlineResult(reminded, overdue);
  }
}
