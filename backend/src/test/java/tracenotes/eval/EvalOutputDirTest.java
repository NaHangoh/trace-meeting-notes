package tracenotes.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** eval 출력 폴더는 저장소 밖 절대 경로만 받는다 (응답 원문이 저장소에 들어가지 않게). 임시 폴더로 확인한다. */
class EvalOutputDirTest {

    @TempDir
    Path temp;

    private Path repo() throws IOException {
        return Files.createDirectories(temp.resolve("repo"));
    }

    @Test
    void outsideAbsolutePathIsCreatedAndReturnedAsRealPath() throws IOException {
        Path out = EvalOutputDir.prepare(temp.resolve("out").toString(), repo());

        assertThat(out).isEqualTo(temp.resolve("out").toRealPath());
        assertThat(Files.isDirectory(out)).isTrue();
    }

    @Test
    void repoItselfAndPathsInsideAreRejected() throws IOException {
        Path repo = repo();

        assertThatThrownBy(() -> EvalOutputDir.prepare(repo.toString(), repo)).hasMessageContaining("저장소 밖");
        assertThatThrownBy(() -> EvalOutputDir.prepare(repo.resolve("eval/raw").toString(), repo))
                .hasMessageContaining("저장소 밖");
        assertThatThrownBy(() -> EvalOutputDir.prepare(temp.resolve("x/../repo/raw").toString(), repo))
                .hasMessageContaining("저장소 밖");
    }

    @Test
    void missingOrRelativePathIsRejected() throws IOException {
        Path repo = repo();

        assertThatThrownBy(() -> EvalOutputDir.prepare(null, repo)).hasMessageContaining("EVAL_OUT_DIR");
        assertThatThrownBy(() -> EvalOutputDir.prepare(" ", repo)).hasMessageContaining("EVAL_OUT_DIR");
        assertThatThrownBy(() -> EvalOutputDir.prepare("relative/out", repo)).hasMessageContaining("절대 경로");
    }

    /** 실제 경로로 비교한다: 이름이 다른 경로라도 실제로 저장소 안이면 거부. 심볼릭 링크를 못 만드는 환경이면 건너뛴다. */
    @Test
    void linkPointingIntoRepoIsRejected() throws IOException {
        Path repo = repo();
        Path link = temp.resolve("link");
        try {
            Files.createSymbolicLink(link, repo);
        } catch (IOException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.abort("symbolic links not available: " + e.getClass().getSimpleName());
        }

        assertThatThrownBy(() -> EvalOutputDir.prepare(link.resolve("raw").toString(), repo))
                .hasMessageContaining("저장소 밖");
    }
}
