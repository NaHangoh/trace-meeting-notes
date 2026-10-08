package tracenotes.common;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플리케이션 설정값 (app.*).
 *
 * @param maxScriptChars 입력 최대 글자 수 (SPEC S3). 1~1,000,000
 * @param maxRequestBytes 요청 본문 바이트 상한 (SPEC S3). 1~10MB, 그리고 maxScriptChars × 4 + JSON 여유분 이상
 * @param corsAllowedOrigins CORS를 허용할 프론트 주소. 없으면 어떤 출처도 허용하지 않는다.
 *     앞뒤 공백을 지운 값으로 저장한다. `*`나 쉼표가 든 값, 문자 그대로의 "null"(끝의 `/` 무시), 빈 값, null 항목이
 *     있으면 시작하지 않는다.
 */
@ConfigurationProperties("app")
public record AppProperties(int maxScriptChars, int maxRequestBytes, List<String> corsAllowedOrigins) {

    /** 본문을 JSON으로 감싸는 부분(`{"text":""}` 등)의 여유분. */
    public static final int JSON_OVERHEAD_BYTES = 1024;

    private static final String MAX_SCRIPT_CHARS_KEY = "app.max-script-chars";
    private static final String MAX_REQUEST_BYTES_KEY = "app.max-request-bytes";
    private static final int MAX_SCRIPT_CHARS_LIMIT = 1_000_000;
    private static final int MAX_REQUEST_BYTES_LIMIT = 10 * 1024 * 1024;
    private static final String CORS_KEY = "app.cors-allowed-origins";

    /** 설정값이 없으면 0이 되어 여기서 걸린다. 오류 메시지에는 키와 범위만 넣는다. */
    private static void requireRange(String key, int value, int max) {
        if (value < 1 || value > max) {
            throw new IllegalArgumentException(key + " must be between 1 and " + max);
        }
    }

    public AppProperties {
        requireRange(MAX_SCRIPT_CHARS_KEY, maxScriptChars, MAX_SCRIPT_CHARS_LIMIT);
        requireRange(MAX_REQUEST_BYTES_KEY, maxRequestBytes, MAX_REQUEST_BYTES_LIMIT);
        // 글자 수 상한을 4바이트 문자로 채운 JSON도 바이트 상한을 통과해야 한다 (SPEC S3).
        // JSON 이스케이프로 늘어나는 바이트(\r\n, 제어 문자의 \\uXXXX)는 넣지 않았다. 그런 입력은 50,000자 이하여도 413일 수 있다.
        if ((long) maxScriptChars * 4 + JSON_OVERHEAD_BYTES > maxRequestBytes) {
            throw new IllegalArgumentException(MAX_REQUEST_BYTES_KEY + " must be at least "
                    + MAX_SCRIPT_CHARS_KEY + " * 4 + " + JSON_OVERHEAD_BYTES);
        }
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
