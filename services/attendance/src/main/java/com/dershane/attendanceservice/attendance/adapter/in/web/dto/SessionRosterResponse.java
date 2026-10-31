package com.dershane.attendanceservice.attendance.adapter.in.web.dto;

import com.dershane.attendanceservice.session.domain.SessionStatus;
import java.util.List;
import java.util.UUID;

public record SessionRosterResponse(
    UUID sessionId, SessionStatus sessionStatus, List<RosterRowResponse> students) {}
