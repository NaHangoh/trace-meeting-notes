package tracenotes.common;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플리케이션 설정값 (app.*).
 *
 * @param maxScriptChars 입력 최대 글자 수 (SPEC S3)
 * @param maxRequestBytes 요청 본문 바이트 상한 (SPEC S3)
 * @param corsAllowedOrigins CORS를 허용할 프론트 주소. 없으면 어떤 출처도 허용하지 않는다.
 *     앞뒤 공백을 지운 값으로 저장한다. `*`나 쉼표가 든 값, 문자 그대로의 "null"(끝의 `/` 무시), 빈 값, null 항목이
 *     있으면 시작하지 않는다.
 */
@ConfigurationProperties("app")
public record AppProperties(int maxScriptChars, int maxRequestBytes, List<String> corsAllowedOrigins) {

    private static final String CORS_KEY = "app.cors-allowed-origins";

    public AppProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : validOrigins(corsAllowedOrigins);
    }

    /** 오류 메시지에는 설정 키만 넣고 설정값은 넣지 않는다. */
    private static List<String> validOrigins(List<String> origins) {
        List<String> valid = new ArrayList<>();
        for (String origin : origins) {
            if (origin == null) {
                throw new IllegalArgumentException(CORS_KEY + " must not contain null entries");
            }
            String value = origin.strip();
            if (value.isEmpty()) {
                throw new IllegalArgumentException(CORS_KEY + " must not contain blank entries");
            }
            if (value.contains("*")) {
                throw new IllegalArgumentException(CORS_KEY + " must not contain '*'");
            }
            // Spring은 항목을 다시 쉼표로 나누므로, 한 항목에 숨긴 출처가 아래 검사를 피할 수 있다
            if (value.contains(",")) {
                throw new IllegalArgumentException(CORS_KEY + " entries must not contain ','");
            }
            // Spring은 비교 전에 끝의 "/"를 지우므로 "null/"도 null 출처를 허용하게 된다
            if (withoutTrailingSlashes(value).equalsIgnoreCase("null")) {
                throw new IllegalArgumentException(CORS_KEY + " must not contain the \"null\" origin");
            }
            valid.add(value);
        }
        return List.copyOf(valid);
    }

    private static String withoutTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
