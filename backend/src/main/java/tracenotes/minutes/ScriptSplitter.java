package tracenotes.minutes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 회의 스크립트를 번호 붙은 발언 목록으로 나눈다 (SPEC F1 분할 규칙). */
public class ScriptSplitter {

    public static final String UNKNOWN_SPEAKER = "미상";

    private static final int MAX_SPEAKER_LENGTH = 20;
    private static final int MAX_SPEAKER_WORDS = 2;

    public List<Utterance> split(String script) {
        List<Utterance> utterances = new ArrayList<>();
        String speaker = UNKNOWN_SPEAKER;

        for (String line : script.replace("\r\n", "\n").split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String marked = speakerMark(line);
            if (marked == null) {
                utterances.add(new Utterance(utterances.size() + 1, speaker, line.strip()));
                continue;
            }
            speaker = marked;
            String content = line.substring(line.indexOf(':') + 1).strip();
            if (!content.isEmpty()) {
                utterances.add(new Utterance(utterances.size() + 1, speaker, content));
            }
        }
        return utterances;
    }

    /** 처음 나온 순서, "미상"은 항상 마지막. */
    public static List<String> speakersOf(List<Utterance> utterances) {
        Set<String> speakers = new LinkedHashSet<>();
        boolean hasUnknown = false;
        for (Utterance utterance : utterances) {
            if (UNKNOWN_SPEAKER.equals(utterance.speaker())) {
                hasUnknown = true;
            } else {
                speakers.add(utterance.speaker());
            }
        }
        List<String> result = new ArrayList<>(speakers);
        if (hasUnknown) {
            result.add(UNKNOWN_SPEAKER);
        }
        return result;
    }

    /** 화자 표시로 인정되면 연속 공백을 한 칸으로 합친 화자 이름, 아니면 null. */
    private static String speakerMark(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            return null;
        }
        List<String> words = words(line.substring(0, colon));
        String prefix = String.join(" ", words);
        int length = prefix.codePointCount(0, prefix.length());
        if (length < 1 || length > MAX_SPEAKER_LENGTH) {
            return null;
        }
        if (words.size() > MAX_SPEAKER_WORDS) {
            return null;
        }
        if (colon > 0 && colon + 1 < line.length()
                && Character.isDigit(line.codePointBefore(colon))
                && Character.isDigit(line.codePointAt(colon + 1))) {
            return null;
        }
        if (prefix.codePoints().noneMatch(Character::isLetter)) {
            return null;
        }
        if (words.stream().anyMatch(HeaderWords.WORDS::contains)) {
            return null;
        }
        return prefix;
    }

    /** strip()과 같은 공백 기준(Character.isWhitespace, 전각 공백 포함)으로 단어를 나눈다. */
    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int cp : text.codePoints().toArray()) {
            if (Character.isWhitespace(cp)) {
                if (!word.isEmpty()) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.appendCodePoint(cp);
            }
        }
        if (!word.isEmpty()) {
            words.add(word.toString());
        }
        return words;
    }
}
