package com.dershane.messagingservice.report.adapter.in.web;

import com.dershane.messagingservice.report.adapter.in.web.dto.AuditMessageResponse;
import com.dershane.messagingservice.report.adapter.in.web.dto.CloseReportRequest;
import com.dershane.messagingservice.report.adapter.in.web.dto.FileReportRequest;
import com.dershane.messagingservice.report.adapter.in.web.dto.ReportResponse;
import com.dershane.messagingservice.report.application.port.in.CloseReportUseCase;
import com.dershane.messagingservice.report.application.port.in.FileReportUseCase;
import com.dershane.messagingservice.report.application.port.in.ReportQuery;
import com.dershane.messagingservice.report.domain.ReportStatus;
import com.dershane.messagingservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.messagingservice.shared.adapter.in.web.CurrentActor;
import com.dershane.messagingservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.messagingservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Message reports")
@RestController
@RequestMapping("/api/v1/message-reports")
@RequiredArgsConstructor
class ReportController {

  private final FileReportUseCase fileReport;
  private final CloseReportUseCase closeReport;
  private final ReportQuery reports;
  private final ReportWebMapper mapper;

  @Operation(summary = "Report a message of a conversation I take part in")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.STUDENT_OR_TEACHER)
  public ResponseEntity<ReportResponse> file(
      @CurrentActor Actor actor, @Valid @RequestBody FileReportRequest request) {
    ReportResponse created = mapper.toResponse(fileReport.file(actor, mapper.toCommand(request)));
    return ResponseEntity.created(URI.create("/api/v1/message-reports/" + created.id()))
        .body(created);
  }

  @Operation(summary = "List reports (branch admin: own branch; super admin: all)")
  @GetMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public PagedModel<ReportResponse> list(
      @CurrentActor Actor actor,
      @RequestParam(required = false) ReportStatus status,
      Pageable pageable) {
    return new PagedModel<>(reports.list(actor, status, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Get a report with the reported message")
  @GetMapping("/{reportId}")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ReportResponse get(@CurrentActor Actor actor, @PathVariable UUID reportId) {
    return mapper.toResponse(reports.get(actor, reportId));
  }

  @Operation(summary = "Read the reported conversation, including deleted and edited content")
  @GetMapping("/{reportId}/messages")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public List<AuditMessageResponse> messages(
      @CurrentActor Actor actor,
      @PathVariable UUID reportId,
      @RequestParam(required = false) Long beforeSeq,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
    return reports.auditMessages(actor, reportId, beforeSeq, limit).stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Operation(summary = "Close a report")
  @PostMapping("/{reportId}/close")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ReportResponse close(
      @CurrentActor Actor actor,
      @PathVariable UUID reportId,
      @Valid @RequestBody(required = false) CloseReportRequest request) {
    return mapper.toResponse(
        closeReport.close(actor, reportId, request == null ? null : request.resolutionNote()));
  }
}
