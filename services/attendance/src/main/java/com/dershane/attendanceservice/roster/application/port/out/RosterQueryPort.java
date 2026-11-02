package com.dershane.attendanceservice.roster.application.port.out;

import com.dershane.attendanceservice.roster.application.port.in.RosterEntryView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RosterQueryPort {

  List<RosterEntryView> findRosterOn(UUID classroomId, LocalDate date);

  boolean existsMembershipOn(UUID classroomId, UUID studentId, LocalDate date);
}
