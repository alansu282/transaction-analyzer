package com.assetmark.analyzer;

import java.math.BigDecimal;
import java.util.List;

/**
 * Formats an {@link AnalysisResult} as JSON in the shape given by the spec.
 *
 * <p>Hand-written to avoid a third-party dependency. Amounts are printed at their
 * input scale with no rounding or padding.
 */
public final class JsonFormatter {

    private static final String INDENT = "  ";

    private JsonFormatter() {
        // static utility class
    }

    public static String format(AnalysisResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append(INDENT).append("\"summaries\": ");
        appendSummaries(json, result.summaries());
        json.append(",\n");
        json.append(INDENT).append("\"topSpender\": ").append(stringOrNull(result.topSpender())).append('\n');
        json.append('}');
        return json.toString();
    }

    private static void appendSummaries(StringBuilder json, List<AccountSummary> summaries) {
        if (summaries.isEmpty()) {
            json.append("[]");
            return;
        }
        json.append("[\n");
        for (int i = 0; i < summaries.size(); i++) {
            appendSummary(json, summaries.get(i));
            boolean isLast = i == summaries.size() - 1;
            json.append(isLast ? "\n" : ",\n");
        }
        json.append(INDENT).append(']');
    }

    private static void appendSummary(StringBuilder json, AccountSummary summary) {
        String fieldIndent = INDENT.repeat(3);
        json.append(INDENT.repeat(2)).append("{\n");
        json.append(fieldIndent).append("\"accountId\": ").append(quote(summary.accountId())).append(",\n");
        json.append(fieldIndent).append("\"totalCredits\": ").append(number(summary.totalCredits())).append(",\n");
        json.append(fieldIndent).append("\"totalDebits\": ").append(number(summary.totalDebits())).append(",\n");
        json.append(fieldIndent).append("\"endingBalance\": ").append(number(summary.endingBalance())).append(",\n");
        json.append(fieldIndent).append("\"largestTransaction\": ").append(number(summary.largestTransaction())).append(",\n");
        json.append(fieldIndent).append("\"transactionCount\": ").append(summary.transactionCount()).append(",\n");
        json.append(fieldIndent).append("\"overdrawn\": ").append(summary.overdrawn()).append('\n');
        json.append(INDENT.repeat(2)).append('}');
    }

    /** toPlainString avoids scientific notation (e.g. "5E+2") that toString can produce. */
    private static String number(BigDecimal value) {
        return value.toPlainString();
    }

    /** A null string becomes the JSON literal null, not the string "null". */
    private static String stringOrNull(String value) {
        return value == null ? "null" : quote(value);
    }

    /** Wraps a string in quotes, escaping characters that would otherwise break the JSON. */
    static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                case '\b' -> quoted.append("\\b");
                case '\f' -> quoted.append("\\f");
                default -> {
                    if (c < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) c)); // other control characters
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }
}
