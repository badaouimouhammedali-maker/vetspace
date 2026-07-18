package com.vetspace.web.error;

import java.time.Instant;

public record ApiError(String error, String message, Instant timestamp) {
}
