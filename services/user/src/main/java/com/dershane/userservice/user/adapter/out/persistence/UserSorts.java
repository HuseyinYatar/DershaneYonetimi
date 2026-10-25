package com.dershane.userservice.user.adapter.out.persistence;

import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Clients sort user lists by {@code firstName} or {@code lastName}; in the entity those columns sit
 * in the embedded {@code info}. This keeps the API's sort parameters unchanged.
 */
final class UserSorts {

  private static final Map<String, String> ENTITY_PATHS =
      Map.of(
          "firstName", "info.firstName",
          "lastName", "info.lastName",
          "email", "info.email",
          "phone", "info.phone");

  private UserSorts() {}

  static Pageable toEntityPaths(Pageable pageable) {
    if (pageable.isUnpaged() || pageable.getSort().isUnsorted()) {
      return pageable;
    }
    Sort sort =
        Sort.by(
            pageable.getSort().stream()
                .map(
                    order ->
                        order.withProperty(
                            ENTITY_PATHS.getOrDefault(order.getProperty(), order.getProperty())))
                .toList());
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
  }
}
