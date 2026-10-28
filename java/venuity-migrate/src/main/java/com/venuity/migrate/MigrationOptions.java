package com.venuity.migrate;

import java.nio.file.Path;
import java.time.ZoneId;

public record MigrationOptions(
        Path sqlitePath,
        String mysqlUrl,
        String mysqlUser,
        String mysqlPassword,
        boolean dryRun,
        boolean forceEmptyCheckOff,
        ZoneId sourceTimezone,
        Path reportPath
) {
}
