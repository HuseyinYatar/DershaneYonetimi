from collections.abc import Callable
from functools import lru_cache
from typing import Annotated

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.auth.keycloak import CurrentUser, KeycloakTokenVerifier
from app.config import get_settings
from app.errors import ForbiddenError, UnauthorizedError
from app.models.enums import Role

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


def require_roles(*roles: Role) -> Callable[[CurrentUser], CurrentUser]:
    allowed = frozenset(roles)

    def dependency(user: Annotated[CurrentUser, Depends(get_current_user)]) -> CurrentUser:
        if not user.has_any_role(*allowed):
            raise ForbiddenError(
                "ROLE_NOT_ALLOWED",
                "Your role is not allowed to perform this operation",
                required_roles=sorted(allowed),
            )
        return user

    return dependency

#endpointe uzun uzun tanım yapamak yerine alies tanımlama
AnyUser = Annotated[CurrentUser, Depends(get_current_user)]
AdminUser = Annotated[CurrentUser, Depends(require_roles(Role.ADMIN))]
StaffUser = Annotated[CurrentUser, Depends(require_roles(Role.ADMIN, Role.TEACHER))]
