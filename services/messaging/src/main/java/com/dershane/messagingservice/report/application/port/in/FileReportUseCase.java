package com.dershane.messagingservice.report.application.port.in;

import com.dershane.messagingservice.shared.application.Actor;

public interface FileReportUseCase {

  ReportView file(Actor actor, FileReportCommand command);
}
