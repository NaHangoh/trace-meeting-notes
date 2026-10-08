package tracenotes.minutes;

/**
 * 검사와 분할이 같이 쓰는 공백 기준 (SPEC F1 서버 검사). NBSP·전각 공백 포함.
 * 정리 결과의 담당자·기한 확인(SPEC F2)도 같은 기준을 쓴다.
 */
public final class Whitespace {

    private Whitespace() {
    }

    public static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    public static boolean isBlank(String text) {
        return text.codePoints().allMatch(Whitespace::isSpace);
    }

    public static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end) {
            int cp = text.codePointAt(start);
            if (!isSpace(cp)) {
                break;
            }
            start += Character.charCount(cp);
        }
        while (end > start) {
            int cp = text.codePointBefore(end);
            if (!isSpace(cp)) {
                break;
            }
            end -= Character.charCount(cp);
        }
        return text.substring(start, end);
    }

    /** 앞뒤 공백을 지우고 연속 공백(이 기준의 모든 공백 문자)을 한 칸 " "으로 합친다. */
    public static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int cp : strip(text).codePoints().toArray()) {
            if (isSpace(cp)) {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            out.appendCodePoint(cp);
        }
        return out.toString();
    }
}
