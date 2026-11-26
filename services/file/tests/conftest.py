"""Shared fixtures.

Database tests use TEST_DATABASE_URL when it is set, otherwise a Testcontainers PostgreSQL
(requires Docker); without either they are skipped. S3 is replaced by InMemoryFileStorage in the
API tests and mocked with moto in the storage tests; nothing reaches AWS or Floci.
"""

import io
import os
import time
import uuid
from collections.abc import Callable, Iterator
from dataclasses import dataclass

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from PIL import Image

os.environ.setdefault("OIDC_ISSUER", "http://keycloak.test/realms/dershane")
os.environ.setdefault("OIDC_AUDIENCE", "dershane-api")
os.environ.setdefault("OTEL_ENABLED", "false")
os.environ.setdefault("AWS_ACCESS_KEY_ID", "test")
os.environ.setdefault("AWS_SECRET_ACCESS_KEY", "test")
# Tests never talk to Floci, even when a developer's .env points there.
os.environ["AWS_ENDPOINT_URL"] = ""

TEST_ISSUER = os.environ["OIDC_ISSUER"]
TEST_AUDIENCE = os.environ["OIDC_AUDIENCE"]


@pytest.fixture(scope="session", autouse=True)
def _structured_logging() -> None:
    """Route structlog through stdlib logging as in production, so caplog sees every event."""
    from app.observability.logging import configure_logging

    configure_logging("DEBUG", "file-test", "test")


@dataclass(frozen=True)
class SigningKeys:
    private_key: rsa.RSAPrivateKey
    kid: str = "test-key"


@pytest.fixture(scope="session")
def signing_keys() -> SigningKeys:
    return SigningKeys(private_key=rsa.generate_private_key(public_exponent=65537, key_size=2048))


TokenFactory = Callable[..., str]


@pytest.fixture(scope="session")
def make_token(signing_keys: SigningKeys) -> TokenFactory:
    def factory(
        *,
        sub: uuid.UUID,
        roles: list[str],
        expires_in: int = 300,
        audience: str = TEST_AUDIENCE,
        client: str = "dershane-frontend",
    ) -> str:
        now = int(time.time())
        claims = {
            "sub": str(sub),
            # Keycloak client the token was issued to; services are told apart by it.
            "azp": client,
            "iss": TEST_ISSUER,
            "aud": audience,
            "iat": now,
            "exp": now + expires_in,
            # Keycloak adds its default roles too; the service must ignore them.
            "realm_access": {"roles": [*roles, "offline_access", "default-roles-dershane"]},
        }
        return jwt.encode(
            claims, signing_keys.private_key, algorithm="RS256", headers={"kid": signing_keys.kid}
        )

    return factory


def png_bytes(size: tuple[int, int] = (40, 30)) -> bytes:
    output = io.BytesIO()
    Image.new("RGB", size, (200, 30, 30)).save(output, format="PNG")
    return output.getvalue()


PDF_BYTES = b"%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n"


@pytest.fixture(scope="session")
def database_url() -> Iterator[str]:
    configured = os.environ.get("TEST_DATABASE_URL")
    if configured:
        yield configured
        return
    try:
        from testcontainers.postgres import PostgresContainer

        container = PostgresContainer("postgres:17-alpine", driver="psycopg")
        container.start()
    except Exception as exc:  # Docker missing or not running
        pytest.skip(f"no TEST_DATABASE_URL and Testcontainers unavailable: {type(exc).__name__}")
    try:
        yield container.get_connection_url()
    finally:
        container.stop()


@pytest.fixture(scope="session")
def migrated_database(database_url: str) -> str:
    """Fresh schema built by the Alembic migrations (not create_all), so migrations are tested too."""
    from alembic import command
    from alembic.config import Config
    from sqlalchemy import create_engine, text

    engine = create_engine(database_url)
    with engine.begin() as connection:
        connection.execute(text("DROP SCHEMA public CASCADE"))
        connection.execute(text("CREATE SCHEMA public"))
    engine.dispose()

    service_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    config = Config(os.path.join(service_root, "alembic.ini"))
    config.attributes["database_url"] = database_url
    config.attributes["configure_logger"] = False
    command.upgrade(config, "head")

    os.environ["DATABASE_URL"] = database_url
    from app.config import get_settings
    from app.db import get_engine, get_session_factory

    get_settings.cache_clear()
    get_engine.cache_clear()
    get_session_factory.cache_clear()
    return database_url


@pytest.fixture
def session_factory(migrated_database: str):
    from sqlalchemy import text

    from app.db import get_engine, get_session_factory
    from app.models import Base

    yield get_session_factory()
    table_names = ", ".join(f'"{table.name}"' for table in Base.metadata.sorted_tables)
    with get_engine().begin() as connection:
        connection.execute(text(f"TRUNCATE {table_names} CASCADE"))


@pytest.fixture
def db_session(session_factory):
    with session_factory() as session:
        yield session
