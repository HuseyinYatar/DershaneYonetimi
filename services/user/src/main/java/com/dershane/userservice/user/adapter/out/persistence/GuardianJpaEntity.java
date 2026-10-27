package com.dershane.userservice.user.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guardian")
@PrimaryKeyJoinColumn(name = "user_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class GuardianJpaEntity extends UserJpaEntity {}
