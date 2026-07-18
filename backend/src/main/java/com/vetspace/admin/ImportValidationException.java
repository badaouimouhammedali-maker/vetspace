package com.vetspace.admin;

import com.vetspace.admin.dto.QuestionDtos.ImportRowError;
import java.util.List;

/** Bulk import failed validation: carries every row error so the client can fix them all in one pass. Nothing was persisted. */
public class ImportValidationException extends RuntimeException {

    private final transient List<ImportRowError> errors;

    public ImportValidationException(List<ImportRowError> errors) {
        super("Import failed validation");
        this.errors = List.copyOf(errors);
    }

    public List<ImportRowError> getErrors() {
        return errors;
    }
}
