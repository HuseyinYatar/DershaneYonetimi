from functools import lru_cache

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration, read from environment variables (case-insensitive)."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = "notification"
    environment: str = "local"
    log_level: str = "INFO"

    database_url: str = "postgresql+psycopg://notification:notification@localhost:5432/notification"
    db_pool_size: int = 5

    # Points to Floci locally; left empty in production so boto3 talks to real AWS.
    aws_endpoint_url: str | None = None
    aws_region: str = "eu-central-1"
    # Queue subscribed to user-events and exam-analysis-events (infra/terraform).
    # Looked up by name, so the same value works on Floci and AWS. Empty value disables consuming;
    # the worker then only delivers notifications already stored.
    sqs_queue_name: str | None = "notification-queue"
    sqs_max_messages: int = 10
    # Long polling: an empty queue answers after this many seconds. Kept short because the same
    # loop also delivers pending notifications.
    sqs_wait_seconds: int = 5

    # Mailpit locally (nothing leaves the machine), SES SMTP endpoint on AWS.
    smtp_host: str = "localhost"
    smtp_port: int = 1025
    smtp_username: str | None = None
    smtp_password: str | None = None
    smtp_starttls: bool = False
    smtp_timeout_seconds: float = 10.0
    mail_from: str = "Dershane <no-reply@dershane.local>"

    # Links in the notification texts point to the frontend.
    frontend_base_url: str = "http://localhost:3000"

    delivery_batch_size: int = 20
    delivery_max_attempts: int = 5
    # Wait before the 2nd, 3rd, ... attempt; the last value repeats.
    delivery_backoff_seconds: list[int] = [60, 300, 900, 3600]

    # Tokens are verified locally with Keycloak's signing keys (JWKS), never by calling Keycloak.
    oidc_issuer: str = "http://localhost:8180/realms/dershane"
    oidc_jwks_url: str = "http://localhost:8180/realms/dershane/protocol/openid-connect/certs"
    # Shared by every backend service. Empty value disables audience verification (logged at startup).
    oidc_audience: str | None = "dershane-api"
    # Public Keycloak client Swagger UI logs in with (authorization code + PKCE).
    oidc_swagger_client_id: str = "dershane-swagger"

    worker_poll_interval_seconds: float = 2.0
    worker_metrics_port: int = 9100
    worker_heartbeat_file: str = "/tmp/notification-worker-heartbeat"  # noqa: S108 - container-local file
    worker_heartbeat_max_age_seconds: int = 90

    # Exporter endpoint and headers come from the standard OTEL_EXPORTER_OTLP_* variables.
    otel_enabled: bool = False

    @field_validator(
        "aws_endpoint_url", "sqs_queue_name", "smtp_username", "smtp_password", "oidc_audience", mode="before"
    )
    @classmethod
    def _empty_string_is_none(cls, value: object) -> object:
        return None if value == "" else value

    @property
    def swagger_enabled(self) -> bool:
        # CLAUDE.md: Swagger UI is open only in the local and dev environments.
        return self.environment in {"local", "dev"}


@lru_cache
def get_settings() -> Settings:
    return Settings()
