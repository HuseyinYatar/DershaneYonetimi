package com.dershane.messagingservice.report.application.port.in;

import com.dershane.messagingservice.conversation.application.port.in.AuditMessageView;
import com.dershane.messagingservice.report.domain.ReportStatus;
import com.dershane.messagingservice.shared.application.Actor;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportQuery {

  /** Branch admins see their branch's reports, super admins all of them. */
  Page<ReportView> list(Actor actor, ReportStatus status, Pageable pageable);

  ReportView get(Actor actor, UUID reportId);

  /** The reported conversation, with deleted content and edit history. Every read is logged. */
  List<AuditMessageView> auditMessages(Actor actor, UUID reportId, Long beforeSeq, int limit);
}
