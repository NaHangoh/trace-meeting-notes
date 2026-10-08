package tracenotes.minutes;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 작업과 발언 저장. 발언이 최대 5만 줄이라 엔티티를 하나씩 저장하지 않고 JDBC batch로 넣는다.
 * 발언은 FK의 ON DELETE CASCADE로 작업과 함께 지워진다 (V1 마이그레이션).
 */
@Repository
public class JobRepository {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbc;

    public JobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String jobId, Instant createdAt, List<Utterance> utterances) {
        jdbc.update("INSERT INTO job (id, created_at) VALUES (?, ?)", jobId, utc(createdAt));
        jdbc.batchUpdate(
                "INSERT INTO job_utterance (job_id, utterance_no, speaker, content) VALUES (?, ?, ?, ?)",
                utterances, BATCH_SIZE, (ps, utterance) -> {
                    ps.setString(1, jobId);
                    ps.setInt(2, utterance.no());
                    ps.setString(3, utterance.speaker());
                    ps.setString(4, utterance.text());
                });
    }

    /** created_at이 cutoff보다 이른 작업(과 발언)을 지우고 지운 작업 수를 돌려준다. */
    public int deleteCreatedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM job WHERE created_at < ?", utc(cutoff));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
