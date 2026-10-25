"""Shared fixtures.

Database tests use TEST_DATABASE_URL when it is set, otherwise a Testcontainers PostgreSQL
(requires Docker); without either they are skipped. Integration tests never touch real AWS.
"""

import os
import time
import uuid
from collections.abc import Callable, Iterator
from dataclasses import dataclass

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

os.environ.setdefault("OIDC_ISSUER", "http://keycloak.test/realms/dershane")
os.environ.setdefault("OIDC_AUDIENCE", "exam-analysis")
os.environ.setdefault("REDIS_URL", "")
os.environ.setdefault("OTEL_ENABLED", "false")

TEST_ISSUER = os.environ["OIDC_ISSUER"]
TEST_AUDIENCE = os.environ["OIDC_AUDIENCE"]


@pytest.fixture(scope="session", autouse=True)
def _structured_logging() -> None:
    """Route structlog through stdlib logging as in production, so caplog sees every event."""
    from app.observability.logging import configure_logging

    configure_logging("INFO", "exam-analysis-test", "test")


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
        branch_id: uuid.UUID | None = None,
        expires_in: int = 300,
        issuer: str = TEST_ISSUER,
        audience: str = TEST_AUDIENCE,
    ) -> str:
        now = int(time.time())
        claims: dict[str, object] = {
            "sub": str(sub),
            "iss": issuer,
            "aud": audience,
            "iat": now,
            "exp": now + expires_in,
            "realm_access": {"roles": [*roles, "offline_access", "default-roles-dershane"]},
        }
        if branch_id is not None:
            claims["branch_id"] = str(branch_id)
        return jwt.encode(
            claims, signing_keys.private_key, algorithm="RS256", headers={"kid": signing_keys.kid}
        )

    return factory


@pytest.fixture(scope="session")
def database_url() -> Iterator[str]:
    configured = os.environ.get("TEST_DATABASE_URL")
    if configured:
        yield configured
        return
    try:
        from testcontainers.postgres import PostgresContainer

        container = PostgresContainer("postgres:16-alpine", driver="psycopg")
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


@pytest.fixture
def catalog(db_session):
    from tests.factories import create_catalog

    return create_catalog(db_session)


@pytest.fixture
def storage():
    from app.storage.file_storage import InMemoryFileStorage

    return InMemoryFileStorage()


class RecordingCache:
    """DisabledResultCache that remembers invalidations, so tests can assert on them."""

    def __init__(self) -> None:
        self.invalidated: list[tuple[uuid.UUID, frozenset[uuid.UUID]]] = []

    def get_json(self, key: str) -> None:
        return None

    def set_json(self, key: str, value: object) -> None:
        return None

    def invalidate_exam(self, mock_exam_id: uuid.UUID, exam_type_id: uuid.UUID, student_ids) -> None:
        self.invalidated.append((mock_exam_id, frozenset(student_ids)))


@pytest.fixture
def cache() -> RecordingCache:
    return RecordingCache()


@pytest.fixture
def processor(session_factory, storage, cache):
    from app.services.import_processor import ImportProcessor

    return ImportProcessor(session_factory, storage, cache)


@pytest.fixture
def app(session_factory, signing_keys, storage, cache):
    from app.api.dependencies import get_cache, get_file_storage
    from app.auth.dependencies import get_token_verifier
    from app.auth.keycloak import KeycloakTokenVerifier
    from app.config import get_settings
    from app.main import create_app

    application = create_app(get_settings())
    public_key = signing_keys.private_key.public_key()
    verifier = KeycloakTokenVerifier(get_settings(), key_resolver=lambda _: public_key)
    application.dependency_overrides[get_token_verifier] = lambda: verifier
    application.dependency_overrides[get_file_storage] = lambda: storage
    application.dependency_overrides[get_cache] = lambda: cache
    return application


@pytest.fixture
def client(app):
    from fastapi.testclient import TestClient

    with TestClient(app, raise_server_exceptions=False) as test_client:
        yield test_client


@dataclass(frozen=True)
class Actors:
    branch_id: uuid.UUID
    admin: dict[str, str]
    teacher: dict[str, str]
    other_branch_admin: dict[str, str]
    admin_id: uuid.UUID

    @staticmethod
    def bearer(token: str) -> dict[str, str]:
        return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def actors(make_token: TokenFactory) -> Actors:
    branch_id = uuid.uuid4()
    admin_id = uuid.uuid4()
    return Actors(
        branch_id=branch_id,
        admin=Actors.bearer(make_token(sub=admin_id, roles=["ADMIN"], branch_id=branch_id)),
        teacher=Actors.bearer(make_token(sub=uuid.uuid4(), roles=["TEACHER"], branch_id=branch_id)),
        other_branch_admin=Actors.bearer(
            make_token(sub=uuid.uuid4(), roles=["ADMIN"], branch_id=uuid.uuid4())
        ),
        admin_id=admin_id,
    )


@pytest.fixture
def student_headers(make_token: TokenFactory) -> Callable[[uuid.UUID], dict[str, str]]:
    return lambda student_id: Actors.bearer(make_token(sub=student_id, roles=["STUDENT"]))
