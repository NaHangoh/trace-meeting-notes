package tracenotes.minutes;

import tracenotes.minutes.InvalidScriptException.Reason;

/** 서버 측 입력 검사 (SPEC F1 서버 검사, S3). 미리보기와 작업 생성이 같이 쓴다. */
public class ScriptValidator {

    private final int maxChars;

    public ScriptValidator(int maxChars) {
        this.maxChars = maxChars;
    }

    public void validate(String script) {
        if (script == null || Whitespace.isBlank(script)) {
            throw new InvalidScriptException(Reason.BLANK);
        }
        if (script.indexOf('\0') >= 0) {
            throw new InvalidScriptException(Reason.CONTAINS_NUL);
        }
        if (charCount(script) > maxChars) {
            throw new InvalidScriptException(Reason.TOO_LONG);
        }
    }

    /** 줄바꿈을 \n으로 정규화한 뒤의 코드 포인트 수. */
    static int charCount(String script) {
        String normalized = script.replace("\r\n", "\n");
        return normalized.codePointCount(0, normalized.length());
    }
}
