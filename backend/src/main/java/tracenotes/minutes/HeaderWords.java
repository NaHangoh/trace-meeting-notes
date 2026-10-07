package tracenotes.minutes;

import java.util.Set;

/** 콜론 앞에 와도 화자로 보지 않는 머리말 (SPEC F1 분할 규칙 c). */
public final class HeaderWords {

    public static final Set<String> WORDS = Set.of("참고", "결론", "비고", "안건", "결정", "메모", "주의", "요약");

    private HeaderWords() {
    }
}
