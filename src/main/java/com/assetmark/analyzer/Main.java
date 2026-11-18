package com.assetmark.analyzer;

import java.math.BigDecimal;
import java.util.List;

/** Runs the analyzer on the example input from the spec and prints the result as JSON. */
public final class Main {

    public static void main(String[] args) {
        List<Transaction> transactions = List.of(
                new Transaction("A100", "credit", new BigDecimal("500"), "2026-05-10T10:00:00Z"),
                new Transaction("A100", "debit", new BigDecimal("200"), "2026-05-10T12:00:00Z"),
                new Transaction("B200", "debit", new BigDecimal("50"), "2026-05-10T11:00:00Z"),
                new Transaction("B200", "credit", new BigDecimal("20"), "2026-05-10T13:00:00Z"),
                new Transaction("", "credit", new BigDecimal("100"), "2026-05-10T14:00:00Z"));

        AnalysisResult result = new TransactionAnalyzer().analyze(transactions);
        System.out.println(JsonFormatter.format(result));
    }
}
