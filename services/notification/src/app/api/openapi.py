"""Swagger UI login through Keycloak (authorization code + PKCE).

The password grant is disabled in the realm, so Swagger obtains tokens the way the frontend does.
Every secured operation accepts either the Keycloak login or a pasted bearer token.
"""

from typing import Any

from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi

from app.config import Settings

KEYCLOAK_SCHEME = "keycloak"
BEARER_SCHEME = "HTTPBearer"


def swagger_ui_oauth(settings: Settings) -> dict[str, Any]:
    return {
        "clientId": settings.oidc_swagger_client_id,
        "usePkceWithAuthorizationCodeGrant": True,
        "scopes": "openid",
    }


def install_keycloak_login(app: FastAPI, settings: Settings) -> None:
    def openapi() -> dict[str, Any]:
        if app.openapi_schema is not None:
            return app.openapi_schema
        schema = get_openapi(
            title=app.title, version=app.version, description=app.description, routes=app.routes
        )
        schema.setdefault("components", {}).setdefault("securitySchemes", {})[KEYCLOAK_SCHEME] = {
            "type": "oauth2",
            "flows": {
                "authorizationCode": {
                    "authorizationUrl": f"{settings.oidc_issuer}/protocol/openid-connect/auth",
                    "tokenUrl": f"{settings.oidc_issuer}/protocol/openid-connect/token",
                    "scopes": {"openid": "OpenID Connect"},
                }
            },
        }
        for operations in schema.get("paths", {}).values():
            for operation in operations.values():
                requirements = operation.get("security", [])
                if any(BEARER_SCHEME in requirement for requirement in requirements):
                    requirements.append({KEYCLOAK_SCHEME: ["openid"]})
        app.openapi_schema = schema
        return schema

    app.openapi = openapi  # type: ignore[method-assign]
