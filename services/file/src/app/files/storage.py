"""File contents in S3 (Floci locally). Metadata and sharing live in PostgreSQL."""

import uuid
from typing import Any, Protocol

from botocore.exceptions import BotoCoreError, ClientError

from app.errors import DependencyUnavailableError, NotFoundError

_MISSING_CODES = {"NoSuchKey", "404"}


class FileStorage(Protocol):
    def put(self, file_id: uuid.UUID, content: bytes, content_type: str) -> None: ...
    def get(self, file_id: uuid.UUID) -> bytes: ...
    def download_url(
        self, file_id: uuid.UUID, *, content_type: str, content_disposition: str, expires_in: int
    ) -> str: ...


class S3FileStorage:
    def __init__(self, client: Any, presign_client: Any, bucket: str, prefix: str = "files/") -> None:
        self._client = client
        self._presign_client = presign_client
        self._bucket = bucket
        self._prefix = prefix

    def _key(self, file_id: uuid.UUID) -> str:
        return f"{self._prefix}{file_id}"

    def put(self, file_id: uuid.UUID, content: bytes, content_type: str) -> None:
        try:
            self._client.put_object(
                Bucket=self._bucket, Key=self._key(file_id), Body=content, ContentType=content_type
            )
        except (BotoCoreError, ClientError) as exc:
            raise _unavailable(file_id, exc, "The file could not be stored") from exc

    def get(self, file_id: uuid.UUID) -> bytes:
        try:
            response = self._client.get_object(Bucket=self._bucket, Key=self._key(file_id))
            return response["Body"].read()
        except ClientError as exc:
            if exc.response.get("Error", {}).get("Code") in _MISSING_CODES:
                # The metadata row exists but S3 has no object: a broken file, not a client error.
                raise NotFoundError(
                    "FILE_NOT_FOUND",
                    "File content not found",
                    file_id=str(file_id),
                    log_context={"reason": "object_missing"},
                ) from exc
            raise _unavailable(file_id, exc, "The file could not be read") from exc
        except BotoCoreError as exc:
            raise _unavailable(file_id, exc, "The file could not be read") from exc

    def download_url(
        self, file_id: uuid.UUID, *, content_type: str, content_disposition: str, expires_in: int
    ) -> str:
        # S3 answers the download with these headers instead of the stored ones, so a PDF is
        # saved under its original name and a photo opens in the browser.
        return self._presign_client.generate_presigned_url(
            "get_object",
            Params={
                "Bucket": self._bucket,
                "Key": self._key(file_id),
                "ResponseContentType": content_type,
                "ResponseContentDisposition": content_disposition,
            },
            ExpiresIn=expires_in,
        )


def _unavailable(file_id: uuid.UUID, exc: Exception, message: str) -> DependencyUnavailableError:
    return DependencyUnavailableError(
        "FILE_STORAGE_UNAVAILABLE", message, file_id=str(file_id), log_context={"error": type(exc).__name__}
    )


class InMemoryFileStorage:
    """For tests."""

    def __init__(self) -> None:
        self.files: dict[uuid.UUID, tuple[bytes, str]] = {}

    def put(self, file_id: uuid.UUID, content: bytes, content_type: str) -> None:
        self.files[file_id] = (content, content_type)

    def get(self, file_id: uuid.UUID) -> bytes:
        if file_id not in self.files:
            raise NotFoundError("FILE_NOT_FOUND", "File content not found", file_id=str(file_id))
        return self.files[file_id][0]

    def download_url(
        self, file_id: uuid.UUID, *, content_type: str, content_disposition: str, expires_in: int
    ) -> str:
        return f"https://storage.test/files/{file_id}?expires={expires_in}"
