package com.dershane.attendanceservice.excuse.application.port.out;

import com.dershane.attendanceservice.excuse.domain.Excuse;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LoadExcusePort {

  Optional<Excuse> findById(UUID excuseId);

  /** Student id to the earliest decided approved excuse covering {@code date}. */
  Map<UUID, UUID> findApprovedCoverage(Collection<UUID> studentIds, LocalDate date);
}
