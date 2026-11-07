-- Ids of integration events from other services that were already applied (at-least-once delivery).
CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    event_type   VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
