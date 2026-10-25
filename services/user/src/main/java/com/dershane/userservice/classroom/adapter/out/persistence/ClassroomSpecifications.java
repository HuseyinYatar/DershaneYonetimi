package com.dershane.userservice.classroom.adapter.out.persistence;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class ClassroomSpecifications {

  private ClassroomSpecifications() {}

  /** Null filters are not applied. */
  static Specification<ClassroomJpaEntity> matching(UUID branchId, String term, Boolean active) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (branchId != null) {
        predicates.add(cb.equal(root.get("branchId"), branchId));
      }
      if (term != null) {
        predicates.add(cb.equal(root.get("term"), term));
      }
      if (active != null) {
        predicates.add(cb.equal(root.get("active"), active));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
