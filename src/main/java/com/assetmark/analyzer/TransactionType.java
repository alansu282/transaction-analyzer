package com.assetmark.analyzer;

import java.util.Optional;

/** The two valid transaction types. */
public enum TransactionType {
    CREDIT("credit"),
    DEBIT("debit");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    /**
     * Parses a raw type string. Matching is exact and case-sensitive,
     * so "Credit" and "CREDIT" are rejected.
     *
     * @return the matching type, or empty if the string is null or unrecognised
     */
    public static Optional<TransactionType> fromString(String raw) {
        for (TransactionType type : values()) {
            if (type.label.equals(raw)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
