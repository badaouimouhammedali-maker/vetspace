-- Persistent record of each code-generation batch.
--
-- Plaintext codes are shown exactly once and held only in memory, so a restart or a
-- missed download loses them for good: the codes still exist as hashes, attached to a
-- pack, consuming inventory and sellable to nobody. Recording the batch gives the admin
-- a way out — revoke the whole batch and generate a fresh one — and makes "did anyone
-- actually download this?" answerable.
CREATE TABLE code_batches (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    pack_id      uuid NOT NULL,
    created_by   uuid NULL,
    code_count   integer NOT NULL,
    max_uses     integer NOT NULL,
    generated_at timestamptz NOT NULL,
    -- Set when the one-shot CSV is actually fetched. FALSE here is the signal that the
    -- plaintext was never saved anywhere.
    downloaded_at timestamptz NULL,
    CONSTRAINT fk_code_batches_pack FOREIGN KEY (pack_id) REFERENCES packs (id) ON DELETE CASCADE,
    -- Keep the batch if the admin account goes away; who generated it is secondary to
    -- the codes remaining accounted for.
    CONSTRAINT fk_code_batches_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_code_batches_pack_id ON code_batches (pack_id);

-- Codes generated before this migration have no batch; nullable rather than backfilled
-- into a fictional one.
ALTER TABLE activation_codes
    ADD COLUMN batch_id uuid NULL,
    ADD CONSTRAINT fk_activation_codes_batch FOREIGN KEY (batch_id) REFERENCES code_batches (id) ON DELETE SET NULL;

CREATE INDEX idx_activation_codes_batch_id ON activation_codes (batch_id);
