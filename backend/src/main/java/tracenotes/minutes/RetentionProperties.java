package tracenotes.minutes;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 보관 기간 설정값 (SPEC S2).
 *
 * @param retention 작업·발언 보관 기간. 기본 24시간
 * @param retentionCleanupInterval 만료 작업 삭제를 실행하는 간격
 */
@ConfigurationProperties("app")
public record RetentionProperties(Duration retention, Duration retentionCleanupInterval) {

    public RetentionProperties {
        requirePositive("app.retention", retention);
        requirePositive("app.retention-cleanup-interval", retentionCleanupInterval);
    }

    private static void requirePositive(String key, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(key + " must be positive");
        }
    }
}
