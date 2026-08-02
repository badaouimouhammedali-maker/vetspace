-- Anti-sharing: one active session per account.
--
-- The enforcement itself lives in RefreshTokenService (revoke or LRU-evict other
-- families on login). What this migration adds is the metadata that makes the policy
-- explainable rather than mysterious — the admin console has to be able to say WHICH
-- device was closed and from WHERE, and an account being passed around a class group
-- has to be visible before anyone accuses a student of it.

ALTER TABLE refresh_tokens
    -- The table never had one: rows were only ever looked up by hash, so nothing needed
    -- to know when a token was minted. The 7-day sharing window and the "logins" count
    -- both do.
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
    -- Derived server-side from the User-Agent ("Chrome sur Windows"), never sent by the
    -- client. Coarse on purpose: enough to recognise your own device in a list, not a
    -- fingerprint. Nullable because pre-existing rows have no User-Agent to derive from
    -- and because a request may legitimately arrive without the header.
    ADD COLUMN device_label varchar(120) NULL,
    -- Touched on every rotation, so "least recently used" means the family whose device
    -- has been quiet longest — the right one to evict when MAX_CONCURRENT_SESSIONS > 1.
    -- Seeded to created_at below rather than left NULL, so LRU ordering is total from
    -- the first request after this migration instead of treating every old family as
    -- equally stale.
    ADD COLUMN last_used_at timestamptz NULL,
    -- varchar, NOT inet, and the reason is a live failure mode rather than taste.
    -- `created_ip` is derived from X-Forwarded-For, which is client-controlled: with an
    -- inet column, anyone sending `X-Forwarded-For: nonsense` gets a type error on INSERT
    -- and turns their own login into a 500. Postgres also refuses to implicitly cast
    -- varchar to inet, so the driver would need a custom type for no gain — the value is
    -- only ever counted distinct, never subnet-matched. 45 characters is the longest
    -- textual IPv6 (39) with room for an IPv4-mapped form; DeviceContext rejects anything
    -- that is not IP-shaped before it gets here.
    ADD COLUMN created_ip varchar(45) NULL,
    -- WHY a token was revoked, which the rotation path cannot infer reliably.
    --
    -- Every revoked token used to look identical, so an evicted device replaying its
    -- cookie was indistinguishable from an attacker replaying a stolen rotated one — and
    -- both were answered as reuse. That is the difference between "you were signed out on
    -- another device" and a security incident, and the frontend has to tell a student
    -- which one happened.
    --
    -- ROTATED         superseded by its own successor; replaying it IS reuse
    -- REUSE           the family was burned because a rotated token came back
    -- SUPERSEDED      evicted by a newer login (the anti-sharing policy)
    -- LOGOUT          the user signed out
    -- PASSWORD_CHANGE credentials changed under it
    -- ADMIN           an admin revoked the session
    ADD COLUMN revoked_reason varchar(20) NULL;

-- Existing rows would all default to now(), which would report every account as having
-- logged in today. Their real creation time is recoverable exactly rather than guessed:
-- expires_at is set to creation + the 7-day TTL, so subtracting it back is not an
-- approximation. Kept in step with RefreshTokenService.REFRESH_TOKEN_TTL.
UPDATE refresh_tokens SET created_at = expires_at - interval '7 days';

UPDATE refresh_tokens SET last_used_at = created_at WHERE last_used_at IS NULL;

-- Pre-existing revoked rows: we genuinely do not know why, and guessing ROTATED would
-- make a replay of one look like reuse on evidence we do not have. Left NULL, which the
-- rotation path treats as the conservative case.


-- The admin sharing signal counts logins and distinct IPs per user over a 7-day window,
-- and the login path lists a user's live families on every single login. Both are
-- user_id + a created_at/revoked_at predicate; idx_refresh_tokens_user_id alone makes
-- those scan every token the account has ever held, which for a heavy user is one row
-- per 15 minutes for a week.
CREATE INDEX idx_refresh_tokens_user_created ON refresh_tokens (user_id, created_at DESC);

-- Eviction picks the least-recently-used LIVE family for one user. Partial index: revoked
-- rows are the overwhelming majority over time and are never candidates.
CREATE INDEX idx_refresh_tokens_live_by_user ON refresh_tokens (user_id, last_used_at)
    WHERE revoked_at IS NULL;
