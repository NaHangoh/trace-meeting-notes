package tracenotes.minutes;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    /** 작업과 발언은 한 트랜잭션이다. 커밋된 뒤에 대기열에 넣어야 작업자가 발언을 읽을 수 있다 (JobService). */
    @Transactional
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

    /** 작업의 발언을 번호 순서대로. 작업이 없으면(보관 기간 삭제 등) 빈 목록. */
    public List<Utterance> findUtterances(String jobId) {
        return jdbc.query("SELECT utterance_no, speaker, content FROM job_utterance WHERE job_id = ? ORDER BY utterance_no",
                (rs, n) -> new Utterance(rs.getInt("utterance_no"), rs.getString("speaker"), rs.getString("content")),
                jobId);
    }

    /** created_at이 cutoff보다 이른 작업(과 발언)을 지우고 지운 작업 수를 돌려준다. */
    public int deleteCreatedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM job WHERE created_at < ?", utc(cutoff));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
