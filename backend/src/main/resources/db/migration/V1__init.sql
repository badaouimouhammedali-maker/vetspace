-- Placeholder migration for initial schema bootstrap
CREATE TABLE IF NOT EXISTS health_check (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(32) NOT NULL
);
