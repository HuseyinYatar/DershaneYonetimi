import uuid
from datetime import UTC, datetime

import pytest

from app.auth.keycloak import KeycloakTokenVerifier
from app.config import Settings
from app.errors import UnauthorizedError, localized_message
from app.files.service import _clean_file_name, content_disposition
from app.models import StoredFile
from app.models.enums import FileKind, FilePurpose, Role
from tests.conftest import TEST_AUDIENCE, TEST_ISSUER


@pytest.fixture
def verifier(signing_keys) -> KeycloakTokenVerifier:
    public_key = signing_keys.private_key.public_key()
    return KeycloakTokenVerifier(
        Settings(oidc_issuer=TEST_ISSUER, oidc_audience=TEST_AUDIENCE), key_resolver=lambda _: public_key
    )


class TestTokenVerifier:
    def test_service_account_role_is_read_and_keycloak_defaults_are_ignored(
        self, verifier, make_token
    ) -> None:
        user_id = uuid.uuid4()
        user = verifier.verify(make_token(sub=user_id, roles=["internal-service"], client="omr-service"))
        assert user.id == user_id
        assert user.roles == frozenset({Role.INTERNAL_SERVICE})
        assert user.client_id == "omr-service"
        assert user.is_service("omr-service")
        assert not user.is_service("messaging-service")

    def test_expired_token(self, verifier, make_token) -> None:
        with pytest.raises(UnauthorizedError) as raised:
            verifier.verify(make_token(sub=uuid.uuid4(), roles=[], expires_in=-10))
        assert raised.value.code == "TOKEN_EXPIRED"

    def test_token_for_another_audience(self, verifier, make_token) -> None:
        with pytest.raises(UnauthorizedError) as raised:
            verifier.verify(make_token(sub=uuid.uuid4(), roles=[], audience="account"))
        assert raised.value.code == "TOKEN_INVALID"


def stored(name: str, kind: FileKind, purpose: FilePurpose = FilePurpose.TASK_ATTACHMENT) -> StoredFile:
    return StoredFile(
        id=uuid.uuid4(),
        purpose=purpose,
        owner_id=uuid.uuid4(),
        kind=kind,
        size_bytes=1,
        file_name=name,
        source_sha256="0" * 64,
        created_at=datetime.now(UTC),
    )


class TestDownloadHeaders:
    def test_pdf_is_saved_under_its_turkish_name(self) -> None:
        header = content_disposition(stored("Ödev 1.pdf", FileKind.PDF))
        assert header == "attachment; filename=\"dev 1.pdf\"; filename*=UTF-8''%C3%96dev%201.pdf"

    def test_chat_photo_opens_in_the_browser(self) -> None:
        photo = stored("foto.jpg", FileKind.JPEG, FilePurpose.MESSAGE_PHOTO)
        assert content_disposition(photo).startswith("inline;")

    def test_homework_photo_is_saved(self) -> None:
        assert content_disposition(stored("soru.jpg", FileKind.JPEG)).startswith("attachment;")

    @pytest.mark.parametrize(
        ("raw", "clean"),
        [
            ("C:\\Users\\ayse\\odev.pdf", "odev.pdf"),
            ("../../etc/passwd", "passwd"),
            ("satır\r\nkırma.pdf", "satırkırma.pdf"),
            (None, "dosya.pdf"),
            ("   ", "dosya.pdf"),
        ],
    )
    def test_file_names_lose_paths_and_control_characters(self, raw: str | None, clean: str) -> None:
        assert _clean_file_name(raw, FileKind.PDF) == clean


class TestMessages:
    def test_limit_is_written_into_the_turkish_and_english_text(self) -> None:
        assert localized_message("FILE_TOO_LARGE", None, "x", maxMegabytes=10).startswith(
            "Dosya çok büyük. En fazla 10 MB"
        )
        assert localized_message("FILE_TOO_LARGE", "en-US", "x", maxMegabytes=10).endswith("10 MB.")

    def test_unknown_code_falls_back_to_the_english_message(self) -> None:
        assert localized_message("SOMETHING_ELSE", None, "fallback") == "fallback"
