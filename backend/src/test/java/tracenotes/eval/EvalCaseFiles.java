package tracenotes.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.minutes.ScriptSplitter;
import tracenotes.minutes.Utterance;

/**
 * eval/cases/{케이스}/ 읽기. script.txt는 제품과 같은 분할 규칙(SPEC F1)으로 나누고, expected.json은 사람이 쓴 정답이다.
 * planted.json은 검증 단계(F3)용이라 이 단계에서는 읽지 않는다.
 */
final class EvalCaseFiles {

    /**
     * @param name 케이스 폴더 이름
     * @param utterances 번호 붙은 발언
     * @param expected 정답
     */
    record EvalCase(String name, List<Utterance> utterances, ExpectedCase expected) {
    }

    private EvalCaseFiles() {
    }

    /** script.txt가 있는 폴더를 이름 순으로. expected.json이 없거나 형식이 틀리면 하나라도 실패한다 (일부만 재지 않게). */
    static List<EvalCase> load(Path casesDir, JsonMapper json) {
        return caseDirs(casesDir).stream().map(d -> load(d, json, d.getFileName().toString())).toList();
    }

    /** 예상 호출 수 계산용: 발언만 읽는다 (expected.json을 쓰기 전에도 볼 수 있게). 케이스 이름 순. */
    static Map<String, List<Utterance>> scripts(Path casesDir) {
        Map<String, List<Utterance>> scripts = new LinkedHashMap<>();
        caseDirs(casesDir).forEach(d -> scripts.put(d.getFileName().toString(), split(d)));
        return scripts;
    }

    private static List<Path> caseDirs(Path casesDir) {
        try (Stream<Path> dirs = Files.list(casesDir)) {
            List<Path> caseDirs = dirs.filter(d -> Files.isRegularFile(d.resolve("script.txt"))).sorted().toList();
            if (caseDirs.isEmpty()) {
                throw new IllegalStateException("no eval cases under " + casesDir);
            }
            return caseDirs;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Utterance> split(Path dir) {
        try {
            return new ScriptSplitter().split(Files.readString(dir.resolve("script.txt"), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static EvalCase load(Path dir, JsonMapper json, String name) {
        Path expected = dir.resolve("expected.json");
        if (!Files.isRegularFile(expected)) {
            throw new IllegalStateException(name + ": expected.json이 없다 (사람이 작성)");
        }
        try {
            List<Utterance> utterances = split(dir);
            ExpectedCase parsed;
            try {
                parsed = ExpectedCase.parse(json, Files.readString(expected, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(name + ": " + e.getMessage(), e);
            }
            int last = utterances.size();
            parsed.items().forEach(item -> item.evidence().forEach(no -> {
                if (no > last) {
                    throw new IllegalStateException(name + ": " + item.id() + " 근거 " + no + "번이 스크립트(" + last
                            + "개 발언)에 없다");
                }
            }));
            return new EvalCase(name, utterances, parsed);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
