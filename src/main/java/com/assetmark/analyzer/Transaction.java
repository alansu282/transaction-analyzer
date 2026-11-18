package com.assetmark.analyzer;

import java.math.BigDecimal;

/**
 * A single raw transaction, exactly as received.
 *
 * <p>No validation happens here: any field may be null or malformed, so that
 * invalid input can be represented and tested. {@link TransactionAnalyzer}
 * decides which transactions are valid.
 *
 * @param accountId the account the transaction belongs to
 * @param type      expected to be "credit" or "debit"
 * @param amount    expected to be positive
 * @param timestamp carried through but not used by the analysis
 */
public record Transaction(String accountId, String type, BigDecimal amount, String timestamp) {
}
