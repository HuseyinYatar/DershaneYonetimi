package com.dershane.attendanceservice.attendance.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AttendanceRecordChangeJpaRepository
    extends JpaRepository<AttendanceRecordChangeJpaEntity, UUID> {}
