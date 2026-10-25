package com.dershane.userservice.subject.application.service;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.subject.application.port.in.LoadSubjectQuery;
import com.dershane.userservice.subject.application.port.out.LoadSubjectPort;
import com.dershane.userservice.subject.domain.Subject;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class SubjectLookup implements LoadSubjectQuery {

  private final LoadSubjectPort subjects;

  @Override
  @Transactional(readOnly = true)
  public Subject require(UUID subjectId) {
    return subjects
        .findById(subjectId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.SUBJECT_NOT_FOUND, "Subject not found", "subjectId", subjectId));
  }

  @Override
  @Transactional(readOnly = true)
  public Subject requireActive(UUID subjectId, String action) {
    Subject subject = require(subjectId);
    subject.requireActive(action);
    return subject;
  }
}
