# Transaction Analyzer

Processes a list of financial transactions and produces per-account summaries and a top spender.

## How to Run

**Requirements:** JDK 17 or later. Maven does not need to be installed: the included Maven Wrapper downloads it on first run.

```
./mvnw test                 # run the unit tests (macOS/Linux)
./mvnw compile exec:java    # run the spec example and print the result as JSON

.\mvnw.cmd test             # Windows equivalents (PowerShell or cmd)
.\mvnw.cmd compile exec:java
```

With Maven installed, `mvn test` and `mvn compile exec:java` work the same way.

The first run downloads JUnit and the Maven plugins. `WARNING` log lines during the run are expected: they come from invalid transactions being skipped.

## Project Structure

```
pom.xml                     Maven build: Java 17, JUnit 5, exec plugin for Main
mvnw, mvnw.cmd, .mvn/       Maven Wrapper scripts and configuration
src/main/java/com/assetmark/analyzer/
  Transaction.java          raw input record; no validation, so invalid input can be represented
  TransactionType.java      credit/debit enum with exact-match parsing
  TransactionAnalyzer.java  validation, aggregation, sorting and top spender
  AccountSummary.java       per-account result record
  AnalysisResult.java       summaries plus topSpender
  JsonFormatter.java        prints a result as JSON in the spec's format
  Main.java                 runs the spec example
src/test/java/com/assetmark/analyzer/
  TransactionAnalyzerTest.java
  JsonFormatterTest.java
  TestData.java             shared test builders
```

## Assumptions

### Input

- The input is an in-memory `List<Transaction>`.
- A `null` list throws a `NullPointerException`. An empty list returns empty summaries and a `null` `topSpender`.
- A `null` entry in the list counts as an invalid transaction.

### Validation

A transaction is invalid, and is skipped, if any of the following hold:

- **`accountId`** is `null`, empty, or whitespace-only. All other IDs are accepted as-is, with no format check. The example IDs follow a capital-letter-plus-digits pattern, but the spec only lists missing/empty as invalid, so no pattern is enforced.
- **`type`** is anything other than exactly `"credit"` or `"debit"`, including `null`, `"Credit"`, and `"CREDIT"`.
- **`amount`** is `null` or `<= 0`.

### Behavior

- Timestamps are carried on the record but not parsed or validated. The spec does not list a bad timestamp as invalid, and no output depends on the timestamp, so unsorted input does not affect results.
- Transactions have no currency field, so all amounts are assumed to be in the same currency. Summing across currencies would require conversion, which is out of scope.
- Invalid transactions are logged and skipped, and contribute nothing to any result.
- An account whose transactions are all invalid does not appear in the output.
- `largestTransaction` is the largest single amount across both credits and debits.
- `transactionCount` counts valid transactions only.
- `overdrawn` is `true` only when `endingBalance < 0`. A balance of exactly 0 is not overdrawn.
- `topSpender` is the account with the highest `totalDebits`. Ties go to the lexicographically smaller `accountId`. If there are no valid transactions, or no account has any debits, `topSpender` is `null`.
- "Lexicographic" means `String.compareTo`, which compares UTF-16 code units, so all uppercase letters sort before lowercase (e.g. `"B100" < "a100"`). The same order is used for sorting summaries and breaking `topSpender` ties.
- Identical transactions are counted separately. The schema has no transaction ID, so two identical transactions cannot be told apart from a legitimate repeat.
- When equal amounts are written at different scales (`100` and `100.00`), `largestTransaction` keeps the larger scale, so the result does not depend on input order.
- Printed amounts keep their input scale: `500` prints as `500`, and `500.00` prints as `500.00`. An account with no credits prints `totalCredits` as `0`.

## Tradeoffs and Design Choices

- **`BigDecimal` for amounts.** A `double` is a binary float and cannot represent values like 0.10 exactly, so sums drift. `BigDecimal` stores decimal digits as a whole number plus a decimal-place count, so arithmetic is exact. Comparisons use `compareTo` rather than `equals`, because `equals` compares value and scale (`100.0` ≠ `100.00`), while `compareTo` compares numeric value only.
- **No assumption of 2 decimal places.** The spec does not specify a currency, and values such as share quantities can exceed 2 decimal places.
- **`HashMap` plus one final sort.** A `HashMap` of per-account accumulators gives O(n) aggregation plus an O(k log k) sort at the end. A `TreeMap` keeps keys sorted on every insert, at O(log k) per lookup, for O(n log k) total.
- **Transactions are not stored.** The spec does not require it, and keeping only per-account totals keeps memory at O(k).
- **Mutable `Accumulator`, immutable `AccountSummary`.** Running totals are kept in a mutable internal accumulator and converted into an immutable summary at the end. The balance and overdrawn flag are computed once, after all transactions are processed, so they cannot fall out of sync with the totals.
- **`type` stored as a raw `String`.** The analyzer parses it into an enum. If the record held the enum directly, a transaction with an invalid type could not be constructed, and the invalid-type rule could not be tested.
- **No I/O in the analyzer.** Apart from warning logs through `java.util.logging`, the analyzer is a pure function, which keeps it easy to test. Printing happens only in `Main`.
- **Invalid transactions are logged and skipped.** No exception is thrown and no error field is added to the output, because the spec says to ignore invalid transactions and its output format has no place for errors. The log line includes the full transaction, account ID and amount included, to make debugging easy; production code would mask or omit these fields.
- **Accounts with no valid transactions are omitted.** The spec says to ignore invalid transactions, so they should not affect the output. An all-zero row would also have no meaningful `largestTransaction`.
- **No account ID format check.** No format is specified, and an invented rule could cause valid transactions in the test data to be skipped.
- **Exact type matching.** The spec names exactly two values. Silently accepting variants like `CREDIT` would guess at intent and could hide data problems upstream.
- **Hand-written JSON output.** Printed without a third-party library to avoid a dependency. It escapes quotes, backslashes, and control characters, and prints a `null` `topSpender` as a bare `null`.
- **Amounts printed at input scale.** Avoids rounding or padding values, which could misrepresent them.

## Note on AI Assistance

Developed with assistance from Claude.
