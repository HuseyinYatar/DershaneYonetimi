package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import com.dershane.attendanceservice.schedule.application.port.out.BranchScheduleLockPort;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL transaction-level advisory lock keyed by the branch id; released on commit or
 * rollback.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class BranchScheduleLockAdapter implements BranchScheduleLockPort {

  private final ClassScheduleJpaRepository repository;

  @Override
  public void lock(UUID branchId) {
    repository.lockBranch(branchId.toString());
  }
}
