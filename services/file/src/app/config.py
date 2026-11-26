from functools import lru_cache

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

MIB = 1024 * 1024


class Settings(BaseSettings):
    """Runtime configuration, read from environment variables (case-insensitive)."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = "file"
    environment: str = "local"
    log_level: str = "INFO"

    database_url: str = "postgresql+psycopg://file_service:file_service@localhost:5432/file_service"
    db_pool_size: int = 5

    # Points to Floci locally; left empty in production so boto3 talks to real AWS.
    aws_endpoint_url: str | None = None
    # Address the browser opens download URLs with. A presigned URL's signature covers the host, so
    # it must be signed with the address the browser can reach: http://localhost:4566 locally (the
    # container itself reaches Floci as http://floci:4566). Empty: same as AWS_ENDPOINT_URL.
    s3_public_endpoint_url: str | None = None
    aws_region: str = "eu-central-1"
    files_bucket: str = "file-uploads"
    download_url_ttl_seconds: int = 300

    max_image_bytes: int = 10 * MIB
    max_document_bytes: int = 20 * MIB
    # Photos are re-encoded; a longer edge is scaled down to this many pixels.
    image_max_edge_px: int = 2560
    # Larger images are rejected before they are decoded (decompression bombs).
    image_max_pixels: int = 40_000_000
    # A homework attachment given to a classroom lists every student of it.
    max_recipients: int = 200

    # Tokens are verified locally with Keycloak's signing keys (JWKS), never by calling Keycloak.
    oidc_issuer: str = "http://localhost:8180/realms/dershane"
    oidc_jwks_url: str = "http://localhost:8180/realms/dershane/protocol/openid-connect/certs"
    # Shared by every backend service. Empty value disables audience verification (logged at startup).
    oidc_audience: str | None = "dershane-api"
    oidc_roles_claim: str = "realm_access.roles"
    # Public Keycloak client Swagger UI logs in with (authorization code + PKCE).
    oidc_swagger_client_id: str = "dershane-swagger"

    # Exporter endpoint and headers come from the standard OTEL_EXPORTER_OTLP_* variables.
    otel_enabled: bool = False

    @field_validator("aws_endpoint_url", "s3_public_endpoint_url", "oidc_audience", mode="before")
    @classmethod
    def _empty_string_is_none(cls, value: object) -> object:
        return None if value == "" else value

    @property
    def swagger_enabled(self) -> bool:
        # CLAUDE.md: Swagger UI is open only in the local and dev environments.
        return self.environment in {"local", "dev"}

    @property
    def max_file_megabytes(self) -> int:
        return -(-max(self.max_image_bytes, self.max_document_bytes) // MIB)

    @property
    def max_request_bytes(self) -> int:
        # The largest file plus room for the multipart framing and the other form fields.
        return max(self.max_image_bytes, self.max_document_bytes) + 64 * 1024


@lru_cache
def get_settings() -> Settings:
    return Settings()
