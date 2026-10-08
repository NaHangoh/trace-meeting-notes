package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tracenotes.minutes.Utterance;

/** SPEC F2: 발언 경계에서 덩어리로 나누고, 덩어리 크기를 넘는 발언 하나는 잘라 같은 번호로 보낸다. */
class ScriptChunkerTest {

    /** 요청에 들어가는 한 줄 `[번호] 화자: 내용`의 길이(코드 포인트). */
    private static int lineLength(Utterance u) {
        String line = "[" + u.no() + "] " + u.speaker() + ": " + u.text();
        return line.codePointCount(0, line.length());
    }

    /** 덩어리 하나의 길이: 줄 길이 합 + 줄바꿈. */
    private static int chunkLength(List<Utterance> chunk) {
        return chunk.stream().mapToInt(ScriptChunkerTest::lineLength).sum() + chunk.size() - 1;
    }

    @Test
    void chunkBoundariesKeepUtteranceNumbers() {
        // 줄 하나가 30자: "[n] 참석자: " (9자) + 내용 21자. 100자 덩어리에 3줄(30+1+30+1+30=92)
        List<Utterance> utterances = IntStream.rangeClosed(1, 9)
                .mapToObj(n -> new Utterance(n, "참석자", "가".repeat(21)))
                .toList();

        List<List<Utterance>> chunks = new ScriptChunker(100).chunk(utterances);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunkLength(chunk)).isLessThanOrEqualTo(100));
        assertThat(chunks.stream().flatMap(List::stream).toList()).isEqualTo(utterances);
        assertThat(chunks.get(1)).extracting(Utterance::no).containsExactly(4, 5, 6);
    }

    /** 경계: 줄바꿈까지 세어 정확히 100자면 같은 덩어리, 101자면 나눈다. */
    @Test
    void lineLengthIncludesNumberSpeakerAndNewline() {
        Utterance a = new Utterance(1, "참석자", "가".repeat(40));   // 9 + 40 = 49
        Utterance b = new Utterance(2, "참석자", "가".repeat(41));   // 9 + 41 = 50 → 49 + 1 + 50 = 100
        Utterance c = new Utterance(3, "참석자", "가".repeat(41));   // 50 → 50 + 1 + 50 = 101

        List<List<Utterance>> chunks = new ScriptChunker(100).chunk(List.of(a, b, c));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).containsExactly(a, b);
        assertThat(chunks.get(1)).containsExactly(c);
    }

    @Test
    void oversizedUtteranceSplitKeepsNumber() {
        String longText = "가".repeat(250);
        List<Utterance> utterances = List.of(
                new Utterance(1, "진행자", "시작합니다"),
                new Utterance(2, "참석자", longText),
                new Utterance(3, "진행자", "마칩니다"));

        List<List<Utterance>> chunks = new ScriptChunker(100).chunk(utterances);

        List<Utterance> pieces = chunks.stream().flatMap(List::stream).filter(u -> u.no() == 2).toList();
        assertThat(pieces).hasSizeGreaterThan(1).allSatisfy(piece -> {
            assertThat(piece.speaker()).isEqualTo("참석자");
            assertThat(lineLength(piece)).isLessThanOrEqualTo(100);
        });
        assertThat(pieces.stream().map(Utterance::text).collect(Collectors.joining())).isEqualTo(longText);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunkLength(chunk)).isLessThanOrEqualTo(100));
        assertThat(chunks.stream().flatMap(List::stream).map(Utterance::no).distinct().toList())
                .containsExactly(1, 2, 3);
    }

    /** 보충 평면 문자(이모지 등)를 반으로 자르지 않는다. */
    @Test
    void oversizedUtteranceIsNotSplitInsideSurrogatePair() {
        String emoji = new String(Character.toChars(0x1F600));
        String longText = emoji.repeat(250);

        List<List<Utterance>> chunks = new ScriptChunker(100).chunk(List.of(new Utterance(1, "참석자", longText)));

        List<Utterance> pieces = chunks.stream().flatMap(List::stream).toList();
        assertThat(pieces).allSatisfy(piece -> {
            assertThat(Character.isLowSurrogate(piece.text().charAt(0))).isFalse();
            assertThat(Character.isHighSurrogate(piece.text().charAt(piece.text().length() - 1))).isFalse();
            assertThat(lineLength(piece)).isLessThanOrEqualTo(100);
        });
        assertThat(pieces.stream().map(Utterance::text).collect(Collectors.joining())).isEqualTo(longText);
    }

    @Test
    void emptyInputHasNoChunks() {
        assertThat(new ScriptChunker(100).chunk(List.of())).isEmpty();
    }

    /** 덩어리가 너무 작으면 번호·화자만으로 줄이 차므로 100자 미만은 받지 않는다. */
    @Test
    void chunkSizeBelowMinimumIsRejected() {
        assertThatThrownBy(() -> new ScriptChunker(99)).isInstanceOf(IllegalArgumentException.class);
    }
}
