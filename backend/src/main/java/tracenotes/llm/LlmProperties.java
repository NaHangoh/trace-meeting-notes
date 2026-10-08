package tracenotes.llm;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 설정값 (app.llm.*, SPEC F2, ADR 0002).
 *
 * @param provider 사용할 구현 (openai-compatible, fixture)
 * @param baseUrl OpenAI 호환 API 주소 (예: http://127.0.0.1:8081/v1)
 * @param model 모델 이름
 * @param apiKey 실행기 API 키. 환경 변수 LLM_API_KEY로만 넣는다. 로그·오류 메시지에 쓰지 않는다
 * @param timeout 호출 하나의 타임아웃. 기본 5분
 * @param connectTimeout 실행기에 연결하는 타임아웃. 기본 10초. 넘으면 연결 실패로 본다
 * @param disableThinking 요청에 chat_template_kwargs.enable_thinking=false를 넣을지 (Qwen3 생각 모드 끄기)
 */
@ConfigurationProperties("app.llm")
public record LlmProperties(LlmProvider provider, URI baseUrl, String model, String apiKey, Duration timeout,
        Duration connectTimeout, boolean disableThinking) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    public LlmProperties {
        if (provider == null) {
            provider = LlmProvider.OPENAI_COMPATIBLE;
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = DEFAULT_TIMEOUT;
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (provider == LlmProvider.OPENAI_COMPATIBLE) {
            // 오류 메시지에는 설정 키와 환경 변수 이름만 넣는다
            if (baseUrl == null) {
                throw new IllegalArgumentException("app.llm.base-url must be set for openai-compatible");
            }
            String scheme = baseUrl.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("app.llm.base-url must start with http:// or https://");
            }
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("app.llm.model must be set for openai-compatible");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException(
                        "app.llm.api-key (environment variable LLM_API_KEY) must be set for openai-compatible");
            }
            // 제어 문자(CRLF .env의 줄 끝 등)가 있으면 HTTP 헤더를 만들 때 JDK 예외 메시지에 키가 그대로 들어간다
            if (!apiKey.chars().allMatch(c -> c >= 0x21 && c <= 0x7E)) {
                throw new IllegalArgumentException(
                        "app.llm.api-key (environment variable LLM_API_KEY) must be printable ASCII without spaces");
            }
        }
    }

    /** 설정 객체가 로그에 찍혀도 키가 나오지 않게 한다. */
    @Override
    public String toString() {
        return "LlmProperties[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model
                + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "(none)" : "(set)")
                + ", timeout=" + timeout + ", connectTimeout=" + connectTimeout
                + ", disableThinking=" + disableThinking + "]";
    }
}
