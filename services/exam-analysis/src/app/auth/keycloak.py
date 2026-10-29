"""Verification of Keycloak-issued access tokens.

Keycloak signs the token; this service only checks it. The signing keys are fetched from
Keycloak's JWKS endpoint once and cached, so no request ever calls Keycloak synchronously
(CLAUDE.md: services validate tokens themselves with the JWKS address).
"""

import uuid
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

import jwt
import structlog

from app.config import Settings
from app.errors import DependencyUnavailableError, UnauthorizedError
from app.models.enums import Role

logger = structlog.get_logger(__name__)

KeyResolver = Callable[[str], Any]

_KNOWN_ROLES = frozenset(role.value for role in Role)


@dataclass(frozen=True)
class CurrentUser:
    id: uuid.UUID
    roles: frozenset[Role]
    branch_id: uuid.UUID | None

    def has_any_role(self, *roles: Role) -> bool:
        return not self.roles.isdisjoint(roles)


def jwks_key_resolver(jwks_url: str) -> KeyResolver:
    client = jwt.PyJWKClient(jwks_url, cache_keys=True, lifespan=3600)

    def resolve(token: str) -> Any:
        return client.get_signing_key_from_jwt(token).key

    return resolve


# setting congif dosyasından value almak icin
class KeycloakTokenVerifier:
    def __init__(self, settings: Settings, key_resolver: KeyResolver | None = None) -> None:
        self._issuer = settings.oidc_issuer
        self._audience = settings.oidc_audience
        self._roles_claim_path = settings.oidc_roles_claim.split(".")
        self._branch_claim = settings.oidc_branch_claim
        self._resolve_key = key_resolver or jwks_key_resolver(settings.oidc_jwks_url)
        if self._audience is None:
            logger.warning("auth.audience_verification_disabled", reason="oidc_audience_empty")

    def verify(self, token: str) -> CurrentUser:
        try:
            key = self._resolve_key(token)
        except jwt.PyJWKClientConnectionError as exc:
            raise DependencyUnavailableError(
                "IDENTITY_PROVIDER_UNAVAILABLE", "Signing keys could not be fetched from Keycloak"
            ) from exc
        except (jwt.PyJWKClientError, jwt.DecodeError) as exc:
            raise UnauthorizedError(
                "TOKEN_INVALID", "Token signing key is unknown", reason=type(exc).__name__
            ) from exc

        try:
            claims = jwt.decode(
                token,
                key,
                algorithms=["RS256"],
                issuer=self._issuer,
                audience=self._audience,
                options={"require": ["exp", "iat", "sub"], "verify_aud": self._audience is not None},
            )
        except jwt.ExpiredSignatureError as exc:
            raise UnauthorizedError("TOKEN_EXPIRED", "Token has expired") from exc
        except jwt.InvalidTokenError as exc:
            raise UnauthorizedError("TOKEN_INVALID", "Token is invalid", reason=type(exc).__name__) from exc

        return CurrentUser(
            id=self._parse_uuid(claims["sub"], claim="sub"),
            roles=self._extract_roles(claims),
            branch_id=self._extract_branch(claims),
        )

    def _extract_roles(self, claims: dict[str, Any]) -> frozenset[Role]:
        value: Any = claims
        for part in self._roles_claim_path:
            value = value.get(part, {}) if isinstance(value, dict) else {}
        names = value if isinstance(value, list) else []
        # Keycloak also puts default roles (offline_access, uma_authorization, ...) here; only
        # this system's roles are relevant.
        return frozenset(Role(name) for name in names if name in _KNOWN_ROLES)

    def _extract_branch(self, claims: dict[str, Any]) -> uuid.UUID | None:
        raw = claims.get(self._branch_claim)
        return None if raw is None else self._parse_uuid(raw, claim=self._branch_claim)

    @staticmethod
    def _parse_uuid(raw: Any, *, claim: str) -> uuid.UUID:
        try:
            return uuid.UUID(str(raw))
        except ValueError as exc:
            raise UnauthorizedError("TOKEN_INVALID", "Token claim is not a UUID", claim=claim) from exc
