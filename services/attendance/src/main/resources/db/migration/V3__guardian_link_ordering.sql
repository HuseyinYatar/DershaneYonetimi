-- Time of the user service event that set the guardian. Events arrive unordered; the replica
-- ignores an older GuardianLinked/GuardianUnlinked that comes after a newer one. Rows copied
-- before this column existed keep NULL and are overwritten by the next event.
ALTER TABLE guardian_link_replica ADD COLUMN linked_at TIMESTAMPTZ;
