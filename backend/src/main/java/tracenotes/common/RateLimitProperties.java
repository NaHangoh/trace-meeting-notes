package tracenotes.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 요청 수 제한 설정값 (app.rate-limit.*).
 *
 * @param previewPerMinute 미리보기 API의 IP당 분당 요청 수 (SPEC F1). 정리 요청 제한(S4)과 별도 설정값
 * @param jobPerHour 작업 생성(정리 요청)의 IP당 시간당 요청 수 (SPEC S4). 기본 20, 개발 프로필 200
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(int previewPerMinute, int jobPerHour) {

    public RateLimitProperties {
        if (previewPerMinute < 1) {
            throw new IllegalArgumentException("app.rate-limit.preview-per-minute must be at least 1");
        }
        if (jobPerHour < 1) {
            throw new IllegalArgumentException("app.rate-limit.job-per-hour must be at least 1");
        }
    }
}
