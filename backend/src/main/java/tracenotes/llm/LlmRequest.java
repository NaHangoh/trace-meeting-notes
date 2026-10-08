package tracenotes.llm;

import java.util.Map;
import java.util.Objects;

/**
 * LLM 요청 하나. 지시는 시스템 메시지, 데이터(발언)는 사용자 메시지로 나눈다 (rules/llm.md).
 *
 * @param systemPrompt 정리 지시
 * @param userContent 구분자로 감싼 발언 블록
 * @param schemaName response_format의 스키마 이름
 * @param schema response_format에 넣을 JSON 스키마
 */
public record LlmRequest(String systemPrompt, String userContent, String schemaName, Map<String, Object> schema) {

    /** 빠진 부분이 있으면 만들지 않는다. 메시지에는 항목 이름만 넣는다. */
    public LlmRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userContent, "userContent");
        Objects.requireNonNull(schemaName, "schemaName");
        Objects.requireNonNull(schema, "schema");
    }

    /** 로그에 찍혀도 지시·발언 내용이 나오지 않게 길이만 넣는다. */
    @Override
    public String toString() {
        return "LlmRequest[systemLength=" + systemPrompt.length() + ", userLength=" + userContent.length()
                + ", schema=" + schemaName + "]";
    }
}
