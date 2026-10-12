package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tracenotes.minutes.JobRepository;
import tracenotes.minutes.Utterance;

/**
 * 정리 중 Error(StackOverflowError, OutOfMemoryError 등)도 작업을 FAILED(INTERNAL_ERROR)로 남긴다 (SPEC F2 실패 처리).
 * 로그에는 실패 종류와 예외 클래스 이름만 남고 메시지·스택·작업 ID는 없다. Error는 작업자 스레드로 다시 던진다.
 */
@ExtendWith(OutputCaptureExtension.class)
class SummaryJobRunnerErrorTest {

    private static final String MESSAGE_MARKER = "errorDetailMarker";

    private final JobRepository jobs = mock(JobRepository.class);
    private final SummaryRepository repository = mock(SummaryRepository.class);
    private final SummarizeService summarizer = mock(SummarizeService.class);
    private final SummaryJobRunner runner = new SummaryJobRunner(jobs, repository, summarizer);

    @Test
    void errorDuringSummaryIsInternalErrorWithClassNameOnly(CapturedOutput output) {
        String jobId = UUID.randomUUID().toString();
        when(repository.markRunning(jobId)).thenReturn(true);
        when(jobs.findUtterances(jobId)).thenReturn(List.of(new Utterance(1, "참석자", "내용")));
        when(summarizer.summarize(anyList())).thenThrow(new StackOverflowError(MESSAGE_MARKER));

        assertThatThrownBy(() -> runner.accept(jobId)).isInstanceOf(StackOverflowError.class);

        verify(repository).markFailed(jobId, SummaryFailure.INTERNAL_ERROR);
        assertThat(output).contains("INTERNAL_ERROR", "java.lang.StackOverflowError")
                .doesNotContain(MESSAGE_MARKER, jobId, "\tat ");
    }

    /** Error 뒤 실패 저장까지 실패하면 저장 실패도 클래스 이름으로 남기고, Error는 그대로 던진다. */
    @Test
    void stateSaveFailureAfterErrorIsStillLogged(CapturedOutput output) {
        String jobId = UUID.randomUUID().toString();
        when(repository.markRunning(jobId)).thenReturn(true);
        when(jobs.findUtterances(jobId)).thenReturn(List.of(new Utterance(1, "참석자", "내용")));
        when(summarizer.summarize(anyList())).thenThrow(new StackOverflowError(MESSAGE_MARKER));
        doThrow(new DataIntegrityViolationException(MESSAGE_MARKER))
                .when(repository).markFailed(jobId, SummaryFailure.INTERNAL_ERROR);

        assertThatThrownBy(() -> runner.accept(jobId)).isInstanceOf(StackOverflowError.class);

        assertThat(output).contains("state not saved", DataIntegrityViolationException.class.getName())
                .doesNotContain(MESSAGE_MARKER, jobId, "\tat ");
    }
}
