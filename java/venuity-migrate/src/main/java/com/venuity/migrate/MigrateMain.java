package com.venuity.migrate;

import picocli.CommandLine;

import java.nio.file.Path;
import java.time.ZoneId;

@CommandLine.Command(
        name = "venuity-migrate",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "Migrate legacy Venuity SQLite data into MySQL."
)
public final class MigrateMain implements java.util.concurrent.Callable<Integer> {

    @CommandLine.Option(
            names = "--sqlite",
            required = true,
            paramLabel = "PATH",
            description = "Path to the legacy Django SQLite database."
    )
    private Path sqlitePath;

    @CommandLine.Option(
            names = "--mysql-url",
            required = true,
            paramLabel = "URL",
            description = "MySQL JDBC URL, without credentials."
    )
    private String mysqlUrl;

    @CommandLine.Option(
            names = "--mysql-user",
            required = true,
            paramLabel = "USER",
            description = "MySQL user."
    )
    private String mysqlUser;

    @CommandLine.Option(
            names = "--mysql-password-env",
            required = true,
            paramLabel = "VARIABLE",
            description = "Environment variable containing the MySQL password."
    )
    private String mysqlPasswordEnvironment;

    @CommandLine.Option(
            names = "--force-empty-check-off",
            description = "Delete existing target data and migrate into a non-empty target."
    )
    private boolean forceEmptyCheckOff;

    @CommandLine.Option(
            names = "--dry-run",
            description = "Run the complete migration and roll back before committing."
    )
    private boolean dryRun;

    @CommandLine.Option(
            names = "--report",
            paramLabel = "PATH",
            description = "Write the migration report to a Markdown file."
    )
    private Path reportPath;

    @CommandLine.Option(
            names = "--source-timezone",
            defaultValue = "UTC",
            paramLabel = "ZONE",
            description = "Timezone for naive SQLite timestamps (default: UTC)."
    )
    private String sourceTimezone;

    @Override
    public Integer call() {
        try {
            ZoneId zone = ZoneId.of(sourceTimezone);
            String password = System.getenv(mysqlPasswordEnvironment);
            if (password == null) {
                throw new MigrationException(
                        "Environment variable " + mysqlPasswordEnvironment + " is not set."
                );
            }
            MigrationOptions options = new MigrationOptions(
                    sqlitePath,
                    mysqlUrl,
                    mysqlUser,
                    password,
                    dryRun,
                    forceEmptyCheckOff,
                    zone,
                    reportPath
            );
            MigrationReport report = new MigrationRunner(options).run();
            if (reportPath == null) {
                System.out.println(report.toMarkdown());
            } else {
                report.write(reportPath);
                System.out.println("Migration report written to " + reportPath.toAbsolutePath());
                System.out.println(report.summaryLine());
            }
            return 0;
        } catch (MigrationException exception) {
            System.err.println("Migration failed: " + exception.getMessage());
            return 1;
        } catch (RuntimeException exception) {
            System.err.println("Migration failed: " + exception.getMessage());
            return 1;
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MigrateMain()).execute(args);
        System.exit(exitCode);
    }
}
