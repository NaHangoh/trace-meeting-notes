package tracenotes.minutes;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import tracenotes.summary.QueueFullException;
import tracenotes.summary.SummaryQueue;

/** 작업 생성과 대기열 (SPEC F2, ADR 0007): 자리를 먼저 잡고, 저장한 뒤 넣고, 저장에 실패하면 자리를 돌려준다. */
class JobServiceQueueTest {

    private final ScriptParser parser = mock(ScriptParser.class);
    private final JobRepository repository = mock(JobRepository.class);
    private final SummaryQueue queue = mock(SummaryQueue.class);
    private final SummaryQueue.Reservation reservation = mock(SummaryQueue.Reservation.class);
    private final JobService service = new JobService(parser, repository, queue,
            Clock.fixed(Instant.parse("2026-10-08T09:00:00Z"), ZoneOffset.UTC));

    {
        when(parser.parse(anyString())).thenReturn(List.of(new Utterance(1, "참석자", "내용")));
    }

    @Test
    void reservesThenSavesThenSubmits() {
        when(queue.reserve()).thenReturn(reservation);

        String jobId = service.create("참석자: 내용");

        InOrder order = inOrder(queue, repository, reservation);
        order.verify(queue).reserve();
        order.verify(repository).insert(any(), any(), anyList());
        order.verify(reservation).submit(jobId);
        verify(reservation, never()).cancel();
    }

    @Test
    void saveFailureReturnsSlotAndDoesNotSubmit() {
        when(queue.reserve()).thenReturn(reservation);
        doThrow(new DataIntegrityViolationException("insert failed")).when(repository).insert(any(), any(), anyList());

        assertThatThrownBy(() -> service.create("참석자: 내용")).isInstanceOf(DataIntegrityViolationException.class);

        verify(reservation).cancel();
        verify(reservation, never()).submit(any());
    }

    @Test
    void queueFullCreatesNoJob() {
        when(queue.reserve()).thenThrow(new QueueFullException());

        assertThatThrownBy(() -> service.create("참석자: 내용")).isInstanceOf(QueueFullException.class);

        verifyNoInteractions(repository);
    }
}
