"""Request and response bodies of the API."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from app.models.enums import Channel


class ApiModel(BaseModel):
    """camelCase on the wire (gateway, Next.js and the Java services), snake_case in Python."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)


class NotificationView(ApiModel):
    id: uuid.UUID
    event_type: str
    title: str
    body: str
    created_at: datetime
    read_at: datetime | None


class UnreadCount(ApiModel):
    count: int


class ReadAllResult(ApiModel):
    updated: int


class PreferenceItem(ApiModel):
    event_type: str
    channel: Channel
    enabled: bool


class PreferencesUpdate(ApiModel):
    preferences: list[PreferenceItem] = Field(max_length=50)
