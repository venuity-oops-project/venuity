package com.venuity.migrate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MigrationReport {

    public static final String USERS = "users";
    public static final String SETTINGS = "settings";
    public static final String CUSTOMERS = "customers";
    public static final String LEDGER_ROWS = "ledger rows";
    public static final String CHECK_INS = "check-ins";

    private final boolean dryRun;
    private final String sourceDescription;
    private final String targetDescription;
    private final Instant startedAt = Instant.now();
    private final Map<String, Long> sourceCounts = new LinkedHashMap<>();
    private final Map<String, Long> targetBeforeCounts = new LinkedHashMap<>();
    private final Map<String, Long> writtenCounts = new LinkedHashMap<>();
    private final Map<String, Long> targetAfterCounts = new LinkedHashMap<>();
    private final List<String> adjustments = new ArrayList<>();
    private final List<String> roundedValues = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private BigDecimal sourceTotalBalance = BigDecimal.ZERO.setScale(2);
    private BigDecimal targetTotalBalance = BigDecimal.ZERO.setScale(2);
    private int ledgerMismatches;
    private int openingBalances;
    private boolean clean;
    private Instant finishedAt;

    public MigrationReport(boolean dryRun, String sourceDescription, String targetDescription) {
        this.dryRun = dryRun;
        this.sourceDescription = sourceDescription;
        this.targetDescription = targetDescription;
    }

    public void sourceCount(String label, long count) {
        sourceCounts.put(label, count);
    }

    public void targetBeforeCount(String label, long count) {
        targetBeforeCounts.put(label, count);
    }

    public void writtenCount(String label, long count) {
        writtenCounts.put(label, count);
    }

    public void targetAfterCount(String label, long count) {
        targetAfterCounts.put(label, count);
    }

    public void sourceTotalBalance(BigDecimal value) {
        this.sourceTotalBalance = value;
    }

    public void targetTotalBalance(BigDecimal value) {
        this.targetTotalBalance = value;
    }

    public void ledgerMismatches(int value) {
        this.ledgerMismatches = value;
    }

    public void openingBalance() {
        openingBalances++;
    }

    public void adjustment(String value) {
        adjustments.add(value);
    }

    public void roundedValue(String value) {
        roundedValues.add(value);
    }

    public void warning(String value) {
        if (!warnings.contains(value)) {
            warnings.add(value);
        }
    }

    public void finish(boolean clean) {
        this.clean = clean;
        this.finishedAt = Instant.now();
    }

    public boolean isClean() {
        return clean;
    }

    public List<String> getAdjustments() {
        return List.copyOf(adjustments);
    }

    public List<String> getRoundedValues() {
        return List.copyOf(roundedValues);
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    public int getOpeningBalances() {
        return openingBalances;
    }

    public int getLedgerMismatches() {
        return ledgerMismatches;
    }

    public BigDecimal getSourceTotalBalance() {
        return sourceTotalBalance;
    }

    public BigDecimal getTargetTotalBalance() {
        return targetTotalBalance;
    }

    public Map<String, Long> getSourceCounts() {
        return Map.copyOf(sourceCounts);
    }

    public Map<String, Long> getTargetAfterCounts() {
        return Map.copyOf(targetAfterCounts);
    }

    public String summaryLine() {
        return (clean ? "Migration CLEAN" : "Migration FAILED")
                + ": " + sourceCounts.getOrDefault(USERS, 0L) + " users, "
                + sourceCounts.getOrDefault(CUSTOMERS, 0L) + " customers, "
                + sourceCounts.getOrDefault(LEDGER_ROWS, 0L) + " ledger rows, "
                + "total balance " + sourceTotalBalance.toPlainString() + ".";
    }

    public String toMarkdown() {
        StringBuilder out = new StringBuilder();
        out.append("# Venuity SQLite to MySQL migration report\n\n");
        out.append("- Mode: ").append(dryRun ? "DRY RUN (all changes rolled back)" : "APPLIED")
                .append('\n');
        out.append("- Source: ").append(sourceDescription).append('\n');
        out.append("- Target: ").append(targetDescription).append('\n');
        out.append("- Started: ").append(startedAt).append('\n');
        out.append("- Finished: ").append(finishedAt == null ? "in progress" : finishedAt).append('\n');
        if (finishedAt != null) {
            out.append("- Duration: ").append(Duration.between(startedAt, finishedAt).toMillis())
                    .append(" ms\n");
        }
        out.append("- Result: ").append(clean ? "CLEAN" : "FAILED").append("\n\n");

        out.append("## Counts\n\n");
        out.append("| Item | Source | Target before | Written | Target after |\n");
        out.append("| --- | ---: | ---: | ---: | ---: |\n");
        Set<String> labels = new LinkedHashSet<>();
        labels.addAll(sourceCounts.keySet());
        labels.addAll(targetBeforeCounts.keySet());
        labels.addAll(writtenCounts.keySet());
        labels.addAll(targetAfterCounts.keySet());
        for (String label : labels) {
            out.append("| ").append(label)
                    .append(" | ").append(sourceCounts.getOrDefault(label, 0L))
                    .append(" | ").append(targetBeforeCounts.getOrDefault(label, 0L))
                    .append(" | ").append(writtenCounts.getOrDefault(label, 0L))
                    .append(" | ").append(targetAfterCounts.getOrDefault(label, 0L))
                    .append(" |\n");
        }

        out.append("\n## Money\n\n");
        out.append("- Source total balance: ").append(sourceTotalBalance.toPlainString()).append('\n');
        out.append("- Target total balance: ").append(targetTotalBalance.toPlainString()).append('\n');
        out.append("- Ledger mismatches after reconciliation: ").append(ledgerMismatches).append('\n');
        out.append("- Opening-balance rows inserted: ").append(openingBalances).append('\n');
        out.append("- Adjustment rows inserted: ").append(adjustments.size()).append('\n');
        if (!adjustments.isEmpty()) {
            for (String adjustment : adjustments) {
                out.append("  - ").append(adjustment).append('\n');
            }
        }

        out.append("\n## Rounded values\n\n");
        if (roundedValues.isEmpty()) {
            out.append("None.\n");
        } else {
            for (String value : roundedValues) {
                out.append("- ").append(value).append('\n');
            }
        }

        out.append("\n## Warnings\n\n");
        if (warnings.isEmpty()) {
            out.append("None.\n");
        } else {
            for (String warning : warnings) {
                out.append("- ").append(warning).append('\n');
            }
        }
        return out.toString();
    }

    public void write(Path path) {
        try {
            Path absolute = path.toAbsolutePath();
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(absolute, toMarkdown(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new MigrationException("Could not write report to " + path, exception);
        }
    }
}
