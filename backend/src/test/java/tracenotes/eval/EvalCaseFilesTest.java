package tracenotes.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** 케이스 폴더 읽기 (임시 폴더의 가상 데이터, 저장소의 eval/cases는 읽지 않는다). */
class EvalCaseFilesTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @TempDir
    Path cases;

    private void write(String caseName, String file, String text) throws IOException {
        Files.createDirectories(cases.resolve(caseName));
        Files.writeString(cases.resolve(caseName).resolve(file), text, StandardCharsets.UTF_8);
    }

    @Test
    void loadsCasesInNameOrderWithProductSplitRule() throws IOException {
        write("c02", "script.txt", "김민수: 둘\n");
        write("c02", "expected.json", "{\"items\":[]}");
        write("c01", "script.txt", "김민수: 하나\n이영희: 둘\n이어지는 줄\n");
        write("c01", "expected.json",
                "{\"items\":[{\"id\":\"a\",\"type\":\"AGENDA\",\"keywords\":[\"하나\"],\"evidence\":[3]}]}");

        List<EvalCaseFiles.EvalCase> loaded = EvalCaseFiles.load(cases, json);

        assertThat(loaded).extracting(EvalCaseFiles.EvalCase::name).containsExactly("c01", "c02");
        // 화자 표시가 없는 줄은 앞 화자의 새 발언 (SPEC F1)
        assertThat(loaded.getFirst().utterances()).hasSize(3);
        assertThat(loaded.getFirst().utterances().get(2).speaker()).isEqualTo("이영희");
    }

    @Test
    void missingExpectedFailsWholeLoad() throws IOException {
        write("c01", "script.txt", "김민수: 하나\n");
        write("c01", "expected.json", "{\"items\":[]}");
        write("c02", "script.txt", "김민수: 둘\n");

        assertThatThrownBy(() -> EvalCaseFiles.load(cases, json)).hasMessageContaining("c02")
                .hasMessageContaining("expected.json");
    }

    /** 예상 호출 수는 정답을 쓰기 전에도 볼 수 있어야 한다. */
    @Test
    void scriptsLoadWithoutExpected() throws IOException {
        write("c02", "script.txt", "김민수: 둘\n");
        write("c01", "script.txt", "김민수: 하나\n이영희: 둘\n");

        assertThat(EvalCaseFiles.scripts(cases)).containsOnlyKeys("c01", "c02");
        assertThat(EvalCaseFiles.scripts(cases).keySet()).containsExactly("c01", "c02");
        assertThat(EvalCaseFiles.scripts(cases).get("c01")).hasSize(2);
    }

    @Test
    void evidenceBeyondScriptFails() throws IOException {
        write("c01", "script.txt", "김민수: 하나\n");
        write("c01", "expected.json",
                "{\"items\":[{\"id\":\"a\",\"type\":\"AGENDA\",\"keywords\":[\"하나\"],\"evidence\":[2]}]}");

        assertThatThrownBy(() -> EvalCaseFiles.load(cases, json)).hasMessageContaining("c01")
                .hasMessageContaining("2번");
    }

    @Test
    void malformedExpectedNamesTheCase() throws IOException {
        write("c01", "script.txt", "김민수: 하나\n");
        write("c01", "expected.json", "{\"items\":[{\"type\":\"AGENDA\"}]}");

        assertThatThrownBy(() -> EvalCaseFiles.load(cases, json)).hasMessageContaining("c01: expected.json:");
    }
}
