package tracenotes.minutes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ScriptSplitterTest {

    private final ScriptSplitter splitter = new ScriptSplitter();

    @Test
    void splitsEachLineWithSequentialNumbers() {
        List<Utterance> result = splitter.split("""
                김민수: 시작합니다

                이영희: 네
                   \t
                박준호: 좋습니다
                """);

        assertThat(result).containsExactly(
                new Utterance(1, "김민수", "시작합니다"),
                new Utterance(2, "이영희", "네"),
                new Utterance(3, "박준호", "좋습니다"));
    }

    @Test
    void continuationLineInheritsSpeaker() {
        List<Utterance> result = splitter.split("""
                김민수: 일정은요

                다음 주까지 가능합니다
                """);

        assertThat(result).containsExactly(
                new Utterance(1, "김민수", "일정은요"),
                new Utterance(2, "김민수", "다음 주까지 가능합니다"));
    }

    @Test
    void firstLineWithoutSpeakerIsUnknown() {
        List<Utterance> result = splitter.split("""
                회의를 시작하겠습니다
                김민수: 네
                """);

        assertThat(result).containsExactly(
                new Utterance(1, ScriptSplitter.UNKNOWN_SPEAKER, "회의를 시작하겠습니다"),
                new Utterance(2, "김민수", "네"));
        assertThat(ScriptSplitter.UNKNOWN_SPEAKER).isEqualTo("미상");
    }

    @Test
    void speakerOnlyLineSetsSpeakerWithoutNumber() {
        List<Utterance> result = splitter.split("""
                이영희: 안녕하세요
                김민수:
                오늘 안건은 두 가지입니다
                """);

        assertThat(result).containsExactly(
                new Utterance(1, "이영희", "안녕하세요"),
                new Utterance(2, "김민수", "오늘 안건은 두 가지입니다"));
    }

    @Test
    void colonFollowedByDigitIsNotSpeaker() {
        List<Utterance> result = splitter.split("""
                김민수: 시작합니다
                다음 회의는 10:30으로 하죠
                회의 10:30
                시간은 10:30으로
                """);

        assertThat(result).containsExactly(
                new Utterance(1, "김민수", "시작합니다"),
                new Utterance(2, "김민수", "다음 회의는 10:30으로 하죠"),
                new Utterance(3, "김민수", "회의 10:30"),
                new Utterance(4, "김민수", "시간은 10:30으로"));
    }

    @Test
    void digitAfterColonWithLetterBeforeIsSpeaker() {
        List<Utterance> result = splitter.split("김민수:3시에 보죠");

        assertThat(result).containsExactly(new Utterance(1, "김민수", "3시에 보죠"));
    }

    @Test
    void digitsOnlyPrefixIsNotSpeaker() {
        List<Utterance> result = splitter.split("""
                이영희: 실적입니다
                2024: 매출 목표 달성
                10.5: 증가
                2024 1: 실적
                """);

        assertThat(result).containsExactly(
                new Utterance(1, "이영희", "실적입니다"),
                new Utterance(2, "이영희", "2024: 매출 목표 달성"),
                new Utterance(3, "이영희", "10.5: 증가"),
                new Utterance(4, "이영희", "2024 1: 실적"));
    }

    @Test
    void prefixWithLetterAndDigitIsSpeaker() {
        List<Utterance> result = splitter.split("참석자 1: 네");

        assertThat(result).containsExactly(new Utterance(1, "참석자 1", "네"));
    }

    static Stream<String> headerWords() {
        return HeaderWords.WORDS.stream();
    }

    @ParameterizedTest
    @MethodSource("headerWords")
    void headerWordIsNotSpeaker(String word) {
        String line = word + ": 다음 주 마감입니다";

        List<Utterance> result = splitter.split("김민수: 공유합니다\n" + line);

        assertThat(result).containsExactly(
                new Utterance(1, "김민수", "공유합니다"),
                new Utterance(2, "김민수", line));
    }

    @Test
    void headerWordAnywhereInPrefixIsNotSpeaker() {
        List<Utterance> result = splitter.split("""
                김민수: 정리합니다
                결정 사항: A안 채택
                회의 요약: 다음 주 마감
                """);

        assertThat(result).containsExactly(
                new Utterance(1, "김민수", "정리합니다"),
                new Utterance(2, "김민수", "결정 사항: A안 채택"),
                new Utterance(3, "김민수", "회의 요약: 다음 주 마감"));
    }

    @Test
    void headerWordsMatchSpec() {
        assertThat(HeaderWords.WORDS)
                .containsExactlyInAnyOrder("참고", "결론", "비고", "안건", "결정", "메모", "주의", "요약");
    }

    @Test
    void prefixOver20CharsIsNotSpeaker() {
        String prefix20 = "가".repeat(20);
        String prefix21 = "가".repeat(21);

        List<Utterance> result = splitter.split(prefix20 + ": 첫째\n" + prefix21 + ": 둘째");

        assertThat(result).containsExactly(
                new Utterance(1, prefix20, "첫째"),
                new Utterance(2, prefix20, prefix21 + ": 둘째"));
    }

    @Test
    void prefixOver2WordsIsNotSpeaker() {
        List<Utterance> result = splitter.split("""
                이영희: 정리하겠습니다
                결정 사항은 다음과 같습니다:
                """);

        assertThat(result).containsExactly(
                new Utterance(1, "이영희", "정리하겠습니다"),
                new Utterance(2, "이영희", "결정 사항은 다음과 같습니다:"));
    }

    @Test
    void ideographicSpaceCountsAsWordSeparator() {
        String threeWords = "김민수　팀장　님";

        List<Utterance> result = splitter.split("이영희: 네\n" + threeWords + ": 확인");

        assertThat(result).containsExactly(
                new Utterance(1, "이영희", "네"),
                new Utterance(2, "이영희", threeWords + ": 확인"));
    }

    @Test
    void supplementaryPlaneDigitsAreDigits() {
        String boldDigits = new String(Character.toChars(0x1D7D0)) + new String(Character.toChars(0x1D7CE));
        String digitsOnly = boldDigits + ": 매출";
        String digitAfterColon = "회의" + new String(Character.toChars(0x1D7CF)) + ":" + boldDigits + "시";

        List<Utterance> result = splitter.split("이영희: 네\n" + digitsOnly + "\n" + digitAfterColon);

        assertThat(result).containsExactly(
                new Utterance(1, "이영희", "네"),
                new Utterance(2, "이영희", digitsOnly),
                new Utterance(3, "이영희", digitAfterColon));
    }

    @Test
    void twoWordSpeakerIsRecognized() {
        List<Utterance> result = splitter.split("김민수 팀장: 네");

        assertThat(result).containsExactly(new Utterance(1, "김민수 팀장", "네"));
    }

    @Test
    void consecutiveSpacesInSpeakerAreCollapsed() {
        List<Utterance> result = splitter.split("김민수  팀장: 하나\n김민수　팀장: 둘\n김민수 팀장: 셋");

        assertThat(result).extracting(Utterance::speaker).containsOnly("김민수 팀장");
        assertThat(ScriptSplitter.speakersOf(result)).containsExactly("김민수 팀장");
    }

    @Test
    void leadingWhitespaceBeforeSpeakerIsAllowed() {
        List<Utterance> result = splitter.split("  \t김민수: 네");

        assertThat(result).containsExactly(new Utterance(1, "김민수", "네"));
    }

    @Test
    void colonAtLineStartIsNotSpeaker() {
        List<Utterance> result = splitter.split("김민수: 네\n: 내용만 있음");

        assertThat(result).containsExactly(
                new Utterance(1, "김민수", "네"),
                new Utterance(2, "김민수", ": 내용만 있음"));
    }

    @Test
    void whitespaceOnlyAfterColonSetsSpeakerWithoutNumber() {
        List<Utterance> result = splitter.split("이영희: 네\n김민수:   \t\n다음 안건입니다");

        assertThat(result).containsExactly(
                new Utterance(1, "이영희", "네"),
                new Utterance(2, "김민수", "다음 안건입니다"));
    }

    @Test
    void speakerOnlyLastLineCreatesNoUtterance() {
        List<Utterance> result = splitter.split("김민수: 네\n이영희:");

        assertThat(result).containsExactly(new Utterance(1, "김민수", "네"));
        assertThat(ScriptSplitter.speakersOf(result)).containsExactly("김민수");
    }

    @Test
    void handlesCrlf() {
        List<Utterance> result = splitter.split("김민수: 하나\r\n\r\n이어서 둘\r\n이영희: 셋\r\n");

        assertThat(result).containsExactly(
                new Utterance(1, "김민수", "하나"),
                new Utterance(2, "김민수", "이어서 둘"),
                new Utterance(3, "이영희", "셋"));
    }

    @Test
    void speakersInFirstAppearanceOrderWithUnknownLast() {
        List<Utterance> utterances = splitter.split("""
                인사드립니다
                박준호: 시작하죠
                김민수: 네
                박준호: 다음
                이영희: 확인했습니다
                """);

        assertThat(ScriptSplitter.speakersOf(utterances))
                .containsExactly("박준호", "김민수", "이영희", ScriptSplitter.UNKNOWN_SPEAKER);
    }
}
