-- Free study-resource library.
--
-- The paid product is the QCM bank; study material is free to any logged-in student.
-- So resources hang off modules (national since V9, selected by study year) and the
-- subscription gate deliberately does not apply to them.

CREATE TABLE resources (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id       uuid NOT NULL,
    title           varchar(255) NOT NULL,
    description     text,
    file_url        varchar(500) NOT NULL,
    file_type       varchar(10) NOT NULL,
    file_size_bytes bigint NOT NULL,
    position        int NOT NULL,
    published       boolean NOT NULL DEFAULT false,
    uploaded_by     uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_resources_module FOREIGN KEY (module_id)
        REFERENCES modules (id) ON DELETE CASCADE,
    -- SET NULL, not CASCADE: deleting the teacher who uploaded a PDF must not delete
    -- the PDF. Hence the column is nullable.
    CONSTRAINT fk_resources_uploaded_by FOREIGN KEY (uploaded_by)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT resources_file_type_check CHECK (file_type IN ('PDF', 'IMAGE')),
    CONSTRAINT resources_file_size_check CHECK (file_size_bytes > 0)
);

-- The two reads that exist: everything in a module (admin), and the published subset
-- (students). Both start from module_id, so the composite serves the student read and
-- the module_id prefix serves the admin one.
CREATE INDEX idx_resources_module_id ON resources (module_id);
CREATE INDEX idx_resources_module_published ON resources (module_id, published);
CREATE INDEX idx_resources_published ON resources (published);
