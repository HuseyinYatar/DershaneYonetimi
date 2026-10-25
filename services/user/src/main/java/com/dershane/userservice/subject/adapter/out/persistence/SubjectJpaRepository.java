package com.dershane.userservice.subject.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SubjectJpaRepository extends JpaRepository<SubjectJpaEntity, UUID> {

  boolean existsByCode(String code);

  Page<SubjectJpaEntity> findByActive(boolean active, Pageable pageable);
}
