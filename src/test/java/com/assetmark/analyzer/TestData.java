package com.assetmark.analyzer;

import java.math.BigDecimal;
import java.util.List;

/** Shared builders so each test shows only the values that matter to it. */
final class TestData {

    static final String TIMESTAMP = "2026-05-10T10:00:00Z";

    private TestData() {
    }

    static Transaction credit(String accountId, String amount) {
        return transaction(accountId, "credit", amount);
    }

    static Transaction debit(String accountId, String amount) {
        return transaction(accountId, "debit", amount);
    }

    /** Builds any transaction, valid or not. A null amount string gives a null amount. */
    static Transaction transaction(String accountId, String type, String amount) {
        BigDecimal parsedAmount = amount == null ? null : new BigDecimal(amount);
        return new Transaction(accountId, type, parsedAmount, TIMESTAMP);
    }

    /** The example input from the spec. */
    static List<Transaction> specExample() {
        return List.of(
                new Transaction("A100", "credit", new BigDecimal("500"), "2026-05-10T10:00:00Z"),
                new Transaction("A100", "debit", new BigDecimal("200"), "2026-05-10T12:00:00Z"),
                new Transaction("B200", "debit", new BigDecimal("50"), "2026-05-10T11:00:00Z"),
                new Transaction("B200", "credit", new BigDecimal("20"), "2026-05-10T13:00:00Z"),
                new Transaction("", "credit", new BigDecimal("100"), "2026-05-10T14:00:00Z"));
    }
}
