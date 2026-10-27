package com.dershane.userservice.branch.application.port.out;

import java.util.UUID;

/**
 * What the branch module needs to know about other modules before it may be deactivated. The module
 * that owns the data (classroom) implements it.
 */
public interface BranchUsagePort {

  boolean hasActiveClassrooms(UUID branchId);
}
