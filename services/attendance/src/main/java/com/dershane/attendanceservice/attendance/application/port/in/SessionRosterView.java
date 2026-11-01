package com.dershane.attendanceservice.attendance.application.port.in;

import com.dershane.attendanceservice.session.domain.SessionStatus;
import java.util.List;
import java.util.UUID;

public record SessionRosterView(
    UUID sessionId, SessionStatus sessionStatus, List<RosterRowView> students) {}
