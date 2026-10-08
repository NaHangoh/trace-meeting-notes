package tracenotes.llm;

/** 사용할 LLM 구현 (app.llm.provider). */
public enum LlmProvider {
    /** OpenAI 호환 /v1/chat/completions (llama-server, Ollama). */
    OPENAI_COMPATIBLE,
    /** 테스트용 고정 응답. 실제 LLM을 부르지 않는다. */
    FIXTURE
}
