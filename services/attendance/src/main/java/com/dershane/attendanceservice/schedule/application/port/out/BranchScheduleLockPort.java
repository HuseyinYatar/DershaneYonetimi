package com.dershane.attendanceservice.schedule.application.port.out;

import java.util.UUID;

/**
 * Serializes the writes that decide whether a branch's timetable is clash-free (creating or
 * changing a schedule, saving a draft, generating one). Held until the current transaction ends;
 * asking again in the same transaction is free.
 */
public interface BranchScheduleLockPort {

  void lock(UUID branchId);
}
