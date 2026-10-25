package com.dershane.userservice.user.adapter.out.persistence;

import com.dershane.userservice.user.application.port.in.GuardianView;
import com.dershane.userservice.user.application.port.in.StudentView;
import com.dershane.userservice.user.application.port.in.TeacherView;
import com.dershane.userservice.user.application.port.in.UserView;
import com.dershane.userservice.user.domain.Guardian;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.Teacher;
import com.dershane.userservice.user.domain.User;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.SubclassMapping;

/**
 * User hierarchy: domain objects (rehydrated through their constructors) and JPA entities (JOINED
 * inheritance). A plain {@code User} is an admin.
 */
@Mapper
interface UserPersistenceMapper {

  @SubclassMapping(source = StudentJpaEntity.class, target = Student.class)
  @SubclassMapping(source = TeacherJpaEntity.class, target = Teacher.class)
  @SubclassMapping(source = GuardianJpaEntity.class, target = Guardian.class)
  User toDomain(UserJpaEntity entity);

  @SubclassMapping(source = Student.class, target = StudentJpaEntity.class)
  @SubclassMapping(source = Teacher.class, target = TeacherJpaEntity.class)
  @SubclassMapping(source = Guardian.class, target = GuardianJpaEntity.class)
  UserJpaEntity toEntity(User user);

  /** Written by hand: MapStruct would read {@code PersonalInfo.merge} as a fluent setter. */
  default PersonalInfo toInfo(PersonalInfoJpa jpa) {
    return jpa == null
        ? null
        : new PersonalInfo(jpa.getFirstName(), jpa.getLastName(), jpa.getEmail(), jpa.getPhone());
  }

  /** Copies the changeable fields of the domain object onto the loaded entity. */
  default void update(UserJpaEntity entity, User user) {
    if (entity instanceof StudentJpaEntity student && user instanceof Student domain) {
      updateStudent(student, domain);
    } else if (entity instanceof TeacherJpaEntity teacher && user instanceof Teacher domain) {
      updateTeacher(teacher, domain);
    } else if (entity instanceof GuardianJpaEntity guardian && user instanceof Guardian domain) {
      updateGuardian(guardian, domain);
    } else {
      updateUser(entity, user);
    }
  }

  /** Identity, branch, role and username never change. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "branchId", ignore = true)
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "username", ignore = true)
  void updateUser(@MappingTarget UserJpaEntity entity, User user);

  @InheritConfiguration(name = "updateUser")
  void updateStudent(@MappingTarget StudentJpaEntity entity, Student student);

  @InheritConfiguration(name = "updateUser")
  void updateTeacher(@MappingTarget TeacherJpaEntity entity, Teacher teacher);

  @InheritConfiguration(name = "updateUser")
  void updateGuardian(@MappingTarget GuardianJpaEntity entity, Guardian guardian);

  @Mapping(target = "firstName", source = "info.firstName")
  @Mapping(target = "lastName", source = "info.lastName")
  @Mapping(target = "email", source = "info.email")
  @Mapping(target = "phone", source = "info.phone")
  UserView toUserView(UserJpaEntity entity);

  @Mapping(target = "firstName", source = "info.firstName")
  @Mapping(target = "lastName", source = "info.lastName")
  @Mapping(target = "email", source = "info.email")
  @Mapping(target = "phone", source = "info.phone")
  StudentView toStudentView(StudentJpaEntity entity);

  @Mapping(target = "firstName", source = "info.firstName")
  @Mapping(target = "lastName", source = "info.lastName")
  @Mapping(target = "email", source = "info.email")
  @Mapping(target = "phone", source = "info.phone")
  TeacherView toTeacherView(TeacherJpaEntity entity);

  @Mapping(target = "firstName", source = "info.firstName")
  @Mapping(target = "lastName", source = "info.lastName")
  @Mapping(target = "email", source = "info.email")
  @Mapping(target = "phone", source = "info.phone")
  GuardianView toGuardianView(GuardianJpaEntity entity);
}
