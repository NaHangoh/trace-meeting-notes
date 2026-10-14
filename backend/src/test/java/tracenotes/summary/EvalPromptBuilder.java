package tracenotes.summary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * eval 전용 요청 생성기 (테스트 소스, 제품 코드에 넣지 않음).
 * 구분자를 (케이스, 실행 번호, 그 실행 안의 요청 순서)로 정해, 같은 실행 번호의 프롬프트 버전들이 같은 발언 블록을 받게 한다.
 * 제품 경로는 {@link SummaryPromptBuilder}의 공개 생성자로 지금처럼 무작위 구분자를 쓴다.
 * 형식(`SCRIPT_` + 16진수 16자)과 발언에 든 구분자를 건너뛰는 처리는 제품과 같다.
 */
public final class EvalPromptBuilder {

    private EvalPromptBuilder() {
    }

    /** 실행 하나에 새로 만든다. 덩어리·재시도로 부를 때마다 다음 구분자를 쓴다. */
    public static SummaryPromptBuilder forRun(String template, String caseName, int run) {
        AtomicInteger index = new AtomicInteger();
        return new SummaryPromptBuilder(template, () -> delimiter(caseName, run, index.getAndIncrement()));
    }

    static String delimiter(String caseName, int run, int index) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((caseName + "\n" + run + "\n" + index).getBytes(StandardCharsets.UTF_8));
            return "SCRIPT_" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
