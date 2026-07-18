package com.vetspace.access;

import java.security.SecureRandom;

/** Activation code format: 16 crypto-random chars from an unambiguous alphabet, displayed XXXX-XXXX-XXXX-XXXX. */
public final class CodeGenerator {

    /** No 0/O, 1/I/L — codes get read aloud and typed from paper. */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 16;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CodeGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH + 3);
        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(SECURE_RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Canonical form used for hashing: uppercase, separators stripped — redeem input is forgiving about dashes/case. */
    public static String normalize(String input) {
        return input == null ? "" : input.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
