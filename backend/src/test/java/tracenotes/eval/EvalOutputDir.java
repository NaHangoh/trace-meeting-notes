package tracenotes.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * eval 출력 폴더 검사 (응답 원문이 저장소에 들어가지 않게).
 * 폴더를 만든 뒤 실제 경로(링크·짧은 이름·다른 표기를 푼 경로)로 저장소와 비교한다.
 */
final class EvalOutputDir {

    private EvalOutputDir() {
    }

    /** @return 만든 폴더의 실제 경로 */
    static Path prepare(String value, Path repo) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("EVAL_OUT_DIR가 필요하다 (저장소 밖 절대 경로)");
        }
        Path out = Path.of(value.strip());
        if (!out.isAbsolute()) {
            throw new IllegalStateException("EVAL_OUT_DIR는 절대 경로여야 한다");
        }
        try {
            Path realRepo = repo.toRealPath();
            // 만들기 전에도 이름으로 한 번 거른다 (저장소 안에 빈 폴더를 만들지 않게)
            if (out.normalize().startsWith(realRepo) || out.normalize().startsWith(repo.toAbsolutePath().normalize())) {
                throw new IllegalStateException("EVAL_OUT_DIR는 저장소 밖이어야 한다");
            }
            Path real = Files.createDirectories(out).toRealPath();
            if (real.startsWith(realRepo)) {
                throw new IllegalStateException("EVAL_OUT_DIR는 저장소 밖이어야 한다");
            }
            return real;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
