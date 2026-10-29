from typing import Any

import boto3

from app.config import Settings


def create_client(service_name: str, settings: Settings) -> Any:
    """boto3 client; AWS_ENDPOINT_URL points to Floci locally and is empty on AWS."""
    return boto3.client(service_name, endpoint_url=settings.aws_endpoint_url, region_name=settings.aws_region)
