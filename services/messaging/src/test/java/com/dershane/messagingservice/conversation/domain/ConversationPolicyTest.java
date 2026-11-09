package com.dershane.messagingservice.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.messagingservice.shared.domain.DomainException;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationPolicyTest {

  private final UUID branch = UUID.randomUUID();
  private final Party student = new Party(UUID.randomUUID(), PartyRole.STUDENT, branch, true);
  private final Party teacher = new Party(UUID.randomUUID(), PartyRole.TEACHER, branch, true);

  @Test
  void aStudentAndATeacherOfTheSameBranchMayTalkWhoeverOpens() {
    Conversation fromStudent = ConversationPolicy.open(student, teacher);
    Conversation fromTeacher = ConversationPolicy.open(teacher, student);

    assertThat(fromStudent.getStudentId()).isEqualTo(student.id());
    assertThat(fromStudent.getTeacherId()).isEqualTo(teacher.id());
    assertThat(fromTeacher.getStudentId()).isEqualTo(student.id());
    assertThat(fromTeacher.getTeacherId()).isEqualTo(teacher.id());
    assertThat(fromStudent.isParticipant(student.id())).isTrue();
    assertThat(fromStudent.otherParticipant(student.id())).isEqualTo(teacher.id());
  }

  @Test
  void oneCannotMessageOneself() {
    assertRejected(student, student, ErrorCode.CONVERSATION_NOT_ALLOWED);
  }

  @Test
  void twoStudentsOrTwoTeachersCannotTalk() {
    Party otherStudent = new Party(UUID.randomUUID(), PartyRole.STUDENT, branch, true);
    Party otherTeacher = new Party(UUID.randomUUID(), PartyRole.TEACHER, branch, true);

    assertRejected(student, otherStudent, ErrorCode.CONVERSATION_NOT_ALLOWED);
    assertRejected(teacher, otherTeacher, ErrorCode.CONVERSATION_NOT_ALLOWED);
  }

  @Test
  void anotherBranchIsRefused() {
    Party faraway = new Party(UUID.randomUUID(), PartyRole.TEACHER, UUID.randomUUID(), true);

    assertRejected(student, faraway, ErrorCode.CONVERSATION_NOT_ALLOWED);
  }

  @Test
  void aCallerWithoutABranchClaimMatchesNoBranch() {
    Party noBranch = new Party(UUID.randomUUID(), PartyRole.STUDENT, null, true);
    Party alsoNone = new Party(UUID.randomUUID(), PartyRole.TEACHER, null, true);

    assertRejected(noBranch, alsoNone, ErrorCode.CONVERSATION_NOT_ALLOWED);
  }

  @Test
  void anInactiveOtherSideIsRefused() {
    Party gone = new Party(UUID.randomUUID(), PartyRole.TEACHER, branch, false);

    assertRejected(student, gone, ErrorCode.PARTICIPANT_INACTIVE);
  }

  private static void assertRejected(Party caller, Party other, ErrorCode code) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> ConversationPolicy.open(caller, other))
        .extracting(DomainException::getErrorCode)
        .isEqualTo(code);
  }
}
