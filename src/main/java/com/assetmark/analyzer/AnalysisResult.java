package com.assetmark.analyzer;

import java.util.List;

/**
 * The full output of an analysis.
 *
 * @param summaries  one summary per account, sorted by accountId
 * @param topSpender the accountId with the highest total debits, or null if no account has any debits
 */
public record AnalysisResult(List<AccountSummary> summaries, String topSpender) {

    public AnalysisResult {
        summaries = List.copyOf(summaries); // immutable, so callers cannot modify the result
    }
}
