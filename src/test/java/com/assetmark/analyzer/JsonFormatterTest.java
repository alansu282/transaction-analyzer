package com.assetmark.analyzer;

import static com.assetmark.analyzer.TestData.specExample;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonFormatter")
class JsonFormatterTest {

    @Test
    @DisplayName("prints the spec example exactly as the spec's expected output")
    void matchesSpecOutputExactly() {
        AnalysisResult result = new TransactionAnalyzer().analyze(specExample());

        String expected = """
                {
                  "summaries": [
                    {
                      "accountId": "A100",
                      "totalCredits": 500,
                      "totalDebits": 200,
                      "endingBalance": 300,
                      "largestTransaction": 500,
                      "transactionCount": 2,
                      "overdrawn": false
                    },
                    {
                      "accountId": "B200",
                      "totalCredits": 20,
                      "totalDebits": 50,
                      "endingBalance": -30,
                      "largestTransaction": 50,
                      "transactionCount": 2,
                      "overdrawn": true
                    }
                  ],
                  "topSpender": "A100"
                }""";
        assertEquals(expected, JsonFormatter.format(result));
    }

    @Test
    @DisplayName("empty summaries print as [] and a null topSpender as a bare null")
    void emptyResult() {
        String json = JsonFormatter.format(new AnalysisResult(List.of(), null));

        assertEquals("{\n  \"summaries\": [],\n  \"topSpender\": null\n}", json);
        assertFalse(json.contains("\"null\""));
    }

    @Test
    @DisplayName("escapes quotes, backslashes and control characters in strings")
    void escapesSpecialCharacters() {
        assertEquals("\"A\\\"100\"", JsonFormatter.quote("A\"100"));
        assertEquals("\"A\\\\100\"", JsonFormatter.quote("A\\100"));
        assertEquals("\"A\\n100\"", JsonFormatter.quote("A\n100"));
        assertEquals("\"A\\t100\"", JsonFormatter.quote("A\t100"));
        assertEquals("\"A\\u0001100\"", JsonFormatter.quote("A\u0001100"));
    }

    @Test
    @DisplayName("amounts keep their input scale")
    void preservesScale() {
        String json = JsonFormatter.format(resultWithAmount(new BigDecimal("500.00")));

        assertTrue(json.contains("\"totalCredits\": 500.00"), json);
    }

    @Test
    @DisplayName("amounts never use scientific notation")
    void avoidsScientificNotation() {
        String json = JsonFormatter.format(resultWithAmount(new BigDecimal("1E+20")));

        assertTrue(json.contains("\"totalCredits\": 100000000000000000000"), json);
        assertFalse(json.contains("E+"), json);
    }

    /** A one-account result where every amount field is the given value. */
    private static AnalysisResult resultWithAmount(BigDecimal amount) {
        AccountSummary summary = new AccountSummary("A100", amount, amount, amount, amount, 1, false);
        return new AnalysisResult(List.of(summary), "A100");
    }
}
