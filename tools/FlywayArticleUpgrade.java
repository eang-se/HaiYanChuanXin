import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;

/** Runs only the checked-in Flyway migrations through V17, without booting the application. */
public final class FlywayArticleUpgrade {
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing environment variable " + name);
        return value;
    }
    public static void main(String[] args) {
        Flyway flyway = Flyway.configure()
            .dataSource(required("ARTICLE_UPGRADE_DB_URL"), required("ARTICLE_UPGRADE_DB_USER"), required("ARTICLE_UPGRADE_DB_PASSWORD"))
            .locations("filesystem:" + required("ARTICLE_UPGRADE_MIGRATIONS"))
            .target(MigrationVersion.fromVersion("17"))
            .cleanDisabled(true)
            .load();
        MigrationInfo current = flyway.info().current();
        if (current == null || current.getVersion() == null || current.getVersion().compareTo(MigrationVersion.fromVersion("16")) < 0)
            throw new IllegalStateException("Expected an existing database at V16 or V17; refusing to initialize or upgrade older databases.");
        if (current.getVersion().compareTo(MigrationVersion.fromVersion("17")) > 0)
            throw new IllegalStateException("Database is newer than V17; refusing to migrate it with this tool.");
        int applied = flyway.migrate().migrationsExecuted;
        MigrationInfo after = flyway.info().current();
        if (after == null || !MigrationVersion.fromVersion("17").equals(after.getVersion()))
            throw new IllegalStateException("Expected Flyway history to end at V17 after migration.");
        System.out.println("Flyway schema is at V17; migrations applied: " + applied);
    }
}
