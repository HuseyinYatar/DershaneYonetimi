"""Response bodies of the API."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from app.models.enums import FilePurpose


class ApiModel(BaseModel):
    """camelCase on the wire (gateway, frontend and the Java services), snake_case in Python."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)


class FileView(ApiModel):
    id: uuid.UUID
    purpose: FilePurpose
    content_type: str = Field(description="Detected from the file's content, not from the upload")
    size_bytes: int
    file_name: str
    created_at: datetime


class DownloadUrlView(ApiModel):
    url: str = Field(description="Presigned S3 address; open it directly, without an Authorization header")
    expires_at: datetime
