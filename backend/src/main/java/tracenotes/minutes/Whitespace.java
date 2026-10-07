package tracenotes.minutes;

/** 검사와 분할이 같이 쓰는 공백 기준 (SPEC F1 서버 검사). NBSP·전각 공백 포함. */
final class Whitespace {

    private Whitespace() {
    }

    static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    static boolean isBlank(String text) {
        return text.codePoints().allMatch(Whitespace::isSpace);
    }

    static String strip(String text) {
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
}
