package tracenotes.minutes;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** SPEC S2: 보관 기간(기본 24시간, 설정값)이 지난 작업과 발언을 지운다. 스케줄 메서드를 직접 호출한다. */
class RetentionCleanupJobTest {

    /** PreviewRateLimitTest.FixedClockConfig의 시각. */
    static final Instant NOW = Instant.parse("2026-10-08T09:00:30Z");

    static String insertJob(JdbcTemplate jdbc, Instant createdAt) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO job (id, created_at) VALUES (?, ?)", id, Timestamp.from(createdAt));
        jdbc.update("INSERT INTO job_utterance (job_id, utterance_no, speaker, content) VALUES (?, 1, ?, ?)",
                id, "미상", "내용");
        return id;
    }

    static int jobs(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM job WHERE id = ?", Integer.class, id);
    }

    static int utterances(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM job_utterance WHERE job_id = ?", Integer.class, id);
    }

    /** 테이블 이름은 테스트 안의 고정 값만 넘긴다. */
    static int rows(JdbcTemplate jdbc, String table, String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE job_id = ?", Integer.class, id);
    }

    @Nested
    @SpringBootTest
    @Import(PreviewRateLimitTest.FixedClockConfig.class)
    class DefaultRetention {

        @Autowired
        JdbcTemplate jdbc;

        @Autowired
        RetentionCleanupJob cleanup;

        @BeforeEach
        void clear() {
            jdbc.update("DELETE FROM job");
        }

        @Test
        void deletesJobsAndUtterancesOlderThanRetention() {
            String old = insertJob(jdbc, NOW.minus(Duration.ofHours(24).plusMinutes(1)));

            cleanup.deleteExpired();

            assertThat(jobs(jdbc, old)).isZero();
            assertThat(utterances(jdbc, old)).isZero();
        }

        /** SPEC F2·S2: 결과 항목과 근거도 보관 기간이 지나면 작업과 함께 지워진다. */
        @Test
        void resultsDeletedWithJobAfterRetention() {
            String old = insertJob(jdbc, NOW.minus(Duration.ofHours(24).plusMinutes(1)));
            String recent = insertJob(jdbc, NOW.minus(Duration.ofHours(23).plusMinutes(59)));
            for (String id : List.of(old, recent)) {
                jdbc.update("UPDATE job SET status = 'DONE' WHERE id = ?", id);
                jdbc.update("INSERT INTO summary_item (job_id, item_no, item_type, content) VALUES (?, 1, 'DECISION', ?)",
                        id, "결정 내용");
                jdbc.update("INSERT INTO summary_evidence (job_id, item_no, evidence_order, utterance_no)"
                        + " VALUES (?, 1, 1, 1)", id);
            }

            cleanup.deleteExpired();

            assertThat(rows(jdbc, "summary_item", old)).isZero();
            assertThat(rows(jdbc, "summary_evidence", old)).isZero();
            assertThat(rows(jdbc, "summary_item", recent)).isEqualTo(1);
            assertThat(rows(jdbc, "summary_evidence", recent)).isEqualTo(1);
        }

        @Test
        void keepsJobsWithinRetention() {
            String recent = insertJob(jdbc, NOW.minus(Duration.ofHours(23).plusMinutes(59)));

            cleanup.deleteExpired();

            assertThat(jobs(jdbc, recent)).isEqualTo(1);
            assertThat(utterances(jdbc, recent)).isEqualTo(1);
        }
    }

    @Nested
    @SpringBootTest(properties = "app.retention=1h")
    @Import(PreviewRateLimitTest.FixedClockConfig.class)
    class ConfiguredRetention {

        @Autowired
        JdbcTemplate jdbc;

        @Autowired
        RetentionCleanupJob cleanup;

        @Test
        void retentionIsConfigurable() {
            jdbc.update("DELETE FROM job");
            String old = insertJob(jdbc, NOW.minus(Duration.ofMinutes(61)));
            String recent = insertJob(jdbc, NOW.minus(Duration.ofMinutes(59)));

            cleanup.deleteExpired();

            assertThat(jobs(jdbc, old)).isZero();
            assertThat(jobs(jdbc, recent)).isEqualTo(1);
        }
    }
}
