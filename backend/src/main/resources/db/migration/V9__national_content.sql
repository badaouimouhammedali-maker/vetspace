-- National content.
--
-- The client's model changed: content is no longer per-school. Every student in the
-- country sees the same modules, courses, questions, exams and packs, selected by study
-- year alone. "École" survives only as a profile field on users, for analytics.
--
-- So school_id leaves modules, source_exams and packs. It stays on users (the analytics
-- field), and on notifications (targeting a message at one school is messaging, not
-- content scoping).
--
-- Dropping the column is the easy half. The hard half is that school_id was part of what
-- made rows distinct: two schools could each own a module called "Anatomie" for year 3,
-- and after the drop those become duplicates of one national module. Each block below
-- therefore MERGES before it drops.
--
-- The merge rule, identical in shape for all three tables:
--   1. Group by the new national key.
--   2. Elect ONE keeper per group — the row with the most children, because that is the
--      one whose loss would strand the most content, ties broken by the smallest id so
--      the result is deterministic and a re-run picks the same keeper.
--   3. Repoint every child row of the losers at the keeper.
--   4. Delete the losers.
-- Nothing is deleted before its children have been repointed, so no content, no
-- activation code and no subscription is ever orphaned.
--
-- Case and padding are normalised for grouping only (lower(trim(...))) — "Anatomie" and
-- "anatomie " are the same module to a human, and leaving them as two national modules
-- would just move the confusion. The keeper keeps its own original spelling.

-- ---------------------------------------------------------------------------
-- 1. Modules — national key (study_year, lower(trim(name))). Children: courses.
-- ---------------------------------------------------------------------------

WITH ranked AS (
    SELECT m.id,
           first_value(m.id) OVER (
               PARTITION BY m.study_year, lower(trim(m.name))
               ORDER BY (SELECT count(*) FROM courses c WHERE c.module_id = m.id) DESC, m.id
           ) AS keeper_id
    FROM modules m
)
UPDATE courses c
SET module_id = r.keeper_id
FROM ranked r
WHERE c.module_id = r.id
  AND r.id <> r.keeper_id;

WITH ranked AS (
    SELECT m.id,
           first_value(m.id) OVER (
               PARTITION BY m.study_year, lower(trim(m.name))
               ORDER BY (SELECT count(*) FROM courses c WHERE c.module_id = m.id) DESC, m.id
           ) AS keeper_id
    FROM modules m
)
DELETE FROM modules
WHERE id IN (SELECT id FROM ranked WHERE id <> keeper_id);

-- ---------------------------------------------------------------------------
-- 2. Source exams — national key (lower(trim(label)), year, exam_type).
--    Children: questions.source_exam_id (nullable, ON DELETE SET NULL — which is
--    precisely why the repoint must happen first: letting the delete cascade would
--    silently detach questions from their exam instead of merging them).
-- ---------------------------------------------------------------------------

WITH ranked AS (
    SELECT e.id,
           first_value(e.id) OVER (
               PARTITION BY lower(trim(e.label)), e.year, e.exam_type
               ORDER BY (SELECT count(*) FROM questions q WHERE q.source_exam_id = e.id) DESC, e.id
           ) AS keeper_id
    FROM source_exams e
)
UPDATE questions q
SET source_exam_id = r.keeper_id
FROM ranked r
WHERE q.source_exam_id = r.id
  AND r.id <> r.keeper_id;

WITH ranked AS (
    SELECT e.id,
           first_value(e.id) OVER (
               PARTITION BY lower(trim(e.label)), e.year, e.exam_type
               ORDER BY (SELECT count(*) FROM questions q WHERE q.source_exam_id = e.id) DESC, e.id
           ) AS keeper_id
    FROM source_exams e
)
DELETE FROM source_exams
WHERE id IN (SELECT id FROM ranked WHERE id <> keeper_id);

-- ---------------------------------------------------------------------------
-- 3. Packs — national key (study_year, academic_year). Children: subscriptions,
--    activation_codes, code_batches.
--
--    Keeper election weights subscriptions first: a subscription is money a student
--    already paid, and subscriptions.pack_id is ON DELETE RESTRICT, so a losing pack
--    that still carried one would abort the whole migration rather than lose it. The
--    repoint below is what makes the delete legal.
--
--    study_year is nullable here (NULL = a pack not tied to one year). Postgres treats
--    NULLs as distinct in a unique index, so two NULL-year packs for the same academic
--    year would not collide — the constraint at the bottom uses NULLS NOT DISTINCT to
--    close that, and the partition below groups them together to match.
-- ---------------------------------------------------------------------------

WITH ranked AS (
    SELECT p.id,
           first_value(p.id) OVER (
               PARTITION BY p.study_year, p.academic_year
               ORDER BY (SELECT count(*) FROM subscriptions s WHERE s.pack_id = p.id) DESC,
                        (SELECT count(*) FROM activation_codes a WHERE a.pack_id = p.id) DESC,
                        p.id
           ) AS keeper_id
    FROM packs p
)
UPDATE subscriptions s
SET pack_id = r.keeper_id
FROM ranked r
WHERE s.pack_id = r.id
  AND r.id <> r.keeper_id;

WITH ranked AS (
    SELECT p.id,
           first_value(p.id) OVER (
               PARTITION BY p.study_year, p.academic_year
               ORDER BY (SELECT count(*) FROM subscriptions s WHERE s.pack_id = p.id) DESC,
                        (SELECT count(*) FROM activation_codes a WHERE a.pack_id = p.id) DESC,
                        p.id
           ) AS keeper_id
    FROM packs p
)
UPDATE activation_codes a
SET pack_id = r.keeper_id
FROM ranked r
WHERE a.pack_id = r.id
  AND r.id <> r.keeper_id;

WITH ranked AS (
    SELECT p.id,
           first_value(p.id) OVER (
               PARTITION BY p.study_year, p.academic_year
               ORDER BY (SELECT count(*) FROM subscriptions s WHERE s.pack_id = p.id) DESC,
                        (SELECT count(*) FROM activation_codes a WHERE a.pack_id = p.id) DESC,
                        p.id
           ) AS keeper_id
    FROM packs p
)
UPDATE code_batches b
SET pack_id = r.keeper_id
FROM ranked r
WHERE b.pack_id = r.id
  AND r.id <> r.keeper_id;

WITH ranked AS (
    SELECT p.id,
           first_value(p.id) OVER (
               PARTITION BY p.study_year, p.academic_year
               ORDER BY (SELECT count(*) FROM subscriptions s WHERE s.pack_id = p.id) DESC,
                        (SELECT count(*) FROM activation_codes a WHERE a.pack_id = p.id) DESC,
                        p.id
           ) AS keeper_id
    FROM packs p
)
DELETE FROM packs
WHERE id IN (SELECT id FROM ranked WHERE id <> keeper_id);

-- ---------------------------------------------------------------------------
-- 4. Drop the column. The FK constraints and their indexes go with it.
-- ---------------------------------------------------------------------------

ALTER TABLE modules DROP COLUMN school_id;
ALTER TABLE source_exams DROP COLUMN school_id;
ALTER TABLE packs DROP COLUMN school_id;

-- ---------------------------------------------------------------------------
-- 5. The new national uniqueness. These are what stop the duplicates from coming
--    back through the admin console once the merge has cleaned them up.
-- ---------------------------------------------------------------------------

ALTER TABLE modules ADD CONSTRAINT uq_modules_year_name UNIQUE (study_year, name);
ALTER TABLE packs ADD CONSTRAINT uq_packs_year_academic UNIQUE NULLS NOT DISTINCT (study_year, academic_year);

-- Study year is now the only content selector, so it carries every catalog read.
CREATE INDEX IF NOT EXISTS idx_modules_study_year ON modules (study_year);
CREATE INDEX IF NOT EXISTS idx_packs_study_year ON packs (study_year);
