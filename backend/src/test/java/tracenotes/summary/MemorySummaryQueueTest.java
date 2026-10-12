package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** ADR 0007 메모리 대기열: 대기 자리 상한, 작업자 수, 종료 시 실행 중 작업 중단. */
class MemorySummaryQueueTest {

    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> started = new CopyOnWriteArrayList<>();
    private final List<String> interrupted = new CopyOnWriteArrayList<>();
    private MemorySummaryQueue queue;

    /** 시작을 기록하고 release까지 기다리는 작업. 중단되면 기록한다. */
    private final Consumer<String> blockingRunner = jobId -> {
        started.add(jobId);
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            interrupted.add(jobId);
            Thread.currentThread().interrupt();
        }
    };

    @AfterEach
    void stop() {
        release.countDown();
        if (queue != null) {
            queue.shutdown();
        }
    }

    private void awaitStarted(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (started.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(started).hasSizeGreaterThanOrEqualTo(count);
    }

    @Test
    void reserveBeyondCapacityThrowsQueueFull() throws Exception {
        queue = new MemorySummaryQueue(1, 2, blockingRunner);
        queue.reserve().submit("running");
        awaitStarted(1);

        queue.reserve().submit("waiting-1");
        queue.reserve().submit("waiting-2");

        assertThatThrownBy(() -> queue.reserve()).isInstanceOf(QueueFullException.class);
    }

    /** 실행을 시작한 작업은 대기 자리를 내놓는다 (상한은 기다리는 작업 수). */
    @Test
    void startedJobFreesItsSlot() throws Exception {
        queue = new MemorySummaryQueue(1, 1, blockingRunner);
        queue.reserve().submit("running");
        awaitStarted(1);

        queue.reserve().submit("waiting");

        assertThatThrownBy(() -> queue.reserve()).isInstanceOf(QueueFullException.class);
    }

    /** 저장에 실패해 작업을 넣지 못하면 잡은 자리를 돌려준다. 두 번 돌려줘도 자리가 늘지 않는다. */
    @Test
    void cancelReturnsSlotOnce() {
        queue = new MemorySummaryQueue(1, 1, blockingRunner);
        SummaryQueue.Reservation reservation = queue.reserve();
        assertThatThrownBy(() -> queue.reserve()).isInstanceOf(QueueFullException.class);

        reservation.cancel();
        reservation.cancel();

        queue.reserve();
        assertThatThrownBy(() -> queue.reserve()).isInstanceOf(QueueFullException.class);
    }

    @Test
    void workersRunConcurrentlyUpToConfig() throws Exception {
        queue = new MemorySummaryQueue(2, 10, blockingRunner);

        queue.reserve().submit("a");
        queue.reserve().submit("b");
        queue.reserve().submit("c");

        awaitStarted(2);
        Thread.sleep(100);
        assertThat(started).containsExactlyInAnyOrder("a", "b");
    }

    /** 서버 종료 시 실행 중 작업을 중단한다. 작업은 중단을 SERVER_RESTART로 기록한다 (SummaryJobRunner). */
    @Test
    void shutdownInterruptsRunningJob() throws Exception {
        queue = new MemorySummaryQueue(1, 10, blockingRunner);
        queue.reserve().submit("running");
        awaitStarted(1);

        queue.shutdown();

        assertThat(interrupted).containsExactly("running");
    }
}
