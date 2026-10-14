package tracenotes.eval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** eval에서 잴 정리 프롬프트 버전 목록 (EVAL_PROMPT_VERSIONS). 앱 설정은 바꾸지 않는다. */
final class EvalPromptVersions {

    private EvalPromptVersions() {
    }

    /** PromptLoader와 같은 형식. 경로 조작으로 다른 파일을 읽지 못하게 한다. */
    private static final Pattern VERSION = Pattern.compile("v[0-9]+");

    /** 없거나 비었으면 설정 버전 하나. 있으면 쉼표로 나눈 v숫자 목록 (빈 값·중복·형식 오류는 실패). */
    static List<String> parse(String value, String settingVersion) {
        if (value == null || value.isBlank()) {
            return List.of(settingVersion);
        }
        Set<String> versions = new LinkedHashSet<>();
        for (String part : value.split(",", -1)) {
            String version = part.strip();
            if (!VERSION.matcher(version).matches()) {
                throw new IllegalArgumentException("EVAL_PROMPT_VERSIONS는 쉼표로 나눈 v숫자 목록이어야 한다");
            }
            if (!versions.add(version)) {
                throw new IllegalArgumentException("EVAL_PROMPT_VERSIONS에 같은 버전이 두 번 있다: " + version);
            }
        }
        return List.copyOf(versions);
    }
}
