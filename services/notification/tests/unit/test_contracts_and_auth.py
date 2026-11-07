import json
import uuid
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

from app.auth.keycloak import KeycloakTokenVerifier
from app.config import Settings
from app.errors import UnauthorizedError
from app.events.payloads import (
    EventEnvelope,
    ExamResultsPublished,
    GuardianLinked,
    GuardianUnlinked,
    StudentResultAdded,
    UserUpdated,
)
from tests.conftest import TEST_AUDIENCE, TEST_ISSUER

EVENTS_DIR = Path(__file__).resolve().parents[4] / "shared" / "events"

# One schema-valid sample per consumed event, as the producers publish them.
SAMPLES = {
    "user/UserUpdated": (
        UserUpdated,
        {
            "userId": str(uuid.uuid4()), "role": "GUARDIAN", "branchId": str(uuid.uuid4()),
            "firstName": "Fatma", "lastName": "Yılmaz", "email": None, "phone": "05320000001",
            "active": True, "studentNumber": None, "recordVersion": 2,
        },
    ),
    "user/GuardianLinked": (
        GuardianLinked,
        {"guardianId": str(uuid.uuid4()), "studentId": str(uuid.uuid4()), "relationship": "MOTHER"},
    ),
    "user/GuardianUnlinked": (
        GuardianUnlinked,
        {"guardianId": str(uuid.uuid4()), "studentId": str(uuid.uuid4())},
    ),
    "exam-analysis/ExamResultsPublished": (
        ExamResultsPublished,
        {
            "mockExamId": str(uuid.uuid4()), "branchId": str(uuid.uuid4()), "examTypeCode": "TYT",
            "studentIds": [str(uuid.uuid4())], "mockExamName": "TYT Deneme 3", "examDate": "2026-10-12",
        },
    ),
    "exam-analysis/StudentResultAdded": (
        StudentResultAdded,
        {"mockExamId": str(uuid.uuid4()), "branchId": str(uuid.uuid4()), "studentId": str(uuid.uuid4())},
    ),
}  # fmt: skip


def validator(relative_path: str) -> Draft202012Validator:
    return Draft202012Validator(json.loads((EVENTS_DIR / relative_path).read_text()))


class TestConsumedEventContracts:
    """What the producers may publish (shared/events) must be readable by this service."""

    @pytest.mark.parametrize("schema", sorted(SAMPLES))
    def test_schema_valid_sample_parses(self, schema: str) -> None:
        model, data = SAMPLES[schema]
        validator(f"{schema}.schema.json").validate(data)

        assert model.model_validate(data)

    def test_envelope_parses_and_ignores_unknown_fields(self) -> None:
        envelope = {
            "eventId": str(uuid.uuid4()),
            "eventType": "GuardianUnlinked",
            "schemaVersion": 1,
            "occurredAt": "2026-10-28T10:00:00.123Z",
            "source": "user",
            "data": SAMPLES["user/GuardianUnlinked"][1],
        }
        validator("envelope.schema.json").validate(envelope)

        parsed = EventEnvelope.model_validate({**envelope, "futureField": 1})
        assert parsed.event_type == "GuardianUnlinked"

    def test_exam_name_and_date_are_optional(self) -> None:
        data = {
            k: v
            for k, v in SAMPLES["exam-analysis/ExamResultsPublished"][1].items()
            if k not in {"mockExamName", "examDate"}
        }

        event = ExamResultsPublished.model_validate(data)
        assert (event.mock_exam_name, event.exam_date) == (None, None)


class TestKeycloakTokenVerifier:
    @pytest.fixture
    def verifier(self, signing_keys) -> KeycloakTokenVerifier:
        public_key = signing_keys.private_key.public_key()
        settings = Settings(oidc_issuer=TEST_ISSUER, oidc_audience=TEST_AUDIENCE)
        return KeycloakTokenVerifier(settings, key_resolver=lambda _: public_key)

    def test_valid_token_yields_the_user_id(self, verifier, make_token) -> None:
        user_id = uuid.uuid4()

        assert verifier.verify(make_token(sub=user_id)).id == user_id

    @pytest.mark.parametrize(
        ("kwargs", "code"), [({"expires_in": -10}, "TOKEN_EXPIRED"), ({"audience": "other"}, "TOKEN_INVALID")]
    )
    def test_rejected_tokens(self, verifier, make_token, kwargs, code) -> None:
        with pytest.raises(UnauthorizedError) as raised:
            verifier.verify(make_token(sub=uuid.uuid4(), **kwargs))

        assert raised.value.code == code
