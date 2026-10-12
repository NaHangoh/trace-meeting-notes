package tracenotes.summary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작업 상태·실패 사유와 결과 항목 저장 (SPEC F2, V3 마이그레이션).
 * 상태는 QUEUED → RUNNING → DONE, QUEUED·RUNNING → FAILED로만 바꾼다. 조건부 UPDATE라 이미 끝난 작업을
 * 늦게 온 결과나 실패가 덮어쓰지 않는다. 다시 시도는 FAILED → QUEUED.
 * 결과는 작업에 ON DELETE CASCADE로 묶여 보관 기간 삭제 때 함께 지워진다.
 */
@Repository
public class SummaryRepository {

    private final JdbcTemplate jdbc;

    public SummaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<JobState> findState(String jobId) {
        return jdbc.query("SELECT status, failure FROM job WHERE id = ?",
                (rs, n) -> {
                    String failure = rs.getString("failure");
                    return new JobState(JobStatus.valueOf(rs.getString("status")),
                            failure == null ? null : SummaryFailure.valueOf(failure));
                }, jobId).stream().findFirst();
    }

    /** QUEUED일 때만 RUNNING으로. 바꿨으면 true. */
    public boolean markRunning(String jobId) {
        return jdbc.update("UPDATE job SET status = 'RUNNING' WHERE id = ? AND status = 'QUEUED'", jobId) == 1;
    }

    /**
     * RUNNING일 때만 결과를 저장하고 DONE으로. 상태와 결과는 한 트랜잭션이라, 저장이 실패하면 DONE이 되지 않는다.
     * 근거 번호가 그 작업에 없는 발언이면 DB 제약으로 실패한다 (검증기를 거친 결과만 넘긴다).
     */
    @Transactional
    public boolean saveDone(String jobId, List<SummaryItem> items) {
        if (jdbc.update("UPDATE job SET status = 'DONE' WHERE id = ? AND status = 'RUNNING'", jobId) != 1) {
            return false;
        }
        List<Object[]> itemRows = new ArrayList<>();
        List<Object[]> evidenceRows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SummaryItem item = items.get(i);
            int itemNo = i + 1;
            itemRows.add(new Object[] {jobId, itemNo, item.type().name(), item.text(), item.assignee(), item.due()});
            for (int j = 0; j < item.evidence().size(); j++) {
                evidenceRows.add(new Object[] {jobId, itemNo, j + 1, item.evidence().get(j)});
            }
        }
        jdbc.batchUpdate("INSERT INTO summary_item (job_id, item_no, item_type, content, assignee, due)"
                + " VALUES (?, ?, ?, ?, ?, ?)", itemRows);
        jdbc.batchUpdate("INSERT INTO summary_evidence (job_id, item_no, evidence_order, utterance_no)"
                + " VALUES (?, ?, ?, ?)", evidenceRows);
        return true;
    }

    /** QUEUED·RUNNING일 때만 FAILED로. 바꿨으면 true. */
    public boolean markFailed(String jobId, SummaryFailure failure) {
        return jdbc.update("UPDATE job SET status = 'FAILED', failure = ? WHERE id = ? AND status IN ('QUEUED', 'RUNNING')",
                failure.name(), jobId) == 1;
    }

    /** 다시 시도: FAILED일 때만 QUEUED로 되돌리고 실패 사유를 지운다. 바꿨으면 true. */
    public boolean requeue(String jobId) {
        return jdbc.update("UPDATE job SET status = 'QUEUED', failure = NULL WHERE id = ? AND status = 'FAILED'",
                jobId) == 1;
    }

    /** 서버 시작 시: QUEUED·RUNNING인 작업을 모두 FAILED(reason)로. 바꾼 작업 수. */
    public int failUnfinished(SummaryFailure reason) {
        return jdbc.update("UPDATE job SET status = 'FAILED', failure = ? WHERE status IN ('QUEUED', 'RUNNING')",
                reason.name());
    }

    /** 저장 순서대로 항목을, 항목마다 저장 순서대로 근거 번호를 돌려준다. */
    public List<SummaryItem> findItems(String jobId) {
        Map<Integer, List<Integer>> evidence = new LinkedHashMap<>();
        jdbc.query("SELECT item_no, utterance_no FROM summary_evidence WHERE job_id = ? ORDER BY item_no, evidence_order",
                rs -> {
                    evidence.computeIfAbsent(rs.getInt("item_no"), k -> new ArrayList<>()).add(rs.getInt("utterance_no"));
                }, jobId);
        return jdbc.query("SELECT item_no, item_type, content, assignee, due FROM summary_item WHERE job_id = ? ORDER BY item_no",
                (rs, n) -> new SummaryItem(
                        SummaryItemType.valueOf(rs.getString("item_type")),
                        rs.getString("content"),
                        evidence.getOrDefault(rs.getInt("item_no"), List.of()),
                        rs.getString("assignee"),
                        rs.getString("due")),
                jobId);
    }
}
