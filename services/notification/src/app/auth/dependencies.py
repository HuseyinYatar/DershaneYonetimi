from functools import lru_cache
from typing import Annotated

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.auth.keycloak import CurrentUser, KeycloakTokenVerifier
from app.config import get_settings
from app.errors import UnauthorizedError

_bearer = HTTPBearer(auto_error=False)


@lru_cache
def get_token_verifier() -> KeycloakTokenVerifier:
    return KeycloakTokenVerifier(get_settings())


def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
    verifier: Annotated[KeycloakTokenVerifier, Depends(get_token_verifier)],
) -> CurrentUser:
    if credentials is None:
        raise UnauthorizedError("TOKEN_MISSING", "A bearer token is required")
    return verifier.verify(credentials.credentials)


AnyUser = Annotated[CurrentUser, Depends(get_current_user)]
