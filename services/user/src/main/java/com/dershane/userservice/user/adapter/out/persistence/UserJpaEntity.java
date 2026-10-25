package com.dershane.userservice.user.adapter.out.persistence;

import com.dershane.userservice.shared.adapter.out.persistence.AuditableJpaEntity;
import com.dershane.userservice.user.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Common user columns. Students, teachers and guardians have their own tables joined on {@code
 * user_id}; an admin is a plain row of this entity with role ADMIN.
 *
 * <p>{@code toString} prints only the id: names and contact details are personal data.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class UserJpaEntity extends AuditableJpaEntity {

  /** Keycloak {@code sub}; assigned, not generated. */
  @Id @ToString.Include private UUID id;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, updatable = false, length = 16)
  private Role role;

  @Embedded private PersonalInfoJpa info;

  @Column(name = "username", nullable = false, updatable = false, length = 100)
  private String username;

  @Column(name = "active", nullable = false)
  private boolean active;
}
