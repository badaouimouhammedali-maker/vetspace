-- Weekly stats need to bucket answers by day; records the last time the
-- user interacted with the question (answered it or consulted the
-- correction). NULL while UNANSWERED; cleared again by session reset.
ALTER TABLE session_questions ADD COLUMN answered_at timestamptz NULL;
