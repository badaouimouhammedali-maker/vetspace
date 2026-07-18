-- Rotation-with-reuse-detection needs a way to group every refresh token
-- minted from one login into a single lineage ("family"): rotating keeps
-- the same family_id, and presenting an already-rotated/revoked token
-- (reuse) revokes every token in that family without touching the user's
-- other, unrelated sessions/devices.
ALTER TABLE refresh_tokens ADD COLUMN family_id uuid NOT NULL DEFAULT gen_random_uuid();

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
