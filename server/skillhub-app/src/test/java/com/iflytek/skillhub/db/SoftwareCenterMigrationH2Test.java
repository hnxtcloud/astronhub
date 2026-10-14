package com.iflytek.skillhub.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * Applies V70–V73 (software center) against H2 in PostgreSQL compatibility mode,
 * with the prerequisite auth tables from V1 stubbed in. The full migration
 * chain cannot run on H2 because early skill migrations use PostgreSQL-only
 * full-text types (tsvector), so this test scopes the smoke check to the new
 * migration's DDL, seed logic, and idempotency guards. Real-database chain
 * verification happens in staging.
 */
class SoftwareCenterMigrationH2Test {

    private static final String URL = "jdbc:h2:mem:migration-check-%d;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            .formatted(System.nanoTime());

    @Test
    void downloadSessionTimestampMigration_preservesLastActivityAndDefaultsNewRows() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
            statement.execute("""
                    CREATE TABLE software_download_session (
                        id VARCHAR(64) PRIMARY KEY,
                        last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )""");
            statement.execute("""
                    INSERT INTO software_download_session VALUES
                    ('legacy', TIMESTAMP WITH TIME ZONE '2026-09-29 01:00:00+00')""");

            statement.execute(readMigration("db/migration/V73__software_download_session_updated_at.sql"));

            ResultSet legacy = statement.executeQuery("""
                    SELECT updated_at, last_activity_at FROM software_download_session WHERE id = 'legacy'""");
            assertThat(legacy.next()).isTrue();
            assertThat(legacy.getObject(1)).isEqualTo(legacy.getObject(2));

            statement.execute("""
                    INSERT INTO software_download_session (id, last_activity_at) VALUES ('new', CURRENT_TIMESTAMP)""");
            ResultSet inserted = statement.executeQuery("""
                    SELECT updated_at FROM software_download_session WHERE id = 'new'""");
            assertThat(inserted.next()).isTrue();
            assertThat(inserted.getObject(1)).isNotNull();
        }
    }

    @Test
    void softwareCenterMigration_applies_andSeedsExpectedState() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DOMAIN IF NOT EXISTS JSONB AS JSON");
            statement.execute("CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
            statement.execute("""
                    CREATE TABLE role (
                        id BIGSERIAL PRIMARY KEY,
                        code VARCHAR(64) NOT NULL UNIQUE,
                        name VARCHAR(128) NOT NULL,
                        description VARCHAR(255),
                        is_system BOOLEAN NOT NULL DEFAULT FALSE
                    )""");
            statement.execute("""
                    CREATE TABLE permission (
                        id BIGSERIAL PRIMARY KEY,
                        code VARCHAR(128) NOT NULL UNIQUE,
                        name VARCHAR(128) NOT NULL,
                        group_code VARCHAR(64)
                    )""");
            statement.execute("""
                    CREATE TABLE role_permission (
                        id BIGSERIAL PRIMARY KEY,
                        role_id BIGINT NOT NULL,
                        permission_id BIGINT NOT NULL,
                        CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role(id),
                        CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission(id),
                        CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
                    )""");

            statement.execute(shimmed("db/migration/V70__software_center.sql"));
            statement.execute(shimmed("db/migration/V71__software_center_transfer.sql"));
            statement.execute(shimmed("db/migration/V72__software_center_inspection.sql"));
            statement.execute(readMigration("db/migration/V73__software_download_session_updated_at.sql"));

            for (String table : new String[] {
                    "software_category", "software_application", "software_release",
                    "software_package_revision", "software_recommendation",
                    "software_policy_revision", "software_policy_state"}) {
                ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + table + "'");
                rs.next();
                assertThat(rs.getInt(1)).as("table %s exists", table).isEqualTo(1);
            }

            ResultSet state = statement.executeQuery(
                    "SELECT active_revision_id FROM software_policy_state WHERE id = 1");
            assertThat(state.next()).as("policy state singleton row exists").isTrue();
            long activeRevisionId = state.getLong(1);

            ResultSet seed = statement.executeQuery(
                    "SELECT id, schema_version FROM software_policy_revision WHERE revision = 1");
            assertThat(seed.next()).as("policy revision 1 seeded").isTrue();
            assertThat(seed.getLong(1)).isEqualTo(activeRevisionId);
            assertThat(seed.getInt(2)).isEqualTo(1);

            ResultSet revisionCount = statement.executeQuery(
                    "SELECT COUNT(*) FROM software_policy_revision");
            revisionCount.next();
            assertThat(revisionCount.getInt(1)).as("exactly one seeded policy revision").isEqualTo(1);

            ResultSet inspectionTables = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN "
                    + "('software_inspection_task', 'software_inspection_attempt')");
            inspectionTables.next();
            assertThat(inspectionTables.getInt(1))
                    .as("inspection task and attempt tables exist").isEqualTo(2);

            ResultSet taskColumns = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = "
                    + "'software_inspection_task' AND column_name IN ('attempt_token', "
                    + "'lease_owner', 'lease_expires_at', 'next_attempt_not_before', "
                    + "'manual_retry_count', 'policy_revision')");
            taskColumns.next();
            assertThat(taskColumns.getInt(1))
                    .as("inspection task carries lease and policy-snapshot columns").isEqualTo(6);

            ResultSet role = statement.executeQuery(
                    "SELECT COUNT(*) FROM role WHERE code = 'SOFTWARE_ADMIN'");
            role.next();
            assertThat(role.getInt(1)).as("SOFTWARE_ADMIN role seeded").isEqualTo(1);

            ResultSet permissions = statement.executeQuery("""
                    SELECT COUNT(*) FROM role_permission rp
                    JOIN role r ON r.id = rp.role_id
                    JOIN permission p ON p.id = rp.permission_id
                    WHERE r.code = 'SOFTWARE_ADMIN'""");
            permissions.next();
            assertThat(permissions.getInt(1))
                    .as("SOFTWARE_ADMIN bound to manage/publish/settings.read only")
                    .isEqualTo(3);
        }
    }

    /** Applies the documented H2 syntax shims to a software-center migration. */
    private String shimmed(String classpathLocation) throws IOException {
        return readMigration(classpathLocation)
                .replace("BIGSERIAL", "BIGINT GENERATED BY DEFAULT AS IDENTITY")
                // H2 cannot parse functional indexes; flatten to plain-column
                // indexes. PostgreSQL-only COALESCE scoping is verified in staging.
                .replace("COALESCE(target_distro, '')", "target_distro")
                // H2 accepts ON CONFLICT only without a conflict target; each
                // seeded table has exactly one unique constraint, so semantics match.
                .replaceAll("ON CONFLICT [(](?:code|id)[)] DO NOTHING", "ON CONFLICT DO NOTHING");
    }

    private String readMigration(String classpathLocation) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(classpathLocation)) {
            assertThat(in).as("migration %s on classpath", classpathLocation).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
