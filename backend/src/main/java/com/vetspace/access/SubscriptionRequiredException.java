package com.vetspace.access;

/** Rendered as 403 with error code SUBSCRIPTION_REQUIRED so the frontend can route to the pricing page. */
public class SubscriptionRequiredException extends RuntimeException {

    public SubscriptionRequiredException() {
        super("An active subscription is required to access this content");
    }
}
