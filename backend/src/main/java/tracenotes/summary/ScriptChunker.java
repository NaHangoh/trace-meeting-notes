package tracenotes.summary;

import java.util.ArrayList;
import java.util.List;
import tracenotes.minutes.Utterance;

/**
 * 발언을 정리 요청 덩어리로 나눈다 (SPEC F2).
 * 길이는 요청에 들어가는 줄 `[번호] 화자: 내용`의 코드 포인트 수와 줄바꿈으로 센다.
 * 발언 경계에서만 나누고, 덩어리 크기를 넘는 발언 하나는 내용을 잘라 같은 번호·화자의 조각으로 각각 보낸다.
 */
public class ScriptChunker {

    /** 이보다 작으면 번호·화자만으로 줄이 찬다. */
    public static final int MIN_CHARS = 100;

    private final int maxChars;

    public ScriptChunker(int maxChars) {
        if (maxChars < MIN_CHARS) {
            throw new IllegalArgumentException("chunk size must be at least " + MIN_CHARS);
        }
        this.maxChars = maxChars;
    }

    public List<List<Utterance>> chunk(List<Utterance> utterances) {
        List<List<Utterance>> chunks = new ArrayList<>();
        List<Utterance> current = new ArrayList<>();
        int currentLength = 0;
        for (Utterance utterance : utterances) {
            int length = lineLength(utterance);
            if (length > maxChars) {
                flush(chunks, current);
                current = new ArrayList<>();
                currentLength = 0;
                for (Utterance piece : split(utterance)) {
                    chunks.add(List.of(piece));
                }
                continue;
            }
            int joined = current.isEmpty() ? length : currentLength + 1 + length;
            if (joined > maxChars) {
                flush(chunks, current);
                current = new ArrayList<>();
                joined = length;
            }
            current.add(utterance);
            currentLength = joined;
        }
        flush(chunks, current);
        return chunks;
    }

    private static void flush(List<List<Utterance>> chunks, List<Utterance> current) {
        if (!current.isEmpty()) {
            chunks.add(List.copyOf(current));
        }
    }

    /** 내용을 코드 포인트 단위로 잘라 줄 하나가 덩어리 크기를 넘지 않는 조각들로. 보충 평면 문자를 반으로 자르지 않는다. */
    private List<Utterance> split(Utterance utterance) {
        int room = maxChars - codePoints(prefix(utterance));
        if (room < 1) {
            throw new IllegalArgumentException("chunk size is too small for the speaker label");
        }
        int[] text = utterance.text().codePoints().toArray();
        List<Utterance> pieces = new ArrayList<>();
        for (int start = 0; start < text.length; start += room) {
            int end = Math.min(text.length, start + room);
            pieces.add(new Utterance(utterance.no(), utterance.speaker(), new String(text, start, end - start)));
        }
        return pieces;
    }

    private static int lineLength(Utterance utterance) {
        return codePoints(prefix(utterance)) + codePoints(utterance.text());
    }

    private static String prefix(Utterance utterance) {
        return "[" + utterance.no() + "] " + utterance.speaker() + ": ";
    }

    private static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }
}
