package com.dershane.messagingservice.report.adapter.in.web;

import com.dershane.messagingservice.conversation.application.port.in.AuditMessageView;
import com.dershane.messagingservice.report.adapter.in.web.dto.AuditMessageResponse;
import com.dershane.messagingservice.report.adapter.in.web.dto.FileReportRequest;
import com.dershane.messagingservice.report.adapter.in.web.dto.ReportResponse;
import com.dershane.messagingservice.report.application.port.in.FileReportCommand;
import com.dershane.messagingservice.report.application.port.in.ReportView;
import org.mapstruct.Mapper;

@Mapper
interface ReportWebMapper {

  FileReportCommand toCommand(FileReportRequest request);

  ReportResponse toResponse(ReportView view);

  AuditMessageResponse toResponse(AuditMessageView view);
}
