package com.assetmark.analyzer;

import java.math.BigDecimal;

/** The computed totals for one account, built from its valid transactions only. */
public record AccountSummary(
        String accountId,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        BigDecimal endingBalance,
        BigDecimal largestTransaction,
        int transactionCount,
        boolean overdrawn) {
}
