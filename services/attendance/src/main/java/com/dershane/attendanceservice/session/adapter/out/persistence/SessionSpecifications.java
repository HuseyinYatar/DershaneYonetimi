package com.dershane.attendanceservice.session.adapter.out.persistence;

import com.dershane.attendanceservice.session.domain.SessionStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class SessionSpecifications {

  private SessionSpecifications() {}

  /** Null filters are not applied; {@code from} and {@code to} are inclusive. */
  static Specification<ClassSessionJpaEntity> matching(
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      LocalDate from,
      LocalDate to,
      SessionStatus status) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (branchId != null) {
        predicates.add(cb.equal(root.get("branchId"), branchId));
      }
      if (classroomId != null) {
        predicates.add(cb.equal(root.get("classroomId"), classroomId));
      }
      if (teacherId != null) {
        predicates.add(cb.equal(root.get("teacherId"), teacherId));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("sessionDate"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("sessionDate"), to));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
