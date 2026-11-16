import io
import uuid

import pytest
from fastapi.testclient import TestClient
from PIL import Image
from sqlalchemy import func, select

from app.api.dependencies import get_file_storage
from app.auth.dependencies import get_token_verifier
from app.auth.keycloak import KeycloakTokenVerifier
from app.config import Settings, get_settings
from app.files.storage import InMemoryFileStorage
from app.main import create_app
from app.models import FileAccess, StoredFile
from tests.conftest import PDF_BYTES, TEST_AUDIENCE, TEST_ISSUER, png_bytes

pytestmark = pytest.mark.integration

GPS_IFD = 0x8825


class Api:
    """Test client plus one token per person in the scenarios."""

    def __init__(self, client: TestClient, storage: InMemoryFileStorage, make_token) -> None:
        self.client = client
        self.storage = storage
        self._make_token = make_token

    def headers(self, user_id: uuid.UUID, *roles: str, **extra: str) -> dict[str, str]:
        return {"Authorization": f"Bearer {self._make_token(sub=user_id, roles=list(roles))}", **extra}

    def upload(
        self,
        headers: dict[str, str],
        purpose: str,
        content: bytes = PDF_BYTES,
        *,
        name: str = "ödev.pdf",
        student_ids: list[uuid.UUID] | None = None,
        teacher_id: uuid.UUID | None = None,
    ):
        data: dict[str, object] = {"purpose": purpose}
        if student_ids:
            data["studentIds"] = [str(s) for s in student_ids]
        if teacher_id:
            data["teacherId"] = str(teacher_id)
        return self.client.post(
            "/api/v1/files", headers=headers, data=data, files={"file": (name, content, "application/pdf")}
        )


def build_api(signing_keys, make_token, settings: Settings) -> Api:
    app = create_app(settings)
    public_key = signing_keys.private_key.public_key()
    storage = InMemoryFileStorage()
    app.dependency_overrides[get_token_verifier] = lambda: KeycloakTokenVerifier(
        Settings(oidc_issuer=TEST_ISSUER, oidc_audience=TEST_AUDIENCE), key_resolver=lambda _: public_key
    )
    app.dependency_overrides[get_file_storage] = lambda: storage
    app.dependency_overrides[get_settings] = lambda: settings
    return Api(TestClient(app, raise_server_exceptions=False), storage, make_token)


@pytest.fixture
def api(session_factory, signing_keys, make_token) -> Api:
    return build_api(signing_keys, make_token, Settings(environment="test"))


TEACHER, OTHER_TEACHER = uuid.uuid4(), uuid.uuid4()
STUDENT_A, STUDENT_B, STUDENT_C = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
OMR_SERVICE, EXAM_SERVICE = uuid.uuid4(), uuid.uuid4()
SUPER_ADMIN, BRANCH_ADMIN = uuid.uuid4(), uuid.uuid4()


class TestHomeworkAttachment:
    def test_teacher_shares_a_pdf_with_two_students(self, api: Api) -> None:
        response = api.upload(
            api.headers(TEACHER, "teacher"), "TASK_ATTACHMENT", student_ids=[STUDENT_A, STUDENT_B]
        )

        assert response.status_code == 201
        body = response.json()
        assert body["purpose"] == "TASK_ATTACHMENT"
        assert body["contentType"] == "application/pdf"
        assert body["sizeBytes"] == len(PDF_BYTES)
        assert body["fileName"] == "ödev.pdf"
        file_id = body["id"]

        for reader in (TEACHER, STUDENT_A, STUDENT_B):
            role = "teacher" if reader == TEACHER else "student"
            assert (
                api.client.get(f"/api/v1/files/{file_id}", headers=api.headers(reader, role)).status_code
                == 200
            )
        for outsider, role in (
            (STUDENT_C, "student"),
            (OTHER_TEACHER, "teacher"),
            (SUPER_ADMIN, "super-admin"),
        ):
            denied = api.client.get(
                f"/api/v1/files/{file_id}/download-url", headers=api.headers(outsider, role)
            )
            assert denied.status_code == 404
            assert denied.json()["errorCode"] == "FILE_NOT_FOUND"

    def test_student_cannot_upload_a_homework_file(self, api: Api) -> None:
        response = api.upload(api.headers(STUDENT_A, "student"), "TASK_ATTACHMENT", student_ids=[STUDENT_B])
        assert response.status_code == 403
        problem = response.json()
        assert response.headers["content-type"] == "application/problem+json"
        assert problem["errorCode"] == "PURPOSE_NOT_ALLOWED"
        assert problem["detail"] == "Bu tür dosyayı yükleme yetkiniz yok."

    def test_homework_file_needs_students(self, api: Api) -> None:
        response = api.upload(api.headers(TEACHER, "teacher"), "TASK_ATTACHMENT")
        assert response.status_code == 422
        assert response.json()["errorCode"] == "RECIPIENTS_REQUIRED"

    def test_homework_file_takes_no_teacher_id(self, api: Api) -> None:
        response = api.upload(
            api.headers(TEACHER, "teacher"),
            "TASK_ATTACHMENT",
            student_ids=[STUDENT_A],
            teacher_id=OTHER_TEACHER,
        )
        assert response.status_code == 422
        assert response.json()["errorCode"] == "RECIPIENTS_NOT_ALLOWED"

    def test_recipient_limit(self, session_factory, signing_keys, make_token) -> None:
        api = build_api(signing_keys, make_token, Settings(environment="test", max_recipients=2))
        response = api.upload(
            api.headers(TEACHER, "teacher"),
            "TASK_ATTACHMENT",
            student_ids=[STUDENT_A, STUDENT_B, STUDENT_C],
        )
        assert response.status_code == 422
        assert response.json()["errorCode"] == "TOO_MANY_RECIPIENTS"


class TestSubmission:
    def test_student_hands_in_to_one_teacher(self, api: Api) -> None:
        response = api.upload(api.headers(STUDENT_A, "student"), "SUBMISSION", teacher_id=TEACHER)
        assert response.status_code == 201
        file_id = response.json()["id"]

        teacher_view = api.client.get(
            f"/api/v1/files/{file_id}/download-url", headers=api.headers(TEACHER, "teacher")
        )
        assert teacher_view.status_code == 200
        assert teacher_view.headers["cache-control"] == "no-store"
        assert teacher_view.json()["url"].startswith("https://storage.test/files/")
        assert "expiresAt" in teacher_view.json()

        for outsider, role in ((OTHER_TEACHER, "teacher"), (STUDENT_B, "student")):
            response = api.client.get(f"/api/v1/files/{file_id}", headers=api.headers(outsider, role))
            assert response.status_code == 404

    def test_submission_needs_a_teacher(self, api: Api) -> None:
        response = api.upload(api.headers(STUDENT_A, "student"), "SUBMISSION")
        assert response.status_code == 422
        assert response.json()["errorCode"] == "RECIPIENTS_REQUIRED"


class TestOpticalResult:
    def test_omr_service_uploads_and_exam_analysis_reads_the_text(self, api: Api) -> None:
        text = b"12345678 A CBDAEDCBA\n"
        response = api.upload(
            api.headers(OMR_SERVICE, "internal-service"), "OMR_RESULT", text, name="deneme.txt"
        )
        assert response.status_code == 201
        assert response.json()["contentType"] == "text/plain"
        file_id = response.json()["id"]

        content = api.client.get(
            f"/api/v1/files/{file_id}/content", headers=api.headers(EXAM_SERVICE, "internal-service")
        )
        assert content.status_code == 200
        assert content.content == text
        assert content.headers["content-type"] == "text/plain; charset=utf-8"
        assert content.headers["x-content-type-options"] == "nosniff"
        assert content.headers["content-disposition"].startswith("attachment;")

        super_admin = api.headers(SUPER_ADMIN, "admin", "super-admin")
        assert api.client.get(f"/api/v1/files/{file_id}/download-url", headers=super_admin).status_code == 200
        branch_admin = api.headers(BRANCH_ADMIN, "admin")
        assert api.client.get(f"/api/v1/files/{file_id}/content", headers=branch_admin).status_code == 404

    def test_people_cannot_upload_optical_results(self, api: Api) -> None:
        for user_id, role in ((TEACHER, "teacher"), (SUPER_ADMIN, "super-admin")):
            response = api.upload(api.headers(user_id, role), "OMR_RESULT", b"123\n", name="x.txt")
            assert response.status_code == 403

    def test_optical_result_takes_no_recipients(self, api: Api) -> None:
        response = api.upload(
            api.headers(OMR_SERVICE, "internal-service"), "OMR_RESULT", b"123\n", student_ids=[STUDENT_A]
        )
        assert response.status_code == 422
        assert response.json()["errorCode"] == "RECIPIENTS_NOT_ALLOWED"


class TestContentChecks:
    def test_photo_is_stored_without_its_gps_position(self, api: Api) -> None:
        exif = Image.Exif()
        exif.get_ifd(GPS_IFD)[2] = (40.0, 46.0, 30.0)
        original = io.BytesIO()
        Image.new("RGB", (40, 30), (1, 2, 3)).save(original, format="JPEG", exif=exif)

        response = api.upload(
            api.headers(STUDENT_A, "student"),
            "SUBMISSION",
            original.getvalue(),
            name="soru.jpg",
            teacher_id=TEACHER,
        )

        assert response.status_code == 201
        assert response.json()["contentType"] == "image/jpeg"
        stored, content_type = api.storage.files[uuid.UUID(response.json()["id"])]
        assert content_type == "image/jpeg"
        with Image.open(io.BytesIO(stored)) as image:
            assert not image.getexif().get_ifd(GPS_IFD)

    def test_program_renamed_to_pdf_is_rejected(self, api: Api) -> None:
        response = api.upload(
            api.headers(STUDENT_A, "student"), "SUBMISSION", b"MZ\x90\x00" + bytes(64), teacher_id=TEACHER
        )
        assert response.status_code == 415
        assert response.json()["errorCode"] == "UNSUPPORTED_FILE_TYPE"

    def test_empty_file(self, api: Api) -> None:
        response = api.upload(api.headers(STUDENT_A, "student"), "SUBMISSION", b"", teacher_id=TEACHER)
        assert response.status_code == 422
        assert response.json()["errorCode"] == "FILE_EMPTY"

    def test_photo_over_its_own_limit(self, session_factory, signing_keys, make_token) -> None:
        api = build_api(signing_keys, make_token, Settings(environment="test", max_image_bytes=100))
        response = api.upload(
            api.headers(STUDENT_A, "student"), "SUBMISSION", png_bytes((200, 200)), teacher_id=TEACHER
        )
        assert response.status_code == 413
        assert response.json()["errorCode"] == "FILE_TOO_LARGE"

    def test_request_body_over_the_limit_is_cut_off(self, session_factory, signing_keys, make_token) -> None:
        settings = Settings(environment="test", max_image_bytes=1024, max_document_bytes=1024)
        api = build_api(signing_keys, make_token, settings)
        big = b"%PDF-" + bytes(settings.max_request_bytes)

        response = api.upload(api.headers(STUDENT_A, "student"), "SUBMISSION", big, teacher_id=TEACHER)

        assert response.status_code == 413
        problem = response.json()
        assert problem["errorCode"] == "FILE_TOO_LARGE"
        assert problem["maxMegabytes"] == 1
        assert problem["detail"] == "Dosya çok büyük. En fazla 1 MB yükleyebilirsiniz."

    def test_chunked_body_without_length_is_counted(self, session_factory, signing_keys, make_token) -> None:
        settings = Settings(environment="test", max_image_bytes=1024, max_document_bytes=1024)
        api = build_api(signing_keys, make_token, settings)

        def chunks():
            for _ in range(200):
                yield bytes(1024)

        response = api.client.post(
            "/api/v1/files",
            headers={**api.headers(STUDENT_A, "student"), "Content-Type": "multipart/form-data; boundary=x"},
            content=chunks(),
        )
        assert response.status_code == 413


class TestIdempotency:
    def test_repeated_request_returns_the_first_file(self, api: Api, db_session) -> None:
        headers = api.headers(TEACHER, "teacher", **{"Idempotency-Key": "upload-1"})
        first = api.upload(headers, "TASK_ATTACHMENT", student_ids=[STUDENT_A])
        second = api.upload(headers, "TASK_ATTACHMENT", student_ids=[STUDENT_A])

        assert first.status_code == second.status_code == 201
        assert first.json() == second.json()
        assert db_session.scalar(select(func.count()).select_from(StoredFile)) == 1
        assert db_session.scalar(select(func.count()).select_from(FileAccess)) == 1

    def test_same_key_with_another_file_is_rejected(self, api: Api) -> None:
        headers = api.headers(TEACHER, "teacher", **{"Idempotency-Key": "upload-1"})
        assert api.upload(headers, "TASK_ATTACHMENT", student_ids=[STUDENT_A]).status_code == 201

        response = api.upload(headers, "TASK_ATTACHMENT", PDF_BYTES + b"x", student_ids=[STUDENT_A])

        assert response.status_code == 422
        assert response.json()["errorCode"] == "IDEMPOTENCY_KEY_REUSED"

    def test_concurrent_request_with_the_same_key_gets_the_winners_file(
        self, api: Api, db_session, monkeypatch
    ) -> None:
        from app.files import service

        headers = api.headers(TEACHER, "teacher", **{"Idempotency-Key": "race"})
        first = api.upload(headers, "TASK_ATTACHMENT", student_ids=[STUDENT_A]).json()
        # The second request looks before the first one commits, so it misses it and only learns
        # about it from the unique constraint.
        real_find = service._find_by_key
        calls: list[object] = []

        def find_misses_once(*args):
            calls.append(args)
            return None if len(calls) == 1 else real_find(*args)

        monkeypatch.setattr(service, "_find_by_key", find_misses_once)

        second = api.upload(headers, "TASK_ATTACHMENT", student_ids=[STUDENT_A])

        assert second.status_code == 201
        assert second.json()["id"] == first["id"]
        assert db_session.scalar(select(func.count()).select_from(StoredFile)) == 1

    def test_keys_belong_to_their_owner(self, api: Api) -> None:
        key = {"Idempotency-Key": "same-key"}
        first = api.upload(api.headers(TEACHER, "teacher", **key), "TASK_ATTACHMENT", student_ids=[STUDENT_A])
        second = api.upload(
            api.headers(OTHER_TEACHER, "teacher", **key), "TASK_ATTACHMENT", student_ids=[STUDENT_A]
        )
        assert first.json()["id"] != second.json()["id"]


class TestCommon:
    def test_missing_token(self, api: Api) -> None:
        response = api.client.get(f"/api/v1/files/{uuid.uuid4()}")
        assert response.status_code == 401
        assert response.json()["errorCode"] == "TOKEN_MISSING"

    def test_english_text_on_request(self, api: Api) -> None:
        response = api.client.get(
            f"/api/v1/files/{uuid.uuid4()}",
            headers=api.headers(STUDENT_A, "student", **{"Accept-Language": "en"}),
        )
        assert response.status_code == 404
        assert response.json()["detail"] == "File not found."

    def test_not_found_does_not_say_why(self, api: Api) -> None:
        file_id = api.upload(api.headers(STUDENT_A, "student"), "SUBMISSION", teacher_id=TEACHER).json()["id"]
        hidden = api.client.get(f"/api/v1/files/{file_id}", headers=api.headers(STUDENT_B, "student")).json()
        missing = api.client.get(
            f"/api/v1/files/{uuid.uuid4()}", headers=api.headers(STUDENT_B, "student")
        ).json()
        assert set(hidden) == set(missing)
        assert "reason" not in hidden

    def test_unknown_purpose_is_a_validation_error(self, api: Api) -> None:
        response = api.upload(api.headers(TEACHER, "teacher"), "MESSAGE_PHOTO", student_ids=[STUDENT_A])
        assert response.status_code == 422
        assert response.json()["errorCode"] == "VALIDATION_ERROR"

    def test_health(self, api: Api) -> None:
        assert api.client.get("/health/live").status_code == 200
        assert api.client.get("/health/ready").json() == {"status": "ok", "checks": {"database": "ok"}}

    def test_swagger_is_closed_in_production(self, session_factory, signing_keys, make_token) -> None:
        production = build_api(signing_keys, make_token, Settings(environment="prod"))
        assert production.client.get("/openapi.json").status_code == 404
        local = build_api(signing_keys, make_token, Settings(environment="local"))
        assert local.client.get("/openapi.json").status_code == 200
