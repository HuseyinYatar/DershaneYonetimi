import uuid
from urllib.parse import parse_qs, urlparse

import boto3
import pytest
from moto import mock_aws

from app.aws import create_presign_client
from app.config import Settings
from app.errors import DependencyUnavailableError, NotFoundError
from app.files.storage import S3FileStorage

BUCKET = "file-uploads"


@pytest.fixture
def s3():
    with mock_aws():
        client = boto3.client("s3", region_name="eu-central-1")
        client.create_bucket(Bucket=BUCKET, CreateBucketConfiguration={"LocationConstraint": "eu-central-1"})
        yield client


def test_content_round_trip_under_the_files_prefix(s3) -> None:
    storage = S3FileStorage(s3, s3, BUCKET)
    file_id = uuid.uuid4()

    storage.put(file_id, b"%PDF-1.7", "application/pdf")

    assert storage.get(file_id) == b"%PDF-1.7"
    head = s3.head_object(Bucket=BUCKET, Key=f"files/{file_id}")
    assert head["ContentType"] == "application/pdf"


def test_missing_object_is_not_found(s3) -> None:
    with pytest.raises(NotFoundError):
        S3FileStorage(s3, s3, BUCKET).get(uuid.uuid4())


def test_missing_bucket_means_the_storage_is_unavailable(s3) -> None:
    storage = S3FileStorage(s3, s3, "no-such-bucket")
    with pytest.raises(DependencyUnavailableError) as raised:
        storage.put(uuid.uuid4(), b"x", "text/plain")
    assert raised.value.code == "FILE_STORAGE_UNAVAILABLE"


def test_download_url_is_signed_for_the_address_the_browser_reaches() -> None:
    settings = Settings(aws_endpoint_url="http://floci:4566", s3_public_endpoint_url="http://localhost:4566")
    storage = S3FileStorage(None, create_presign_client(settings), BUCKET)
    file_id = uuid.uuid4()

    url = urlparse(
        storage.download_url(
            file_id, content_type="application/pdf", content_disposition="attachment", expires_in=300
        )
    )

    assert (url.scheme, url.netloc, url.path) == ("http", "localhost:4566", f"/{BUCKET}/files/{file_id}")
    query = parse_qs(url.query)
    assert query["response-content-disposition"] == ["attachment"]
    assert query["response-content-type"] == ["application/pdf"]
    assert query["X-Amz-Expires"] == ["300"]


def test_on_aws_the_regional_s3_address_is_used() -> None:
    storage = S3FileStorage(None, create_presign_client(Settings(aws_endpoint_url="")), BUCKET)
    url = urlparse(
        storage.download_url(uuid.uuid4(), content_type="x", content_disposition="y", expires_in=60)
    )
    assert url.netloc == f"{BUCKET}.s3.eu-central-1.amazonaws.com"
