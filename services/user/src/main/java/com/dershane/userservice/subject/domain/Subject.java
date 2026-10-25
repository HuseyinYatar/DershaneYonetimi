package com.dershane.userservice.subject.domain;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Institution-wide course catalog entry (diagram: Ders), e.g. Matematik / MAT. */
@Slf4j
@Getter
public class Subject {

  /** Null until the subject is saved for the first time. */
  private final UUID id;

  /** Version the subject was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private String name;
  private final String code;
  private boolean active;

  /** Rehydrates a saved subject. Use {@link #create} for a new one. */
  public Subject(UUID id, Long version, String name, String code, boolean active) {
    this.id = id;
    this.version = version;
    this.name = name;
    this.code = code;
    this.active = active;
  }

  public static Subject create(String name, String code) {
    return new Subject(null, null, name, code, true);
  }

  public void rename(String newName) {
    this.name = newName;
  }

  /** Returns false when the subject was already inactive. */
  public boolean deactivate() {
    if (!active) {
      return false;
    }
    this.active = false;
    return true;
  }

  public void requireActive(String action) {
    if (!active) {
      throw Rejections.reject(
          log,
          ErrorCode.SUBJECT_INACTIVE,
          "Operation rejected: subject is inactive",
          "subjectId",
          id,
          "action",
          action);
    }
  }
}
