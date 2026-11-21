package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TimetableSettingJpaRepository extends JpaRepository<TimetableSettingJpaEntity, UUID> {}
