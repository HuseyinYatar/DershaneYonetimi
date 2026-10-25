from functools import lru_cache
from typing import Annotated

from fastapi import Depends
from sqlalchemy.orm import Session

from app.aws import create_client
from app.cache import ResultCache, build_cache
from app.config import Settings, get_settings
from app.db import get_session
from app.storage.file_storage import FileStorage, S3FileStorage


@lru_cache
def get_cache() -> ResultCache:
    settings = get_settings()
    return build_cache(settings.redis_url, settings.cache_ttl_seconds)


@lru_cache
def get_file_storage() -> FileStorage:
    settings = get_settings()
    return S3FileStorage(create_client("s3", settings), settings.optical_files_bucket)


SessionDep = Annotated[Session, Depends(get_session)]
CacheDep = Annotated[ResultCache, Depends(get_cache)]
StorageDep = Annotated[FileStorage, Depends(get_file_storage)]
SettingsDep = Annotated[Settings, Depends(get_settings)]
