package tracenotes.summary;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import tracenotes.llm.LlmRequest;
import tracenotes.minutes.Utterance;

/**
 * 정리 요청을 만든다 (SPEC F2, rules/llm.md).
 * 지시는 시스템 메시지, 발언은 `[번호] 화자: 내용` 형식으로 요청마다 무작위 구분자 블록 안에만 넣는다.
 * 구분자를 미리 알 수 없으므로 스크립트 속 문장이 블록을 닫고 지시처럼 보이게 할 수 없다.
 */
public class SummaryPromptBuilder {

    /** 프롬프트 파일에서 구분자 이름이 들어갈 자리. */
    public static final String PLACEHOLDER = "{{DELIMITER}}";

    public static final String SCHEMA_NAME = "summary";

    private static final int MAX_DELIMITER_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** SPEC F2 응답 스키마. response_format으로 실행기에 넘긴다. 서버 쪽 검증(W3)은 따로 한다. */
    static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("items"),
            "properties", Map.of("items", Map.of(
                    "type", "array",
                    "maxItems", 100,
                    "items", Map.of(
                            "type", "object",
                            "additionalProperties", false,
                            "required", List.of("type", "text", "evidence", "assignee", "due"),
                            "properties", Map.of(
                                    "type", Map.of("type", "string",
                                            "enum", List.of("AGENDA", "DISCUSSION", "DECISION", "TODO")),
                                    "text", Map.of("type", "string", "maxLength", 500),
                                    "evidence", Map.of("type", "array", "items", Map.of("type", "integer")),
                                    "assignee", Map.of("type", List.of("string", "null")),
                                    "due", Map.of("type", List.of("string", "null")))))));

    private final String template;
    private final Supplier<String> delimiterSource;

    public SummaryPromptBuilder(String template) {
        this(template, SummaryPromptBuilder::randomDelimiter);
    }

    SummaryPromptBuilder(String template, Supplier<String> delimiterSource) {
        if (template == null || !template.contains(PLACEHOLDER)) {
            throw new IllegalArgumentException("summarize prompt must contain " + PLACEHOLDER);
        }
        this.template = template;
        this.delimiterSource = delimiterSource;
    }

    public LlmRequest build(List<Utterance> utterances) {
        String body = utterances.stream()
                .map(u -> "[" + u.no() + "] " + oneLine(u.speaker()) + ": " + oneLine(u.text()))
                .collect(Collectors.joining("\n"));
        String delimiter = delimiterNotIn(body);
        String user = "<<<" + delimiter + "\n" + body + "\n" + delimiter + ">>>";
        return new LlmRequest(template.replace(PLACEHOLDER, delimiter), user, SCHEMA_NAME, SCHEMA);
    }

    /** 발언에 들어 있지 않은 구분자. 같은 문자열이 있으면 다시 뽑는다. */
    private String delimiterNotIn(String body) {
        for (int attempt = 0; attempt < MAX_DELIMITER_ATTEMPTS; attempt++) {
            String delimiter = delimiterSource.get();
            if (!body.contains(delimiter)) {
                return delimiter;
            }
        }
        throw new IllegalStateException("could not choose a delimiter absent from the script");
    }

    /** 내용 안의 줄바꿈은 공백으로 바꿔 `[번호] 화자: 내용` 한 줄 형식이 깨지지 않게 한다. */
    private static String oneLine(String text) {
        return text.replaceAll("\\R+", " ");
    }

    private static String randomDelimiter() {
        return "SCRIPT_" + String.format("%016x", RANDOM.nextLong());
    }
}
