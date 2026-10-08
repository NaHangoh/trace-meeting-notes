package tracenotes.llm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmRequestTest {

    /** 지시·발언·스키마가 없으면 만들 수 없다 (toString 등에서 NPE가 나지 않게 미리 막는다). */
    @Test
    void nullPartsAreRejected() {
        Map<String, Object> schema = Map.of("type", "object");

        assertThatThrownBy(() -> new LlmRequest(null, "내용", "summary", schema)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LlmRequest("지시", null, "summary", schema)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LlmRequest("지시", "내용", null, schema)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LlmRequest("지시", "내용", "summary", null)).isInstanceOf(NullPointerException.class);
    }
}
