package tracenotes.common;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IP별 요청 수 제한 (SPEC F1, S4). 서버 메모리, 고정 창(창 길이 단위로 정렬), 서버 한 대 전제 (ADR 0006).
 * 창이 바뀌면 지난 창의 항목을 지워 서로 다른 IP가 계속 와도 메모리가 늘기만 하지 않는다.
 */
public class IpRateLimiter {

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicLong purgedWindow = new AtomicLong(Long.MIN_VALUE);

    public IpRateLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1 || window.toMillis() < 1) {
            throw new IllegalArgumentException("limit and window must be positive");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** 이번 창에서 limit 이하면 true. */
    public boolean tryAcquire(String key) {
        long window = Math.floorDiv(clock.millis(), windowMillis);
        purgeOlderThan(window);
        // 창은 뒤로 가지 않는다: 앞 창에서 시계를 읽고 늦게 온 요청은 이미 시작된 다음 창에 센다.
        // 상한을 넘은 뒤에는 limit + 1에서 멈춰 넘침을 막는다
        Counter counter = counters.compute(key, (k, old) -> old == null || old.window < window
                ? new Counter(window, 1)
                : new Counter(old.window, Math.min(old.count + 1, limit + 1)));
        return counter.count <= limit;
    }

    private void purgeOlderThan(long window) {
        long purged = purgedWindow.get();
        if (purged < window && purgedWindow.compareAndSet(purged, window)) {
            counters.values().removeIf(counter -> counter.window < window);
        }
    }

    int trackedKeys() {
        return counters.size();
    }

    private record Counter(long window, int count) {
    }
}
