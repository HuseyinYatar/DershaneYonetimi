from enum import StrEnum

# Member values equal member names: SQLAlchemy stores names, the API and events expose values.


class Channel(StrEnum):
    EMAIL = "EMAIL"
    SMS = "SMS"
    # The row itself is the notification: written as SENT, shown by the API.
    IN_APP = "IN_APP"


class DeliveryStatus(StrEnum):
    PENDING = "PENDING"
    SENT = "SENT"
    FAILED = "FAILED"


class UserRole(StrEnum):
    """Role in the UserUpdated event (user service), not a Keycloak realm role."""

    STUDENT = "STUDENT"
    TEACHER = "TEACHER"
    GUARDIAN = "GUARDIAN"
    ADMIN = "ADMIN"


class Relationship(StrEnum):
    MOTHER = "MOTHER"
    FATHER = "FATHER"
    LEGAL_GUARDIAN = "LEGAL_GUARDIAN"
    OTHER = "OTHER"


# Users may turn these off per event type; IN_APP always stays on.
CONFIGURABLE_CHANNELS = (Channel.EMAIL, Channel.SMS)
