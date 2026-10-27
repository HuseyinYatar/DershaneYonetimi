"""Storage of uploaded optical files.

Temporary adapter: until the file service exists, uploads are written to this service's bucket.
Afterwards only the adapter changes (content fetched through a presigned URL from the file service).
"""

import uuid
from typing import Any, Protocol

from botocore.exceptions import BotoCoreError, ClientError

from app.errors import DependencyUnavailableError, NotFoundError


class FileStorage(Protocol):
    def put(self, file_id: uuid.UUID, content: bytes, content_type: str) -> None: ...
    def get(self, file_id: uuid.UUID) -> bytes: ...


class S3FileStorage:
    def __init__(self, s3_client: Any, bucket: str, prefix: str = "optical/") -> None:
        self._client = s3_client
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
            raise DependencyUnavailableError(
                "FILE_STORAGE_UNAVAILABLE", "The file could not be stored", file_id=str(file_id)
            ) from exc

    def get(self, file_id: uuid.UUID) -> bytes:
        try:
            response = self._client.get_object(Bucket=self._bucket, Key=self._key(file_id))
        except ClientError as exc:
            if exc.response.get("Error", {}).get("Code") in {"NoSuchKey", "404"}:
                raise NotFoundError(
                    "FILE_NOT_FOUND", "Uploaded file not found", file_id=str(file_id)
                ) from exc
            raise DependencyUnavailableError(
                "FILE_STORAGE_UNAVAILABLE", "The file could not be read", file_id=str(file_id)
            ) from exc
        except BotoCoreError as exc:
            raise DependencyUnavailableError(
                "FILE_STORAGE_UNAVAILABLE", "The file could not be read", file_id=str(file_id)
            ) from exc
        return response["Body"].read()


class InMemoryFileStorage:
    def __init__(self) -> None:
        self.files: dict[uuid.UUID, bytes] = {}

    def put(self, file_id: uuid.UUID, content: bytes, content_type: str) -> None:
        self.files[file_id] = content

    def get(self, file_id: uuid.UUID) -> bytes:
        if file_id not in self.files:
            raise NotFoundError("FILE_NOT_FOUND", "Uploaded file not found", file_id=str(file_id))
        return self.files[file_id]
