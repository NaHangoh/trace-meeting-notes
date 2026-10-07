package tracenotes.common;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플리케이션 설정값 (app.*).
 *
 * @param maxScriptChars 입력 최대 글자 수 (SPEC S3)
 * @param maxRequestBytes 요청 본문 바이트 상한 (SPEC S3)
 * @param corsAllowedOrigins CORS를 허용할 프론트 주소. `*`는 쓰지 않는다.
 */
@ConfigurationProperties("app")
public record AppProperties(int maxScriptChars, int maxRequestBytes, List<String> corsAllowedOrigins) {
}
