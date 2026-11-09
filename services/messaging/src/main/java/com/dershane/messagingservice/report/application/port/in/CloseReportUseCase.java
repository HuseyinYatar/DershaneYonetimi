package com.dershane.messagingservice.report.application.port.in;

import com.dershane.messagingservice.shared.application.Actor;
import java.util.UUID;

public interface CloseReportUseCase {

  ReportView close(Actor actor, UUID reportId, String resolutionNote);
}
