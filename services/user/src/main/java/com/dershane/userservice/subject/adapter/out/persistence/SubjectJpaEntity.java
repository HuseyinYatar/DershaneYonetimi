package com.dershane.userservice.subject.adapter.out.persistence;

import com.dershane.userservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "subject")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class SubjectJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "code", nullable = false, length = 16, updatable = false)
  private String code;

  @Column(name = "active", nullable = false)
  private boolean active;
}
