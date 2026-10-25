package com.dershane.userservice.user.adapter.out.persistence;

import com.dershane.userservice.user.domain.Role;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Polymorphic access: {@code findById} returns a student, teacher or guardian row when it is one.
 */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

  boolean existsByInfoEmail(String email);

  boolean existsByInfoEmailAndIdNot(String email, UUID id);

  boolean existsByUsername(String username);

  Page<UserJpaEntity> findByRole(Role role, Pageable pageable);

  Page<UserJpaEntity> findByRoleAndBranchId(Role role, UUID branchId, Pageable pageable);
}
