from typing import Any

import boto3
from botocore.config import Config

from app.config import Settings


def _s3_client(settings: Settings, endpoint_url: str | None, addressing_style: str) -> Any:
    return boto3.client(
        "s3",
        endpoint_url=endpoint_url,
        region_name=settings.aws_region,
        config=Config(signature_version="s3v4", s3={"addressing_style": addressing_style}),
    )


# Floci serves buckets under the path (http://host:4566/bucket/key); a virtual-hosted address
# (bucket.localhost) would not resolve.
def _style(emulator_endpoint: str | None) -> str:
    return "path" if emulator_endpoint else "virtual"


def create_s3_client(settings: Settings) -> Any:
    """Client the service reads and writes with; AWS_ENDPOINT_URL is Floci locally, empty on AWS."""
    return _s3_client(settings, settings.aws_endpoint_url, _style(settings.aws_endpoint_url))


def create_presign_client(settings: Settings) -> Any:
    """Client that only signs download URLs. Signing makes no network call.

    On AWS the URL names the region (bucket.s3.eu-central-1.amazonaws.com). boto3 would otherwise
    sign for the global s3.amazonaws.com, which redirects requests for a new bucket to its region
    and the redirected request fails the signature check.
    """
    emulator = settings.s3_public_endpoint_url or settings.aws_endpoint_url
    endpoint = emulator or f"https://s3.{settings.aws_region}.amazonaws.com"
    return _s3_client(settings, endpoint, _style(emulator))
