"""The scenario of the design drawing: TYT Deneme 3 in one branch, students Ali and Ayşe,
guardians Fatma (Ali's mother) and Hasan (Ayşe's father)."""

import uuid
from dataclasses import dataclass, field

from sqlalchemy.orm import Session

from app.models import ContactReplica, GuardianLinkReplica, NotificationPreference
from app.models.enums import Channel, Relationship, UserRole


@dataclass(frozen=True)
class Scenario:
    branch_id: uuid.UUID = field(default_factory=uuid.uuid4)
    ali: uuid.UUID = field(default_factory=uuid.uuid4)
    ayse: uuid.UUID = field(default_factory=uuid.uuid4)
    fatma: uuid.UUID = field(default_factory=uuid.uuid4)
    hasan: uuid.UUID = field(default_factory=uuid.uuid4)


def contact(user_id: uuid.UUID, role: UserRole, first_name: str, **overrides: object) -> ContactReplica:
    values: dict[str, object] = {
        "user_id": user_id,
        "role": role,
        "branch_id": uuid.uuid4(),
        "first_name": first_name,
        "last_name": "Test",
        "email": None,
        "phone": None,
        "active": True,
        "record_version": 1,
    }
    return ContactReplica(**{**values, **overrides})


def create_scenario(session: Session) -> Scenario:
    s = Scenario()
    session.add_all(
        [
            # Ali has neither e-mail nor phone -> in-app only
            contact(s.ali, UserRole.STUDENT, "Ali", last_name="Yılmaz"),
            contact(s.ayse, UserRole.STUDENT, "Ayşe", last_name="Demir", email="ayse@example.com"),
            contact(s.fatma, UserRole.GUARDIAN, "Fatma", email="fatma@example.com", phone="05320000001"),
            # Hasan has no e-mail and turned SMS off for exam results
            contact(s.hasan, UserRole.GUARDIAN, "Hasan", phone="05330000002"),
            GuardianLinkReplica(student_id=s.ali, guardian_id=s.fatma, relationship=Relationship.MOTHER),
            GuardianLinkReplica(student_id=s.ayse, guardian_id=s.hasan, relationship=Relationship.FATHER),
            NotificationPreference(user_id=s.hasan, event_type="ExamResultsPublished", channel=Channel.SMS),
        ]
    )
    session.commit()
    return s
