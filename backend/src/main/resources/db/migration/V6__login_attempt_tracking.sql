-- Per-account lockout after repeated failed logins.
--
-- Stored on the row rather than in memory so it survives a restart and holds across
-- instances: an in-memory counter would reset on every deploy, and an attacker only has
-- to outlast one. The IP-based rate limiter is a separate, complementary control — it
-- does not stop a slow attack spread across many addresses against one account.
ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    -- Start of the window the current attempt count belongs to; attempts older than the
    -- window are discarded rather than accumulating forever.
    ADD COLUMN first_failed_login_at TIMESTAMP,
    -- Non-null and in the future means the account is locked.
    ADD COLUMN locked_until TIMESTAMP;

-- Lets the "is this account locked" read stay cheap on the login path.
CREATE INDEX idx_users_locked_until ON users (locked_until) WHERE locked_until IS NOT NULL;
