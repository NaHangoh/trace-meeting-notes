package tracenotes.minutes;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 콜론 앞에 와도 화자로 보지 않는 머리말 (SPEC F1 분할 규칙 c).
 * 비교는 정확히 일치이고(앞부분 일치 없음), 영문은 대소문자를 무시한다.
 */
public final class HeaderWords {

    /** 콜론 앞 단어 중 하나라도 같으면 머리말. */
    public static final Set<String> WORDS = Set.of(
            "참고", "결론", "비고", "안건", "결정", "메모", "주의", "요약",
            "결정사항", "참고사항", "주의사항", "논의사항", "전달사항", "공지사항",
            "회의요약", "회의메모", "회의결론", "할일", "일시", "장소", "시간", "날짜", "주제",
            "Note", "TODO");

    /** 콜론 앞 전체(연속 공백을 한 칸으로 합친 뒤)가 같을 때만 머리말. "참석자 1"은 화자다. */
    public static final Set<String> WHOLE_PREFIXES = Set.of("참석자", "할 일");

    private static final Set<String> NORMALIZED_WORDS = normalized(WORDS);
    private static final Set<String> NORMALIZED_WHOLE_PREFIXES = normalized(WHOLE_PREFIXES);

    private HeaderWords() {
    }

    static boolean isWord(String word) {
        return NORMALIZED_WORDS.contains(word.toLowerCase(Locale.ROOT));
    }

    /** prefix는 단어 사이 공백을 한 칸으로 합친 콜론 앞 전체. */
    static boolean isWholePrefix(String prefix) {
        return NORMALIZED_WHOLE_PREFIXES.contains(prefix.toLowerCase(Locale.ROOT));
    }

    private static Set<String> normalized(Set<String> words) {
        return words.stream().map(word -> word.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
}
