package com.vetspace.user.dto;

import java.time.Instant;

public record ActiveSubscriptionDto(String packName, Instant endsAt) {
}
