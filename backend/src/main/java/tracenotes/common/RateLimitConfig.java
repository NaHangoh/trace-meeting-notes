package tracenotes.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 요청 수 제한: 미리보기(분당, SPEC F1)와 작업 생성(시간당, SPEC S4). 서로 다른 설정값과 제한기를 쓴다.
 * 바이트 상한 필터 뒤, 본문 파싱 전에 실행된다.
 * IP는 getRemoteAddr만 쓴다. X-Forwarded-For 같은 헤더는 클라이언트가 바꿀 수 있다 (ADR 0006).
 */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final IpRateLimiter previewLimiter;
    private final IpRateLimiter jobLimiter;

    public RateLimitConfig(RateLimitProperties properties, Clock clock) {
        this.previewLimiter = new IpRateLimiter(properties.previewPerMinute(), Duration.ofMinutes(1), clock);
        this.jobLimiter = new IpRateLimiter(properties.jobPerHour(), Duration.ofHours(1), clock);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LimitInterceptor(previewLimiter)).addPathPatterns("/api/preview");
        registry.addInterceptor(new LimitInterceptor(jobLimiter)).addPathPatterns("/api/jobs");
    }

    /** POST만 센다. CORS 사전 요청(OPTIONS)은 세지 않는다. */
    private record LimitInterceptor(IpRateLimiter limiter) implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws IOException {
            if (!"POST".equals(request.getMethod()) || limiter.tryAcquire(request.getRemoteAddr())) {
                return true;
            }
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(ApiError.TOO_MANY_REQUESTS.toJson().getBytes(StandardCharsets.UTF_8));
            return false;
        }
    }
}
