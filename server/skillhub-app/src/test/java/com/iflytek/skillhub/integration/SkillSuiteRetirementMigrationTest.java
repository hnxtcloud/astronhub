package com.iflytek.skillhub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSuiteRetirementMigrationTest {
    @Test
    void grantsCompletionOnlyToPendingReviewsAndConfirmedBundleResults() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:suite-retirement;MODE=PostgreSQL")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE skill_suite_version (id BIGINT PRIMARY KEY, status VARCHAR(32))");
            sql.execute("CREATE TABLE skill_suite_bundle_operation (operation_id VARCHAR(64), status VARCHAR(32), result_suite_version_id BIGINT)");
            sql.execute("INSERT INTO skill_suite_version VALUES (1,'PENDING_REVIEW'),(2,'DRAFT'),(3,'DRAFT'),(4,'REJECTED')");
            sql.execute("INSERT INTO skill_suite_bundle_operation VALUES ('confirmed','RUNNING',NULL),('finished','SUITE_DRAFT_CREATED',3),('cancelled','CANCELLED',NULL),('stale','REPREVIEW_REQUIRED',NULL)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V85__retire_suite_creation.sql"));
            try (var rows = sql.executeQuery("SELECT id FROM skill_suite_version WHERE retirement_completion_allowed ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(1L);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(3L);
                assertThat(rows.next()).isFalse();
            }
            sql.execute("INSERT INTO skill_suite_version(id,status) VALUES (5,'PENDING_REVIEW')");
            try (var rows = sql.executeQuery("SELECT retirement_completion_allowed FROM skill_suite_version WHERE id=5")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM skill_suite_bundle_operation WHERE retirement_completion_allowed")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }
}
