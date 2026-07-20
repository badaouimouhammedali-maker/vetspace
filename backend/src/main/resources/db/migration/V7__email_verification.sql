-- Email verification at registration.
--
-- Existing users predate the rule and were never asked to verify; locking them out of
-- their own accounts on deploy would be a self-inflicted outage, so they are grandfathered
-- in as verified. Only accounts created from here on must confirm their address.
ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users SET email_verified = TRUE;

-- Deliberately mirrors password_reset_tokens: same columns, same types, same cascade.
-- Only the SHA-256 hash is stored, so a database read cannot be replayed into an
-- account takeover.
CREATE TABLE email_verification_tokens (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL,
    token_hash varchar(255) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz NULL,
    CONSTRAINT ux_email_verification_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);
