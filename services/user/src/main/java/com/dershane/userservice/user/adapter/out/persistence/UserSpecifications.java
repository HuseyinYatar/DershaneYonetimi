package com.dershane.userservice.user.adapter.out.persistence;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** List filters for students, teachers and guardians. Null filters are not applied. */
final class UserSpecifications {

  private UserSpecifications() {}

  static <T extends UserJpaEntity> Specification<T> inBranch(UUID branchId, Boolean active) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (branchId != null) {
        predicates.add(cb.equal(root.get("branchId"), branchId));
      }
      if (active != null) {
        predicates.add(cb.equal(root.get("active"), active));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
