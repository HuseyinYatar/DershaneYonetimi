package com.dershane.userservice.user.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TeacherJpaRepository
    extends JpaRepository<TeacherJpaEntity, UUID>, JpaSpecificationExecutor<TeacherJpaEntity> {}
