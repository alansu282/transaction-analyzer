package com.assetmark.analyzer;

import static com.assetmark.analyzer.TestData.credit;
import static com.assetmark.analyzer.TestData.debit;
import static com.assetmark.analyzer.TestData.specExample;
import static com.assetmark.analyzer.TestData.transaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("TransactionAnalyzer")
class TransactionAnalyzerTest {

    private final TransactionAnalyzer analyzer = new TransactionAnalyzer();

    @Nested
    @DisplayName("Spec example")
    class SpecExample {

        @Test
        @DisplayName("produces the summaries and top spender given in the spec")
        void matchesSpecOutput() {
            AnalysisResult result = analyzer.analyze(specExample());

            // All amounts here are whole numbers, so exact BigDecimal equality (including scale) is safe.
            List<AccountSummary> expected = List.of(
                    new AccountSummary("A100", bd("500"), bd("200"), bd("300"), bd("500"), 2, false),
                    new AccountSummary("B200", bd("20"), bd("50"), bd("-30"), bd("50"), 2, true));
            assertEquals(expected, result.summaries());
            assertEquals("A100", result.topSpender());
        }
    }

    @Nested
    @DisplayName("Invalid transactions are skipped")
    class InvalidTransactions {

        @ParameterizedTest(name = "accountId = [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("missing or blank accountId")
        void rejectsMissingOrBlankAccountId(String accountId) {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "50"),
                    transaction(accountId, "credit", "100")));

            assertEquals(List.of("A100"), accountIds(result));
            assertEquals(1, summaryFor(result, "A100").transactionCount());
        }

        @ParameterizedTest(name = "type = [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"Credit", "CREDIT", " credit", "debit ", "refund"})
        @DisplayName("type other than exactly \"credit\" or \"debit\"")
        void rejectsInvalidType(String type) {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "50"),
                    transaction("A100", type, "100")));

            AccountSummary summary = summaryFor(result, "A100");
            assertEquals(1, summary.transactionCount());
            assertAmount("50", summary.totalCredits());
            assertAmount("0", summary.totalDebits());
        }

        @ParameterizedTest(name = "amount = [{0}]")
        @NullSource
        @ValueSource(strings = {"0", "0.00", "-50"})
        @DisplayName("missing, zero or negative amount")
        void rejectsNonPositiveAmount(String amount) {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "50"),
                    transaction("A100", "debit", amount)));

            AccountSummary summary = summaryFor(result, "A100");
            assertEquals(1, summary.transactionCount());
            assertAmount("0", summary.totalDebits());
        }

        @Test
        @DisplayName("null entry in the list")
        void skipsNullEntry() {
            List<Transaction> transactions = Arrays.asList(credit("A100", "50"), null);

            AnalysisResult result = analyzer.analyze(transactions);

            assertEquals(1, summaryFor(result, "A100").transactionCount());
        }

        @Test
        @DisplayName("an invalid transaction's amount never becomes largestTransaction")
        void invalidAmountDoesNotAffectLargest() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "50"),
                    transaction("A100", "refund", "9999")));

            assertAmount("50", summaryFor(result, "A100").largestTransaction());
        }

        @Test
        @DisplayName("an account with only invalid transactions is omitted")
        void omitsAccountWithOnlyInvalidTransactions() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "50"),
                    debit("B200", "0"),
                    transaction("B200", "Debit", "10")));

            assertEquals(List.of("A100"), accountIds(result));
        }
    }

    @Nested
    @DisplayName("Mistakes the analyzer cannot detect")
    class UndetectableMistakes {

        @Test
        @DisplayName("a typo in an accountId creates a separate account")
        void typoInAccountIdIsSeparateAccount() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "100"),
                    credit("A1OO", "50"))); // letter O instead of zero

            assertEquals(List.of("A100", "A1OO"), accountIds(result));
        }

        @Test
        @DisplayName("accountIds are not trimmed or case-folded")
        void accountIdsAreNotNormalised() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "100"),
                    credit("A100 ", "100"),
                    credit("a100", "100")));

            assertEquals(List.of("A100", "A100 ", "a100"), accountIds(result));
        }

        @Test
        @DisplayName("malformed or missing timestamps are still counted")
        void timestampsAreNotValidated() {
            AnalysisResult result = analyzer.analyze(List.of(
                    new Transaction("A100", "credit", bd("10"), "not-a-date"),
                    new Transaction("A100", "credit", bd("10"), null)));

            assertEquals(2, summaryFor(result, "A100").transactionCount());
        }
    }

    @Nested
    @DisplayName("Aggregation")
    class Aggregation {

        @Test
        @DisplayName("totals, balance, largest and count across several transactions")
        void aggregatesSeveralTransactions() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "100"),
                    credit("A100", "250.50"),
                    debit("A100", "75.25"),
                    debit("A100", "20")));

            AccountSummary summary = summaryFor(result, "A100");
            assertAmount("350.50", summary.totalCredits());
            assertAmount("95.25", summary.totalDebits());
            assertAmount("255.25", summary.endingBalance());
            assertAmount("250.50", summary.largestTransaction());
            assertEquals(4, summary.transactionCount());
        }

        @Test
        @DisplayName("input order does not change the result")
        void resultIsIndependentOfInputOrder() {
            List<Transaction> transactions = List.of(
                    credit("B200", "30"),
                    debit("A100", "10.5"),
                    credit("A100", "100"),
                    debit("B200", "45"),
                    debit("C300", "45"));
            List<Transaction> reversed = new ArrayList<>(transactions);
            Collections.reverse(reversed);

            assertEquals(analyzer.analyze(transactions), analyzer.analyze(reversed));
        }

        @Test
        @DisplayName("equal largest amounts at different scales give the same result in any order")
        void largestTransactionIsIndependentOfOrderAcrossScales() {
            List<Transaction> transactions = List.of(
                    credit("A100", "100.00"),
                    debit("A100", "100"));
            List<Transaction> reversed = new ArrayList<>(transactions);
            Collections.reverse(reversed);

            // assertEquals on the records compares BigDecimal scale too, so 100 vs 100.00 would fail.
            assertEquals(analyzer.analyze(transactions), analyzer.analyze(reversed));
            assertEquals(bd("100.00"), summaryFor(analyzer.analyze(transactions), "A100").largestTransaction());
        }

        @Test
        @DisplayName("identical transactions are each counted")
        void countsDuplicateTransactions() {
            AnalysisResult result = analyzer.analyze(List.of(
                    debit("A100", "40"),
                    debit("A100", "40")));

            AccountSummary summary = summaryFor(result, "A100");
            assertEquals(2, summary.transactionCount());
            assertAmount("80", summary.totalDebits());
        }

        @Test
        @DisplayName("largestTransaction can be a debit")
        void largestTransactionCanBeADebit() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "100"),
                    debit("A100", "300")));

            assertAmount("300", summaryFor(result, "A100").largestTransaction());
        }
    }

    @Nested
    @DisplayName("Balance and overdrawn")
    class BalanceAndOverdrawn {

        @Test
        @DisplayName("a balance of exactly zero is not overdrawn")
        void zeroBalanceIsNotOverdrawn() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "100"),
                    debit("A100", "100")));

            AccountSummary summary = summaryFor(result, "A100");
            assertAmount("0", summary.endingBalance());
            assertFalse(summary.overdrawn());
        }

        @Test
        @DisplayName("a credit-only account has zero debits and is not overdrawn")
        void creditOnlyAccount() {
            AnalysisResult result = analyzer.analyze(List.of(credit("A100", "100")));

            AccountSummary summary = summaryFor(result, "A100");
            assertAmount("0", summary.totalDebits());
            assertAmount("100", summary.endingBalance());
            assertFalse(summary.overdrawn());
        }

        @Test
        @DisplayName("a debit-only account has zero credits and is overdrawn")
        void debitOnlyAccount() {
            AnalysisResult result = analyzer.analyze(List.of(debit("A100", "25")));

            AccountSummary summary = summaryFor(result, "A100");
            assertAmount("0", summary.totalCredits());
            assertAmount("-25", summary.endingBalance());
            assertTrue(summary.overdrawn());
        }
    }

    @Nested
    @DisplayName("Decimal precision")
    class DecimalPrecision {

        @Test
        @DisplayName("0.30 minus three 0.10 debits is exactly zero, not overdrawn")
        void noFloatingPointDrift() {
            // With double arithmetic this balance comes out as about -2.8e-17, which would be overdrawn.
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "0.30"),
                    debit("A100", "0.10"),
                    debit("A100", "0.10"),
                    debit("A100", "0.10")));

            AccountSummary summary = summaryFor(result, "A100");
            assertAmount("0", summary.endingBalance());
            assertFalse(summary.overdrawn());
        }

        @Test
        @DisplayName("amounts with more than two decimal places are summed exactly")
        void sumsSmallFractionsExactly() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "0.0001"),
                    credit("A100", "0.0001"),
                    credit("A100", "0.0001")));

            assertAmount("0.0003", summaryFor(result, "A100").totalCredits());
        }

        @Test
        @DisplayName("very large amounts do not overflow")
        void handlesVeryLargeAmounts() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "100000000000000000000"),
                    credit("A100", "100000000000000000000")));

            assertAmount("200000000000000000000", summaryFor(result, "A100").totalCredits());
        }
    }

    @Nested
    @DisplayName("Top spender")
    class TopSpender {

        @Test
        @DisplayName("is the account with the highest total debits")
        void highestDebitsWins() {
            AnalysisResult result = analyzer.analyze(List.of(
                    debit("A100", "50"),
                    debit("B200", "80"),
                    debit("C300", "10")));

            assertEquals("B200", result.topSpender());
        }

        @Test
        @DisplayName("a tie goes to the lexicographically smaller accountId")
        void tieGoesToSmallerId() {
            AnalysisResult result = analyzer.analyze(List.of(
                    debit("B200", "50"),
                    debit("A100", "50")));

            assertEquals("A100", result.topSpender());
        }

        @Test
        @DisplayName("amounts that are equal but written with different scales still tie")
        void tieIgnoresScale() {
            AnalysisResult result = analyzer.analyze(List.of(
                    debit("B200", "50.00"),
                    debit("A100", "50")));

            assertEquals("A100", result.topSpender());
        }

        @Test
        @DisplayName("in a mixed-case tie, uppercase sorts first")
        void tieUsesCaseSensitiveOrdering() {
            AnalysisResult result = analyzer.analyze(List.of(
                    debit("a100", "50"),
                    debit("B200", "50")));

            assertEquals("B200", result.topSpender());
        }

        @Test
        @DisplayName("is null when no account has any debits")
        void nullWhenNoDebits() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("A100", "50"),
                    credit("B200", "80")));

            assertNull(result.topSpender());
        }
    }

    @Nested
    @DisplayName("Input contract")
    class InputContract {

        @Test
        @DisplayName("a null list throws NullPointerException")
        void nullListThrows() {
            assertThrows(NullPointerException.class, () -> analyzer.analyze(null));
        }

        @Test
        @DisplayName("an empty list gives empty summaries and no top spender")
        void emptyList() {
            AnalysisResult result = analyzer.analyze(List.of());

            assertTrue(result.summaries().isEmpty());
            assertNull(result.topSpender());
        }

        @Test
        @DisplayName("a list of only invalid transactions gives empty summaries and no top spender")
        void onlyInvalidTransactions() {
            AnalysisResult result = analyzer.analyze(List.of(
                    transaction("", "credit", "100"),
                    transaction("A100", "refund", "100"),
                    transaction("B200", "debit", "-5")));

            assertTrue(result.summaries().isEmpty());
            assertNull(result.topSpender());
        }
    }

    @Nested
    @DisplayName("Sorting and result")
    class SortingAndResult {

        @Test
        @DisplayName("summaries are sorted by accountId")
        void summariesAreSorted() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("C300", "10"),
                    credit("A100", "10"),
                    credit("B200", "10")));

            assertEquals(List.of("A100", "B200", "C300"), accountIds(result));
        }

        @Test
        @DisplayName("uppercase accountIds sort before lowercase")
        void uppercaseSortsBeforeLowercase() {
            AnalysisResult result = analyzer.analyze(List.of(
                    credit("a100", "10"),
                    credit("B200", "10")));

            assertEquals(List.of("B200", "a100"), accountIds(result));
        }

        @Test
        @DisplayName("the summaries list cannot be modified")
        void summariesAreImmutable() {
            AnalysisResult result = analyzer.analyze(specExample());

            assertThrows(UnsupportedOperationException.class, () -> result.summaries().clear());
        }
    }

    // ---- helpers ----

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /**
     * Compares by numeric value only. assertEquals would use BigDecimal.equals,
     * which also compares scale and treats 50 and 50.00 as different.
     */
    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private static List<String> accountIds(AnalysisResult result) {
        return result.summaries().stream().map(AccountSummary::accountId).toList();
    }

    private static AccountSummary summaryFor(AnalysisResult result, String accountId) {
        return result.summaries().stream()
                .filter(summary -> summary.accountId().equals(accountId))
                .findFirst()
                .orElseGet(() -> fail("no summary for account " + accountId));
    }
}
