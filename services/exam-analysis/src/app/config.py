from functools import lru_cache

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration, read from environment variables (case-insensitive)."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = "exam-analysis"
    environment: str = "local"
    log_level: str = "INFO"

    database_url: str = "postgresql+psycopg://exam_analysis:exam_analysis@localhost:5432/exam_analysis"
    db_pool_size: int = 5

    # Empty value disables the cache; the service then reads straight from PostgreSQL.
    redis_url: str | None = "redis://localhost:6379/0"
    cache_ttl_seconds: int = 300

    # Points to Floci locally; left empty in production so boto3 talks to real AWS.
    aws_endpoint_url: str | None = None
    aws_region: str = "eu-central-1"
    optical_files_bucket: str = "exam-analysis-optical-files"
    max_upload_bytes: int = 10 * 1024 * 1024

    #Keycloak'a her seferinde sormadan JWT Token'ı kendi kendine doğrulaması ve içindeki bilgileri okuması
    oidc_issuer: str = "http://localhost:8080/realms/dershane"
    oidc_jwks_url: str = "http://localhost:8080/realms/dershane/protocol/openid-connect/certs"
    # Empty value disables audience verification (logged as a warning at startup).
    oidc_audience: str | None = "exam-analysis"
    oidc_roles_claim: str = "realm_access.roles"
    oidc_branch_claim: str = "branch_id"

    sns_topic_arn: str | None = None
    outbox_relay_enabled: bool = False
    outbox_batch_size: int = 50

    worker_poll_interval_seconds: float = 2.0
    worker_stale_job_timeout_seconds: int = 900
    worker_reaper_interval_seconds: float = 30.0
    worker_metrics_port: int = 9100
    worker_heartbeat_file: str = "/tmp/exam-analysis-worker-heartbeat"  # noqa: S108 - container-local file
    worker_heartbeat_max_age_seconds: int = 60

    # Exporter endpoint and headers come from the standard OTEL_EXPORTER_OTLP_* variables.
    otel_enabled: bool = False

    @field_validator("redis_url", "aws_endpoint_url", "oidc_audience", "sns_topic_arn", mode="before")
    @classmethod
    def _empty_string_is_none(cls, value: object) -> object:
        return None if value == "" else value


@lru_cache
def get_settings() -> Settings:
    return Settings()
