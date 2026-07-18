package com.vetspace.auth.dto;

public record LoginResponse(String accessToken, long expiresInSeconds) {
}
