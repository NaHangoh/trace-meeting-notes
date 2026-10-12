package tracenotes.summary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 서버 메모리 대기열과 작업자 (ADR 0007). 서버 한 대 전제.
 * 대기 자리는 Semaphore로 센다. 작업이 실행을 시작하면 자리를 내놓으므로 상한은 기다리는 작업 수다.
 * 서버 종료 시 실행 중 작업을 중단(interrupt)하고, 기다리던 작업은 DB에 QUEUED로 남아 다음 시작 때 SERVER_RESTART가 된다.
 */
public class MemorySummaryQueue implements SummaryQueue {

    private static final Logger log = LoggerFactory.getLogger(MemorySummaryQueue.class);
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    private final Semaphore slots;
    private final ExecutorService workers;
    private final Consumer<String> runner;

    public MemorySummaryQueue(int workers, int capacity, Consumer<String> runner) {
        this.slots = new Semaphore(capacity);
        this.workers = Executors.newFixedThreadPool(workers, threadFactory());
        this.runner = runner;
    }

    private static ThreadFactory threadFactory() {
        AtomicInteger next = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "summary-worker-" + next.incrementAndGet());
            thread.setDaemon(true);
            // 기본 처리기는 메시지와 스택을 stderr에 찍는다. 메시지에 작업 ID·SQL이 들어갈 수 있어 클래스 이름만 남긴다.
            // 스레드는 끝나고 실행기가 새 작업자를 만든다
            thread.setUncaughtExceptionHandler((t, e) -> log.error("summary worker stopped ({})", e.getClass().getName()));
            return thread;
        };
    }

    @Override
    public Reservation reserve() {
        if (!slots.tryAcquire()) {
            throw new QueueFullException();
        }
        return new SlotReservation();
    }

    /** 실행 중 작업을 중단하고 작업자가 끝날 때까지 잠시 기다린다. */
    public void shutdown() {
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("summary workers did not stop within {}s", SHUTDOWN_WAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class SlotReservation implements Reservation {

        private final AtomicBoolean used = new AtomicBoolean();

        @Override
        public void submit(String jobId) {
            if (!used.compareAndSet(false, true)) {
                return;
            }
            try {
                workers.execute(() -> {
                    slots.release();
                    runner.accept(jobId);
                });
            } catch (RuntimeException e) {
                // 종료 중이라 받지 못함. 작업은 QUEUED로 남아 다음 시작 때 SERVER_RESTART가 된다
                slots.release();
                throw e;
            }
        }

        @Override
        public void cancel() {
            if (used.compareAndSet(false, true)) {
                slots.release();
            }
        }
    }
}
