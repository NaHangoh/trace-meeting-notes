package tracenotes.llm;

/**
 * 모든 LLM 호출이 거치는 인터페이스 (rules/llm.md). 구현은 설정 app.llm.provider로만 바꾼다.
 * LLM이 하는 일은 JSON 텍스트를 돌려주는 것뿐이다. 도구 호출·파일·네트워크 권한을 주지 않는다.
 */
public interface LlmClient {

    /**
     * 응답 메시지 내용(JSON 텍스트)을 돌려준다. 내용이 스키마에 맞는지는 호출한 쪽에서 검증한다.
     *
     * @throws LlmCallException 타임아웃, 연결 실패, 2xx가 아닌 응답, OpenAI 형식이 아닌 응답
     */
    String complete(LlmRequest request);
}
