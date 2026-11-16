from functools import lru_cache
from typing import Annotated

from fastapi import Depends
from sqlalchemy.orm import Session

from app.aws import create_presign_client, create_s3_client
from app.config import Settings, get_settings
from app.db import get_session
from app.files.storage import FileStorage, S3FileStorage


@lru_cache
def get_file_storage() -> FileStorage:
    settings = get_settings()
    return S3FileStorage(create_s3_client(settings), create_presign_client(settings), settings.files_bucket)


SessionDep = Annotated[Session, Depends(get_session)]
StorageDep = Annotated[FileStorage, Depends(get_file_storage)]
SettingsDep = Annotated[Settings, Depends(get_settings)]
