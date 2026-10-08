package tracenotes.minutes;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
