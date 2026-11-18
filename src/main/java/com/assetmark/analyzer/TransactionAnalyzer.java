package com.assetmark.analyzer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Turns a list of transactions into per-account summaries and a top spender.
 *
 * <p>Invalid transactions are logged and skipped. Apart from that logging,
 * this class does no I/O.
 */
public final class TransactionAnalyzer {

    private static final Logger LOG = Logger.getLogger(TransactionAnalyzer.class.getName());

    /**
     * Analyzes the given transactions.
     *
     * @param transactions the input; may contain null or invalid entries, which are skipped
     * @return summaries sorted by accountId, plus the top spender
     * @throws NullPointerException if {@code transactions} itself is null
     */
    public AnalysisResult analyze(List<Transaction> transactions) {
        Objects.requireNonNull(transactions, "transactions must not be null");

        // Single pass: fold each valid transaction into its account's running totals.
        Map<String, Accumulator> accounts = new HashMap<>();
        for (Transaction transaction : transactions) {
            Optional<String> error = findValidationError(transaction);
            if (error.isPresent()) {
                LOG.warning(() -> "Skipping invalid transaction (" + error.get() + "): " + transaction);
                continue;
            }
            TransactionType type = TransactionType.fromString(transaction.type()).orElseThrow();
            accounts.computeIfAbsent(transaction.accountId(), id -> new Accumulator())
                    .add(type, transaction.amount());
        }

        List<AccountSummary> summaries = new ArrayList<>();
        accounts.forEach((accountId, totals) -> summaries.add(totals.toSummary(accountId)));
        summaries.sort(Comparator.comparing(AccountSummary::accountId));

        return new AnalysisResult(summaries, findTopSpender(summaries));
    }

    /**
     * Checks a transaction against the validation rules.
     *
     * @return a description of the first problem found, or empty if the transaction is valid
     */
    private static Optional<String> findValidationError(Transaction transaction) {
        if (transaction == null) {
            return Optional.of("transaction is null");
        }
        if (!isValidAccountId(transaction.accountId())) {
            return Optional.of("missing or blank accountId");
        }
        if (TransactionType.fromString(transaction.type()).isEmpty()) {
            return Optional.of("invalid type");
        }
        if (transaction.amount() == null || transaction.amount().signum() <= 0) {
            return Optional.of("amount must be positive");
        }
        return Optional.empty();
    }

    /**
     * An accountId is valid unless it is null, empty, or whitespace-only.
     * No format is enforced because the spec defines none; add one here if a real ID scheme is known.
     */
    private static boolean isValidAccountId(String accountId) {
        return accountId != null && !accountId.isBlank();
    }

    /**
     * Returns the accountId with the highest total debits, or null if no account has any debits.
     *
     * <p>The summaries are already sorted by accountId, so on a tie the first one seen
     * (the lexicographically smaller ID) is kept, because a later account only replaces
     * the current top spender if its debits are strictly greater.
     */
    private static String findTopSpender(List<AccountSummary> sortedSummaries) {
        AccountSummary topSpender = null;
        for (AccountSummary summary : sortedSummaries) {
            boolean hasDebits = summary.totalDebits().signum() > 0;
            boolean beatsCurrentTop = topSpender == null
                    || summary.totalDebits().compareTo(topSpender.totalDebits()) > 0;
            if (hasDebits && beatsCurrentTop) {
                topSpender = summary;
            }
        }
        return topSpender == null ? null : topSpender.accountId();
    }

    /** Running totals for one account, used only while processing transactions. */
    private static final class Accumulator {
        private BigDecimal totalCredits = BigDecimal.ZERO;
        private BigDecimal totalDebits = BigDecimal.ZERO;
        private BigDecimal largestTransaction = BigDecimal.ZERO;
        private int transactionCount = 0;

        void add(TransactionType type, BigDecimal amount) {
            switch (type) {
                case CREDIT -> totalCredits = totalCredits.add(amount);
                case DEBIT -> totalDebits = totalDebits.add(amount);
            }
            if (isLarger(amount, largestTransaction)) {
                largestTransaction = amount;
            }
            transactionCount++;
        }

        /**
         * Equal values written at different scales (100 vs 100.00) are broken by the larger
         * scale, so the result does not depend on input order. This matches how sums behave:
         * 100 + 0.00 has scale 2.
         */
        private static boolean isLarger(BigDecimal candidate, BigDecimal current) {
            int comparison = candidate.compareTo(current);
            return comparison > 0 || (comparison == 0 && candidate.scale() > current.scale());
        }

        /** Balance and overdrawn are derived here, once, so they always match the totals. */
        AccountSummary toSummary(String accountId) {
            BigDecimal endingBalance = totalCredits.subtract(totalDebits);
            boolean overdrawn = endingBalance.signum() < 0;
            return new AccountSummary(
                    accountId,
                    totalCredits,
                    totalDebits,
                    endingBalance,
                    largestTransaction,
                    transactionCount,
                    overdrawn);
        }
    }
}
