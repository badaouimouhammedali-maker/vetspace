-- Demo mode: courses flagged free_preview are the only content an
-- unsubscribed student may build their single trial session from.
ALTER TABLE courses ADD COLUMN free_preview boolean NOT NULL DEFAULT false;
