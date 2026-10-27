package com.dershane.userservice.subject.application.service;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.subject.application.port.in.CreateSubjectCommand;
import com.dershane.userservice.subject.application.port.in.CreateSubjectUseCase;
import com.dershane.userservice.subject.application.port.in.DeactivateSubjectUseCase;
import com.dershane.userservice.subject.application.port.in.GetSubjectQuery;
import com.dershane.userservice.subject.application.port.in.ListSubjectsQuery;
import com.dershane.userservice.subject.application.port.in.SubjectView;
import com.dershane.userservice.subject.application.port.in.UpdateSubjectCommand;
import com.dershane.userservice.subject.application.port.in.UpdateSubjectUseCase;
import com.dershane.userservice.subject.application.port.out.LoadSubjectPort;
import com.dershane.userservice.subject.application.port.out.SaveSubjectPort;
import com.dershane.userservice.subject.application.port.out.SubjectQueryPort;
import com.dershane.userservice.subject.domain.Subject;
import io.micrometer.observation.annotation.Observed;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subject catalog. Deactivating a subject keeps existing teacher assignments (history) but blocks
 * new ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class SubjectService
    implements CreateSubjectUseCase,
        UpdateSubjectUseCase,
        DeactivateSubjectUseCase,
        GetSubjectQuery,
        ListSubjectsQuery {

  private final LoadSubjectPort loadSubject;
  private final SaveSubjectPort saveSubject;
  private final SubjectQueryPort subjectQuery;
  private final SubjectLookup subjectLookup;

  @Override
  @Transactional
  @Observed(name = "subject.create")
  public SubjectView create(CreateSubjectCommand command) {
    String code = command.code().toUpperCase(Locale.ROOT);
    if (loadSubject.existsByCode(code)) {
      throw Rejections.reject(
          log,
          ErrorCode.SUBJECT_CODE_TAKEN,
          "Subject creation rejected: code in use",
          "code",
          code);
    }
    Subject subject = saveSubject.save(Subject.create(command.name().strip(), code));
    log.atInfo().addKeyValue("subjectId", subject.getId()).log("Subject created");
    return requireView(subject.getId());
  }

  @Override
  @Transactional
  @Observed(name = "subject.update")
  public SubjectView update(UUID subjectId, UpdateSubjectCommand command) {
    Subject subject = subjectLookup.require(subjectId);
    subject.rename(command.name().strip());
    saveSubject.save(subject);
    log.atInfo().addKeyValue("subjectId", subjectId).log("Subject renamed");
    return requireView(subjectId);
  }

  @Override
  @Transactional
  @Observed(name = "subject.deactivate")
  public SubjectView deactivate(UUID subjectId) {
    Subject subject = subjectLookup.require(subjectId);
    if (!subject.deactivate()) {
      log.atInfo().addKeyValue("subjectId", subjectId).log("Subject already inactive; no change");
      return requireView(subjectId);
    }
    saveSubject.save(subject);
    log.atInfo().addKeyValue("subjectId", subjectId).log("Subject deactivated");
    return requireView(subjectId);
  }

  @Override
  @Transactional(readOnly = true)
  public SubjectView get(UUID subjectId) {
    return requireView(subjectId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<SubjectView> list(Boolean active, Pageable pageable) {
    return subjectQuery.findViews(active, pageable);
  }

  private SubjectView requireView(UUID subjectId) {
    return subjectQuery
        .findViewById(subjectId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.SUBJECT_NOT_FOUND, "Subject not found", "subjectId", subjectId));
  }
}
