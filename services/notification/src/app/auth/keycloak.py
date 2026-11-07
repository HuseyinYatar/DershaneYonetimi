"""Verification of Keycloak-issued access tokens.

Keycloak signs the token; this service only checks it (signature, issuer, aud=dershane-api). The
signing keys are fetched from Keycloak's JWKS endpoint once and cached, so no request calls
Keycloak synchronously. Headers added by the gateway are not trusted.

Authorization here needs only the user id: everyone reads and changes only their own
notifications and preferences, whatever their role.
"""

import uuid
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

import jwt
import structlog

from app.config import Settings
from app.errors import DependencyUnavailableError, UnauthorizedError

logger = structlog.get_logger(__name__)

KeyResolver = Callable[[str], Any]


@dataclass(frozen=True)
class CurrentUser:
    # Keycloak `sub`; the same id as user_id in the user service and recipient_id here.
    id: uuid.UUID


def jwks_key_resolver(jwks_url: str) -> KeyResolver:
    client = jwt.PyJWKClient(jwks_url, cache_keys=True, lifespan=3600)

    def resolve(token: str) -> Any:
        return client.get_signing_key_from_jwt(token).key

    return resolve


class KeycloakTokenVerifier:
    def __init__(self, settings: Settings, key_resolver: KeyResolver | None = None) -> None:
        self._issuer = settings.oidc_issuer
        self._audience = settings.oidc_audience
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

        try:
            return CurrentUser(id=uuid.UUID(str(claims["sub"])))
        except ValueError as exc:
            raise UnauthorizedError("TOKEN_INVALID", "Token claim is not a UUID", claim="sub") from exc
