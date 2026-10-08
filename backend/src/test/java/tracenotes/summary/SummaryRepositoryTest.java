package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tracenotes.minutes.JobRepository;
import tracenotes.minutes.Utterance;

/** SPEC F2 저장: 작업 상태·실패 사유와 결과 항목 (V3 마이그레이션). */
@SpringBootTest
class SummaryRepositoryTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JobRepository jobs;

    @Autowired
    SummaryRepository repository;

    private String jobId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM job");
        jobId = UUID.randomUUID().toString();
        jobs.insert(jobId, Instant.parse("2026-10-08T09:00:00Z"), List.of(
                new Utterance(1, "참석자", "첫 발언"),
                new Utterance(2, "진행자", "둘째 발언"),
                new Utterance(3, "참석자", "셋째 발언")));
    }

    private static SummaryItem todo(String text, List<Integer> evidence, String assignee, String due) {
        return new SummaryItem(SummaryItemType.TODO, text, evidence, assignee, due);
    }

    @Test
    void newJobIsQueued() {
        assertThat(repository.findState(jobId)).contains(new JobState(JobStatus.QUEUED, null));
    }

    @Test
    void unknownJobHasNoState() {
        assertThat(repository.findState(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void statusTransitionsPersisted() {
        assertThat(repository.markRunning(jobId)).isTrue();
        assertThat(repository.findState(jobId)).contains(new JobState(JobStatus.RUNNING, null));

        List<SummaryItem> items = List.of(
                new SummaryItem(SummaryItemType.DECISION, "결정", List.of(2), null, null),
                todo("할 일", List.of(3, 1), "참석자", "금요일"));
        assertThat(repository.saveDone(jobId, items)).isTrue();

        assertThat(repository.findState(jobId)).contains(new JobState(JobStatus.DONE, null));
        assertThat(repository.findItems(jobId)).containsExactlyElementsOf(items);
    }

    @Test
    void failurePersistedWithReason() {
        repository.markRunning(jobId);

        assertThat(repository.markFailed(jobId, SummaryFailure.TIMEOUT)).isTrue();

        assertThat(repository.findState(jobId)).contains(new JobState(JobStatus.FAILED, SummaryFailure.TIMEOUT));
        assertThat(repository.findItems(jobId)).isEmpty();
    }

    @Test
    void queuedJobCanFailWithoutRunning() {
        assertThat(repository.markFailed(jobId, SummaryFailure.CONNECTION_REFUSED)).isTrue();

        assertThat(repository.findState(jobId))
                .contains(new JobState(JobStatus.FAILED, SummaryFailure.CONNECTION_REFUSED));
    }

    /** 상태는 정해진 순서로만 바뀐다. 이미 끝난 작업에 늦게 온 결과·실패가 덮어쓰지 않는다. */
    @Test
    void invalidTransitionsAreIgnored() {
        assertThat(repository.saveDone(jobId, List.of())).as("QUEUED → DONE").isFalse();

        repository.markRunning(jobId);
        assertThat(repository.markRunning(jobId)).as("RUNNING → RUNNING").isFalse();
        repository.markFailed(jobId, SummaryFailure.INVALID_OUTPUT);

        assertThat(repository.markRunning(jobId)).as("FAILED → RUNNING").isFalse();
        assertThat(repository.saveDone(jobId, List.of(new SummaryItem(
                SummaryItemType.DISCUSSION, "늦은 결과", List.of(1), null, null)))).as("FAILED → DONE").isFalse();
        assertThat(repository.markFailed(jobId, SummaryFailure.TIMEOUT)).as("FAILED → FAILED").isFalse();

        assertThat(repository.findState(jobId))
                .contains(new JobState(JobStatus.FAILED, SummaryFailure.INVALID_OUTPUT));
        assertThat(repository.findItems(jobId)).isEmpty();
    }

    @Test
    void doneJobCannotFail() {
        repository.markRunning(jobId);
        repository.saveDone(jobId, List.of());

        assertThat(repository.markFailed(jobId, SummaryFailure.TIMEOUT)).isFalse();
        assertThat(repository.findState(jobId)).contains(new JobState(JobStatus.DONE, null));
    }

    /** 근거 없는 항목, 담당자·기한 없는 항목, 근거 순서가 그대로 돌아온다. */
    @Test
    void itemsRoundTripKeepsOrderAndNulls() {
        repository.markRunning(jobId);
        List<SummaryItem> items = List.of(
                new SummaryItem(SummaryItemType.AGENDA, "안건", List.of(1), null, null),
                new SummaryItem(SummaryItemType.DISCUSSION, "근거 없는 논의", List.of(), null, null),
                todo("담당자만", List.of(3, 2, 1), "진행자", null),
                todo("기한만", List.of(2), null, "다음 주"));

        repository.saveDone(jobId, items);

        List<SummaryItem> found = repository.findItems(jobId);
        assertThat(found).containsExactlyElementsOf(items);
        assertThat(found.get(1).hasEvidence()).isFalse();
    }

    /** 항목 길이 상한(500자, 담당자·기한 100자)은 코드 포인트라 보충 평면 문자로 채워도 저장된다. */
    @Test
    void maxLengthSupplementaryCharactersFit() {
        repository.markRunning(jobId);
        String emoji = Character.toString(0x1F600);
        List<SummaryItem> items = List.of(todo(emoji.repeat(500), List.of(1), emoji.repeat(100), emoji.repeat(100)));

        repository.saveDone(jobId, items);

        assertThat(repository.findItems(jobId)).containsExactlyElementsOf(items);
    }

    /** 근거는 그 작업에 실제로 있는 발언만 가리킨다 (DB 제약). 저장이 실패하면 상태도 DONE이 되지 않는다. */
    @Test
    void evidenceMustReferenceExistingUtterance() {
        repository.markRunning(jobId);

        assertThatThrownBy(() -> repository.saveDone(jobId, List.of(
                new SummaryItem(SummaryItemType.DECISION, "없는 발언", List.of(99), null, null))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(repository.findState(jobId)).contains(new JobState(JobStatus.RUNNING, null));
        assertThat(repository.findItems(jobId)).isEmpty();
    }
}
