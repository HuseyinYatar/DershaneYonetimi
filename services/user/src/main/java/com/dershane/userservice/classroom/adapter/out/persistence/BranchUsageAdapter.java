package com.dershane.userservice.classroom.adapter.out.persistence;

import com.dershane.userservice.branch.application.port.out.BranchUsagePort;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Tells the branch module whether a branch still has active classrooms. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class BranchUsageAdapter implements BranchUsagePort {

  private final ClassroomJpaRepository repository;

  @Override
  public boolean hasActiveClassrooms(UUID branchId) {
    return repository.existsByBranchIdAndActiveTrue(branchId);
  }
}
