package tracenotes.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class IpRateLimiterTest {

    /** 테스트에서 시간을 옮길 수 있는 시계. */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final Instant MINUTE_START = Instant.parse("2026-10-08T09:00:00Z");

    private final MutableClock clock = new MutableClock(MINUTE_START);

    @Test
    void allowsUpToLimitThenRejects() {
        IpRateLimiter limiter = new IpRateLimiter(60, Duration.ofMinutes(1), clock);

        for (int i = 0; i < 60; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).as("request %d", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
    }

    @Test
    void resetsAfterWindow() {
        IpRateLimiter limiter = new IpRateLimiter(60, Duration.ofMinutes(1), clock);
        for (int i = 0; i < 60; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

        clock.advance(Duration.ofSeconds(1));

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }

    /** 고정 창은 창 길이 단위로 정렬된다. 창 중간에 시작해도 다음 정각 분에 초기화된다. */
    @Test
    void windowsAreAlignedToWindowLength() {
        clock.advance(Duration.ofSeconds(50));
        IpRateLimiter limiter = new IpRateLimiter(1, Duration.ofMinutes(1), clock);
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

        clock.advance(Duration.ofSeconds(10));

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }

    @Test
    void separateLimitPerIp() {
        IpRateLimiter limiter = new IpRateLimiter(1, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
    }

    /** 미리보기와 작업 생성은 별도 제한기(별도 설정값)라 한쪽을 다 써도 다른 쪽은 영향이 없다. */
    @Test
    void previewAndJobLimitsAreIndependent() {
        IpRateLimiter preview = new IpRateLimiter(60, Duration.ofMinutes(1), clock);
        IpRateLimiter job = new IpRateLimiter(20, Duration.ofHours(1), clock);
        for (int i = 0; i < 60; i++) {
            preview.tryAcquire("10.0.0.1");
        }

        assertThat(preview.tryAcquire("10.0.0.1")).isFalse();
        assertThat(job.tryAcquire("10.0.0.1")).isTrue();
    }

    /** 지난 창의 IP 항목은 지워진다. 서로 다른 IP를 계속 보내도 메모리가 늘기만 하지 않는다. */
    @Test
    void staleEntriesAreRemovedAfterWindow() {
        IpRateLimiter limiter = new IpRateLimiter(60, Duration.ofMinutes(1), clock);
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire("10.0.1." + i);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(100);

        clock.advance(Duration.ofMinutes(1));
        limiter.tryAcquire("10.0.2.1");

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    /**
     * 창 경계 경쟁: 앞 창에서 시계를 읽은 요청이 늦게 도착해도 이미 시작된 다음 창의 카운터를 되돌리지 않는다.
     * (되돌리면 다음 창에서 센 횟수가 사라져 상한보다 더 허용된다)
     */
    @Test
    void lateRequestFromOlderWindowDoesNotResetNewerCounter() {
        IpRateLimiter limiter = new IpRateLimiter(2, Duration.ofMinutes(1), clock);
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();

        clock.advance(Duration.ofMinutes(-1));
        limiter.tryAcquire("10.0.0.1");
        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
    }

    @Test
    void concurrentRequestsAllowExactlyLimit() throws Exception {
        IpRateLimiter limiter = new IpRateLimiter(60, Duration.ofMinutes(1), clock);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                calls.add(() -> limiter.tryAcquire("10.0.0.1"));
            }
            int allowed = 0;
            for (Future<Boolean> result : pool.invokeAll(calls)) {
                if (result.get()) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(60);
        } finally {
            pool.shutdownNow();
        }
    }
}
