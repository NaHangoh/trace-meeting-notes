package tracenotes.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tracenotes.minutes.InvalidScriptException.Reason;

class ScriptValidatorTest {

    private static final int SPEC_MAX_CHARS = 50_000;

    private final ScriptValidator validator = new ScriptValidator(SPEC_MAX_CHARS);

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\r\n\t", "\u3000", "\u00A0", "\u00A0\n\u00A0 "})
    void rejectsBlank(String script) {
        assertThatThrownBy(() -> validator.validate(script))
                .isInstanceOf(InvalidScriptException.class)
                .extracting("reason").isEqualTo(Reason.BLANK);
    }

    @Test
    void rejectsNullAsBlank() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidScriptException.class)
                .extracting("reason").isEqualTo(Reason.BLANK);
    }

    @Test
    void rejectsOverMaxLength() {
        String script = "가".repeat(SPEC_MAX_CHARS + 1);

        assertThatThrownBy(() -> validator.validate(script))
                .isInstanceOf(InvalidScriptException.class)
                .extracting("reason").isEqualTo(Reason.TOO_LONG);
    }

    @Test
    void acceptsExactlyMaxLength() {
        String script = "가".repeat(SPEC_MAX_CHARS);

        assertThatCode(() -> validator.validate(script)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNul() {
        String script = "김민수: 안녕\u0000하세요";

        assertThatThrownBy(() -> validator.validate(script))
                .isInstanceOf(InvalidScriptException.class)
                .extracting("reason").isEqualTo(Reason.CONTAINS_NUL);
    }

    @Test
    void countsCodePointsAfterCrlfNormalization() {
        ScriptValidator small = new ScriptValidator(5);
        String boldTwo = new String(Character.toChars(0x1D7D0));

        // \r\n은 1자, 보충 평면 문자도 1자: a \n b \n 𝟐 = 5자 (String.length()는 8)
        assertThatCode(() -> small.validate("a\r\nb\r\n" + boldTwo)).doesNotThrowAnyException();
        assertThatThrownBy(() -> small.validate("a\r\nb\r\n" + boldTwo + "c"))
                .isInstanceOf(InvalidScriptException.class)
                .extracting("reason").isEqualTo(Reason.TOO_LONG);
    }

    @Test
    void exceptionMessageDoesNotContainInput() {
        String withNul = "비밀 안건 내용\u0000";
        String tooLong = "비밀 안건 내용 " + "가".repeat(SPEC_MAX_CHARS);

        assertThatThrownBy(() -> validator.validate(withNul))
                .isInstanceOf(InvalidScriptException.class)
                .message().doesNotContain("비밀 안건");
        assertThatThrownBy(() -> validator.validate(tooLong))
                .isInstanceOf(InvalidScriptException.class)
                .satisfies(e -> assertThat(((InvalidScriptException) e).getReason()).isEqualTo(Reason.TOO_LONG))
                .message().doesNotContain("비밀 안건");
    }
}
