package tracenotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tracenotes.summary.SummaryFailure;

@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Environment environment;

    @Test
    void v1IsAppliedOnStartup() {
        List<MigrationInfo> applied = Arrays.asList(flyway.info().applied());

        assertThat(applied)
                .anySatisfy(info -> {
                    assertThat(info.getVersion().getVersion()).isEqualTo("1");
                    assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS);
                });
    }

    @Test
    void tablesComeFromMigration() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                String.class);

        assertThat(tables).contains("JOB", "JOB_UTTERANCE");
    }

    /** SPEC F2 저장: V3는 작업 상태·실패 사유와 결과 항목·근거 테이블을 만든다. */
    @Test
    void v3MigrationApplies() {
        assertThat(Arrays.asList(flyway.info().applied()))
                .anySatisfy(info -> {
                    assertThat(info.getVersion().getVersion()).isEqualTo("3");
                    assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS);
                });
        assertThat(jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'", String.class))
                .contains("SUMMARY_ITEM", "SUMMARY_EVIDENCE");
        assertThat(jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'JOB'", String.class))
                .contains("STATUS", "FAILURE");
    }

    /** 상태·실패 사유는 정해진 값만, 실패 사유는 FAILED일 때만 들어간다 (DB 제약). */
    @Test
    @Transactional
    void jobStatusConstraints() {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO job (id, created_at) VALUES (?, CURRENT_TIMESTAMP)", id);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE job SET status = 'UNKNOWN' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE job SET status = 'FAILED', failure = 'UNKNOWN' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE job SET status = 'FAILED' WHERE id = ?", id))
                .as("FAILED without failure").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE job SET status = 'DONE', failure = 'TIMEOUT' WHERE id = ?", id))
                .as("failure on DONE").isInstanceOf(DataIntegrityViolationException.class);

        for (String failure : List.of("INVALID_OUTPUT", "TIMEOUT", "CONNECTION_REFUSED", "LLM_ERROR",
                "SERVER_RESTART", "INTERNAL_ERROR")) {
            jdbcTemplate.update("UPDATE job SET status = 'FAILED', failure = ? WHERE id = ?", failure, id);
        }
        for (String status : List.of("QUEUED", "RUNNING", "DONE")) {
            jdbcTemplate.update("UPDATE job SET status = ?, failure = NULL WHERE id = ?", status, id);
        }
    }

    /** DB의 실패 사유 제약과 SummaryFailure 값이 같다 (V4에서 INTERNAL_ERROR 추가). */
    @Test
    @Transactional
    void everySummaryFailureFitsConstraint() {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO job (id, created_at) VALUES (?, CURRENT_TIMESTAMP)", id);

        for (SummaryFailure failure : SummaryFailure.values()) {
            jdbcTemplate.update("UPDATE job SET status = 'FAILED', failure = ? WHERE id = ?", failure.name(), id);
        }
    }

    /** 항목 종류는 정해진 값만 들어간다. */
    @Test
    @Transactional
    void summaryItemTypeConstraint() {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO job (id, created_at) VALUES (?, CURRENT_TIMESTAMP)", id);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO summary_item (job_id, item_no, item_type, content) VALUES (?, 1, 'NOTE', '내용')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void hibernateDoesNotRunDdl() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }
}
